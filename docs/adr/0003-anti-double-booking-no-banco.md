# 0003. Anti double booking no banco com `EXCLUDE USING gist`

- Status: **aceito** (revisão do Alicerce, 13/11/2026)
- Data: 2026-11-13 (proposta e aceite)
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
- **Expiração preguiçosa (R16):** como `now()` não pode entrar no `WHERE` da constraint (precisa ser imutável), o caso de uso de reserva, **na mesma transação e antes do `INSERT`**, expira as vencidas que conflitam com o horário pedido, numa instrução só (trava e atualiza juntas, em ordem de `id`):

  ```sql
  UPDATE reserva
     SET status = 'EXPIRADO', version = version + 1, atualizado_em = :agora
   WHERE id IN (SELECT id
                  FROM reserva
                 WHERE barbeiro_id = :barbeiroId
                   AND status = 'AGUARDANDO_PAGAMENTO'
                   AND expira_em <= :agora
                   AND tstzrange(inicio, fim_ocupacao, '[)') && tstzrange(:inicio, :fimOcupacao, '[)')
                 ORDER BY id
                   FOR UPDATE)
  RETURNING id;
  ```

  - **`version` é incrementado**: quem tiver a reserva carregada com a versão antiga (JPA `@Version`) recebe conflito em vez de sobrescrever o `EXPIRADO`.
  - **`:agora` vem do `Clock` injetado** (ADR 0004), nunca de `now()` do banco, para o teste controlar o tempo e a regra usar o mesmo instante da validação de antecedência.
  - Para cada `id` devolvido, publica `ReservaExpirada` (mesmo evento do job).
  - **A remarcação (R19) usa o mesmo caminho**: expira as vencidas que conflitam com o horário novo, na mesma transação, antes de mover a reserva.
  - **Risco real de deadlock:** dois `INSERT` concorrentes no mesmo horário travam as mesmas linhas na mesma ordem (`ORDER BY id`) e um simplesmente espera o outro. O risco de verdade é com o **job de expiração** (R17), que trava lotes de vencidas de vários barbeiros; por isso o job usa `FOR UPDATE SKIP LOCKED` (pula o que a inserção já travou) e também ordena por `id`.
  - **Se mesmo assim vier `40P01`** (deadlock detectado), a **transação inteira** é refeita (expirar + inserir), **uma vez só**. Na segunda falha, a API responde **`409` com `code` `CONFLITO_CONCORRENTE`** e `detail` "Muitas reservas ao mesmo tempo. Tente de novo.", e registra a métrica. **Não** usa `HORARIO_INDISPONIVEL`: esse código faz o front esconder o horário, e depois de um deadlock o horário pode estar livre. O front pode repetir a mesma requisição **uma vez**; se vier `HORARIO_INDISPONIVEL` na repetição, aí sim o horário está ocupado. Vale para `POST /agendamentos`, `POST /agendamentos/guest` e `POST /agendamentos/{id}/remarcacao` (os três passam pela expiração preguiçosa + escrita). Ajuste da revisão do Alicerce no PR #2. Ajuste da revisão do Alicerce sobre a crítica 4 do Gemini ([`docs/ia/`](../ia/ataque-arquitetura-gemini-2026-11-13.md)).
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
