# 🗄️ ERD e DDL do Flyway V1 (proposta)

| | |
|---|---|
| 🎯 **Para que serve** | o modelo de dados do beta, as constraints que protegem as regras e o porquê de cada índice |
| 👥 **Área responsável** | backend · arquitetura: Alicerce |
| ⏰ **Quando usar** | antes de criar entidade JPA, consulta ou migração |
| 🧰 **Como** | ERD em Mermaid + o DDL em [`V1__agenda.proposta.sql`](V1__agenda.proposta.sql), que vira `backend/src/main/resources/db/migration/V1__agenda.sql` na fatia S01 |
| 🚫 **Não cobre** | regras de transição ([domínio](../dominio/modelo-de-dominio.md)) |

> Status: **proposta (13/11/2026)**, aguardando revisão do Alicerce (arquitetura). Os valores iniciais de `configuracao_agenda` são **HIPÓTESES** ([dossiê, seção 4](../contexto.md#4-hipóteses-em-aberto)).


> **Alinhado na Fatia 1 (16/11/2026, D31 a D35, D44)** na migração real [`V1__agenda.sql`](../../backend/src/main/resources/db/migration/V1__agenda.sql): sai `sinal_centavos`, `percentual_sinal`, `politica_sinal` e `janela_cancelamento_min`; entram `metodo` na cobrança, `aceite_politica_versao` e `aceite_politica_em` na reserva e a transição `EXPIRADO → CONFIRMADO`. O diagrama abaixo ainda mostra a proposta de 13/11; a verdade do schema é a migração.

## 1. ERD

Cada cor de módulo é dona das próprias tabelas (arquitetura do Alicerce, seção 3). FKs entre tabelas de módulos diferentes são permitidas no banco; no código, a referência é só pelo id.

```mermaid
erDiagram
    usuario ||--o{ usuario_papel : tem
    usuario ||--o{ refresh_token : "sessões"
    usuario ||--o{ identidade_social : "pós-beta"
    usuario |o--o| barbeiro : "login BARBEIRO"
    usuario ||--o{ reserva : "faz (cliente)"
    usuario ||--o{ consentimento : registra
    usuario ||--o{ solicitacao_titular : pede
    usuario ||--o{ auditoria_admin : "age (admin)"
    barbeiro ||--o{ jornada : trabalha
    barbeiro ||--o{ folga : "não atende"
    barbeiro ||--o{ reserva : atende
    servico ||--o{ reserva : "é feito em"
    reserva ||--o| reserva_guest : "contato do guest"
    reserva ||--o{ cobranca : "sinal"
    cobranca ||--o{ evento_webhook : "avisos do gateway"

    usuario {
        uuid id PK
        varchar nome
        varchar email UK "lower(email)"
        varchar whatsapp "só dígitos"
        varchar senha_hash "NULL só social"
        char cep "opcional"
        varchar bairro
        varchar cidade
        char uf
        int versao_credencial
        timestamptz anonimizado_em
    }
    usuario_papel {
        uuid usuario_id PK, FK
        varchar papel PK "CLIENTE BARBEIRO ADMIN"
    }
    refresh_token {
        uuid id PK
        uuid usuario_id FK
        uuid familia_id
        char hash UK "SHA-256"
        timestamptz expira_em
        timestamptz usado_em
        timestamptz revogado_em
    }
    identidade_social {
        uuid id PK
        uuid usuario_id FK
        varchar provedor "GOOGLE FACEBOOK"
        varchar sujeito
        boolean email_verificado
    }
    configuracao_agenda {
        smallint id PK "sempre 1"
        varchar fuso
        int antecedencia_minima_min "H5"
        int janela_cancelamento_min "H6"
        int intervalo_entre_atendimentos_min "H4"
        int granularidade_min "H11"
        int horizonte_dias "H12"
        int expiracao_guest_min "15"
        int percentual_sinal "50"
        varchar politica_sinal "H7"
    }
    servico {
        uuid id PK
        varchar nome UK
        int duracao_min
        bigint preco_centavos
        boolean ativo
        bigint version
    }
    barbeiro {
        uuid id PK
        varchar nome_exibicao
        uuid usuario_id FK, UK
        boolean ativo
    }
    jornada {
        uuid id PK
        uuid barbeiro_id FK
        smallint dia_semana "1 a 7 ISO"
        time hora_inicio
        time hora_fim
    }
    folga {
        uuid id PK
        uuid barbeiro_id FK
        timestamptz inicio
        timestamptz fim
        varchar motivo
    }
    reserva {
        uuid id PK
        uuid barbeiro_id FK
        uuid servico_id FK
        uuid cliente_id FK "NULL no guest"
        varchar origem "CLIENTE GUEST"
        timestamptz inicio
        timestamptz fim
        timestamptz fim_ocupacao
        varchar status
        bigint preco_centavos "congelado"
        bigint sinal_centavos "congelado"
        timestamptz expira_em
        bigint version
    }
    reserva_guest {
        uuid reserva_id PK, FK
        varchar nome
        varchar email
        varchar whatsapp
        char token_hash UK
        timestamptz anonimizado_em
    }
    cobranca {
        uuid id PK
        uuid reserva_id FK
        varchar provedor
        varchar id_cobranca_provedor UK
        bigint valor_centavos
        varchar status
        char chave_idempotencia_hash UK
        char hash_corpo
        text pix_copia_e_cola
    }
    evento_webhook {
        uuid id PK
        varchar provedor
        varchar id_evento_provedor UK "idempotência"
        varchar tipo
        varchar id_cobranca_provedor
        timestamptz recebido_em
        timestamptz processado_em
        smallint tentativas
        timestamptz ultima_tentativa_em
        varchar ultimo_erro
    }
    consentimento {
        uuid id PK
        uuid usuario_id FK
        varchar finalidade "MARKETING_CEP"
        varchar versao_texto
        boolean aceito
        timestamptz registrado_em
    }
    solicitacao_titular {
        uuid id PK
        uuid usuario_id FK
        varchar tipo "EXPORTACAO EXCLUSAO"
        timestamptz solicitada_em
        timestamptz concluida_em
    }
    auditoria_admin {
        uuid id PK
        uuid ator_id FK
        varchar acao
        varchar recurso
        uuid recurso_id
        jsonb detalhe "sem dado pessoal"
        timestamptz ocorrido_em
    }
```

Tabelas de infraestrutura do módulo `plataforma`, fora do diagrama: `shedlock` (trava do job) e `event_publication` (registro de eventos do Spring Modulith, que funciona como outbox).

## 2. Constraints que protegem regras

| Constraint | Protege | Regra |
|---|---|---|
| `reserva_sem_sobreposicao`: `EXCLUDE USING gist (barbeiro_id WITH =, tstzrange(inicio, fim_ocupacao, '[)') WITH &&) WHERE (status IN ('AGUARDANDO_PAGAMENTO','CONFIRMADO'))` | nenhum barbeiro com duas reservas sobrepostas, mesmo com requisições simultâneas; exige `btree_gist` | R1, R2 |
| `reserva_origem_ck` | cliente logado sem sinal; guest sem `cliente_id` | R6, R7 |
| `reserva_expiracao_ck` | toda reserva aguardando pagamento tem prazo | R7 |
| `reserva_valores_ck` | sinal entre 0 e o preço congelado | R8 |
| `cobranca_idempotencia_uk` | mesma `X-Idempotency-Key` não cria duas cobranças | R10 |
| `cobranca_ativa_por_reserva_uk` (índice único parcial) | no máximo uma cobrança viva por reserva | R10 |
| `evento_webhook_uk (provedor, id_evento_provedor)` | evento repetido não processa duas vezes (`INSERT ... ON CONFLICT DO NOTHING`). Se o evento já existe mas `processado_em IS NULL` (a API caiu ou falhou depois de gravar), ele é **reprocessado**, senão o pagamento se perderia; o processamento trava a linha (`FOR UPDATE`) e só marca `processado_em` na mesma transação que atualiza a cobrança | R12 |
| `usuario_email_uk` em `lower(email)` | e-mail único sem diferença de maiúsculas | R24 |
| `refresh_token_hash_uk` | refresh localizado pelo hash, nunca pelo valor | R27 |
| `configuracao_agenda_unica_ck` + faixas | uma configuração só, com valores sãos | H4 a H12 |

**Por que `now()` não está no filtro da constraint:** o `WHERE` de uma constraint `EXCLUDE` só aceita expressão imutável. Uma reserva `AGUARDANDO_PAGAMENTO` vencida continua no filtro até virar `EXPIRADO`; por isso existem a expiração preguiçosa na inserção (R16) e o job (R17), e a disponibilidade ignora as vencidas (R15).

## 3. Índices e a consulta que cada um atende

| Índice | Consulta |
|---|---|
| `reserva_barbeiro_inicio_ix (barbeiro_id, inicio)` | agenda do barbeiro no dia/semana; disponibilidade |
| `reserva_inicio_ix (inicio)` | agenda do dia de todos os barbeiros (painel) |
| `reserva_cliente_inicio_ix (cliente_id, inicio DESC)` parcial | "meus agendamentos" |
| `reserva_expiracao_ix (expira_em)` parcial em `AGUARDANDO_PAGAMENTO` | job de expiração |
| `folga_barbeiro_periodo_ix` gist | "há folga neste dia?" na disponibilidade |
| `jornada_barbeiro_dia_ix (barbeiro_id, dia_semana)` | jornada do dia na disponibilidade |
| `usuario_cidade_bairro_ix (cidade, bairro)` | painel de alcance (pós-beta), só agregados |
| `reserva_guest_retencao_ix (criado_em)` parcial | job de retenção do guest |
| `cobranca_devolucao_ix` parcial | painel: sinais a devolver |
| `evento_webhook_cobranca_ix` | histórico de eventos de uma cobrança |
| `auditoria_admin_*` | auditoria por data e por ator |
| `consentimento_usuario_ix` | último consentimento do usuário por finalidade |

Os planos (`EXPLAIN`) das consultas de disponibilidade e da agenda entram no PR da fatia que as cria.

## 4. Validação manual feita nesta etapa

Em 13/11/2026, o coordenador aplicou o DDL num **PostgreSQL 17.12** limpo (imagem `postgres:17` no Docker) e conferiu à mão, com dados fictícios:

| Caso | Resultado observado |
|---|---|
| DDL inteiro com `ON_ERROR_STOP` | aplicado sem erro |
| Reserva 10h00–10h30 e outra 10h15–10h45 do mesmo barbeiro | a segunda falhou com `23P01` em `reserva_sem_sobreposicao` |
| Reserva adjacente 10h30–11h00 | aceita (intervalo `[)`) |
| Reserva `CANCELADO` sobreposta | aceita (fora do filtro) |
| Cliente logado com sinal > 0 | recusada por `reserva_origem_ck` |
| Mesmo evento de webhook duas vezes com `ON CONFLICT DO NOTHING` | uma linha só |
| Duas transações simultâneas no mesmo horário (a primeira segura o commit por 2 s) | a segunda esperou e falhou com `23P01`; ficou 1 reserva |

**Revalidação depois da revisão do Alicerce (13/11/2026, PostgreSQL 17.12 no Docker):** DDL ajustado aplicado sem erro (sem a coluna `devolucao_pendente`, com os índices `cobranca_devolucao_ix` por status e `evento_webhook_pendente_ix`); o `EXPLAIN` das duas instruções de expiração (preguiçosa com `FOR UPDATE` e job com `FOR UPDATE SKIP LOCKED` em lote) passou; o segundo `INSERT ... ON CONFLICT DO NOTHING RETURNING id` do mesmo evento devolveu zero linhas e o `SELECT ... FOR UPDATE` seguinte achou o evento com `processado_em` nulo (caso de reprocessar).

**Revalidação do job de reprocessamento (13/11/2026, PostgreSQL 17.12 no Docker):** DDL com `tentativas`, `ultima_tentativa_em`, `ultimo_erro`, o check `evento_webhook_tentativas_ck` e os índices `evento_webhook_pendente_ix` e `evento_webhook_revisao_ix` aplicado sem erro. Com N = 10 min, a instrução de reivindicação do lote pegou só o evento não processado com 20 min; ignorou o de 2 min, o já processado e o que estava em `REVISAO_MANUAL`, e incrementou `tentativas`. Com outra sessão segurando `FOR UPDATE` num evento elegível, o `SKIP LOCKED` pulou esse evento. `tentativas = -1` foi recusado pelo check.

Isto **não** substitui os testes automatizados: na fatia S01, os mesmos casos viram testes com Testcontainers (`ReservaConstraintIT` e `ReservaConcorrenciaIT`).

**A conferir antes do deploy:** a extensão `btree_gist` no Aiven e a versão do PostgreSQL do plano free (o DDL usa só recursos presentes desde o PostgreSQL 13).
