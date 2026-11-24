# 📐 Spec SDD · Fatia 1 · Esqueleto + Flyway V1 + Docker Compose

| | |
|---|---|
| **Status** | **implementada** (16/11/2026): `./mvnw verify` com 26 testes, 0 falhas, 0 pulados ([PR #4](https://github.com/k3vinrich4rd/barbearia-altas-horas/pull/4)) |
| **Junta** | S00 (esqueleto) + S01 (banco V1) do plano interno |
| **Branch** | `feat/fatia-1-esqueleto-flyway` a partir da `origin/develop` |
| **Não cobre** | nenhuma rota de negócio; catálogo e disponibilidade são a Fatia 2 |

## 1. Entregas

| # | Entrega | Detalhe |
|---|---|---|
| E1 | Projeto Maven em `backend/` | Java 25, Spring Boot **4.1.2** (D11), pacote `io.github.k3vinrich4rd.altashoras`, wrapper `mvnw` |
| E2 | Módulos vazios | `comum`, `plataforma`, `conta`, `cep`, `agenda`, `pagamento`, `privacidade`, `admin` com `package-info.java` (`@ApplicationModule`) |
| E3 | Erros RFC 9457 | `TratadorGlobalDeErros` (`@RestControllerAdvice`) com `code` e `traceId`; `CodigoErro` com os códigos do contrato v1.1 |
| E4 | `Clock` injetável | bean `Clock` em `America/Sao_Paulo`; proibido `Instant.now()` direto (regra ArchUnit) |
| E5 | `compose.yaml` | PostgreSQL 18 na porta **5433** (D40); volume nomeado; healthcheck |
| E6 | Flyway `V1__agenda.sql` | a partir da [proposta](../banco/V1__agenda.proposta.sql) **já com o modelo novo** (abaixo) |
| E7 | Seed `dev` (`R__seed_dev.sql` só no perfil `dev`) | Carlos Eduardo Candido, jornada ter–sáb 07–12 e 13–20, 5 serviços reais, configuração (120 min, 30 min, 30 dias, 15 min) |
| E8 | CI | GitHub Actions: `./mvnw verify`, CodeQL, gitleaks |

### Mudanças no DDL em relação à proposta (D31 a D35)

| Sai | Entra |
|---|---|
| `reserva.sinal_centavos`, `reserva_valores_ck`, regra "cliente logado sem sinal" em `reserva_origem_ck` | `reserva.aceite_politica_versao int null`, `reserva.aceite_politica_em timestamptz null` (preenchidos na criação da cobrança) |
| `configuracao_agenda.percentual_sinal`, `politica_sinal`, `janela_cancelamento_min` | `configuracao_agenda.expiracao_pagamento_min` (15) e tabela `politica_pagamento (versao pk, texto, vigente_desde)` |
| `cobranca` só Pix | `cobranca.metodo varchar check in ('PIX','CARTAO_CREDITO')` |
| `expira_em` só para guest | `expira_em not null` quando `status = 'AGUARDANDO_PAGAMENTO'`, para cliente e guest |

## 2. Critérios de aceite (Dado / Quando / Então)

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | PostgreSQL do compose no ar | `GET /actuator/health` | `200 {"status":"UP"}` | `ContextoSobeIT` (Testcontainers) |
| A2 | API no ar | `GET /rota-que-nao-existe` | `404` `application/problem+json` com `code: RECURSO_NAO_ENCONTRADO` e `traceId` | `ErroPadraoTest` |
| A3 | banco vazio | Flyway roda | V1 aplica sem erro; `btree_gist`, constraint `reserva_sem_sobreposicao` e índices existem | `V1MigracaoIT` |
| A4 | duas reservas `[10:00,10:30)` no mesmo barbeiro | segundo `INSERT` | falha com SQLState `23P01` | `SobreposicaoConstraintIT` |
| A5 | reservas encostadas `[10:00,10:30)` e `[10:30,11:00)` | inserir as duas | ambas passam (`[)`) | `SobreposicaoConstraintIT` |
| A6 | reserva `CANCELADO` ou `EXPIRADO` no horário | inserir outra no mesmo horário | passa | `SobreposicaoConstraintIT` |
| A7 | N threads disputando o mesmo horário | inserções simultâneas | exatamente 1 vence | `ConcorrenciaReservaIT` |
| A8 | `AGUARDANDO_PAGAMENTO` sem `expira_em` | inserir | check constraint recusa | `V1MigracaoIT` |
| A9 | perfil `dev` | subir | seed com 1 barbeiro, 5 serviços (R$ 45, 55, 60, 30, 20; todos com `duracao_min = 60`, D94) e jornada com almoço | `SeedDevIT` |
| A10 | código do projeto | `./mvnw verify` | regras ArchUnit e `ApplicationModules.verify()` verdes | `ArquiteturaTest`, `ModulithTest` |

## 3. Riscos e cuidados
- `btree_gist` precisa de `CREATE EXTENSION` na V1; no Aiven, conferir se a extensão é permitida antes da Fatia 5.
- Seed nunca em `prod` (`spring.flyway.locations` por perfil).
- Hikari com no máximo 5 conexões já no `application.yml` (D12).
