# 0003. Anti double booking no banco com `EXCLUDE USING gist`

- Status: **proposta — revisão do Alicerce**
- Data: 2026-11-13
- Decisores: Alicerce (dono), Quarteto Dev (texto)

## Contexto
Dois clientes não podem reservar o mesmo barbeiro em horários sobrepostos (R1), nem com duas requisições chegando no mesmo milissegundo. A reserva do guest ocupa o horário enquanto espera o pagamento.

## Opções consideradas
1. Verificar no código ("existe reserva sobreposta?") e depois inserir.
2. Lock na aplicação (`synchronized`, lock distribuído).
3. `SELECT ... FOR UPDATE` na agenda do barbeiro.
4. **Constraint `EXCLUDE USING gist` no banco.**

## Decisão
Opção 4:

```sql
CONSTRAINT reserva_sem_sobreposicao EXCLUDE USING gist (
    barbeiro_id WITH =,
    tstzrange(inicio, fim_ocupacao, '[)') WITH &&
) WHERE (status IN ('AGUARDANDO_PAGAMENTO', 'CONFIRMADO'))
```

- Exige a extensão `btree_gist` (igualdade em `uuid` dentro de índice gist).
- Intervalo meio aberto `[)`: 9h00–9h30 e 9h30–10h00 convivem (R2).
- `fim_ocupacao = fim + intervalo entre atendimentos` (configurável, H4).
- A violação (SQLSTATE `23P01`) vira **`409` com `code` `HORARIO_INDISPONIVEL`** no `GlobalExceptionHandler` (pelo nome da constraint, não pela mensagem).
- **Expiração preguiçosa (R16):** como `now()` não pode entrar no `WHERE` da constraint (precisa ser imutável), o caso de uso de reserva, na mesma transação e antes do `INSERT`, roda `UPDATE reserva SET status = 'EXPIRADO' WHERE barbeiro_id = :b AND status = 'AGUARDANDO_PAGAMENTO' AND expira_em <= :agora AND tstzrange(inicio, fim_ocupacao, '[)') && tstzrange(:inicio, :fimOcupacao, '[)')` e publica `ReservaExpirada` para cada uma. Para evitar deadlock entre duas inserções que tocam as mesmas vencidas, as linhas são travadas em ordem (`SELECT id ... ORDER BY id FOR UPDATE`) antes do `UPDATE`, e um `40P01` (deadlock) é repetido uma vez (crítica 4 do Gemini, [`docs/ia/`](../ia/ataque-arquitetura-gemini-2026-11-13.md)).
- Opção 1 descartada: duas transações passam na verificação ao mesmo tempo. Opção 2 descartada: não protege duas instâncias nem um `INSERT` manual. Opção 3 funciona, mas serializa a agenda inteira do barbeiro e depende de todo caminho lembrar do lock.

## Prova
- Teste de integração com **PostgreSQL real (Testcontainers)**: N threads tentam o mesmo horário ao mesmo tempo; exatamente 1 recebe `201` e as outras `409` (`ReservaConcorrenciaIT`, fatia S01/S07). O H2 não tem `EXCLUDE` nem `tstzrange`: por isso os testes não usam H2.
- Validação manual do DDL em 13/11/2026 (PostgreSQL 17.12): ver [ERD, seção 4](../banco/erd.md#4-validação-manual-feita-nesta-etapa).

## Consequências
- ✅ A garantia vale para qualquer caminho de escrita, inclusive remarcação (R19).
- ⚠️ Fica preso ao PostgreSQL (aceito: o banco já é PostgreSQL no Aiven).
- ⚠️ O job de expiração (R17) continua necessário para liberar a disponibilidade e cancelar cobranças.

## Gatilhos para revisitar
- Reserva com mais de um recurso (ex.: cadeira + barbeiro) → constraint por recurso.
