# 🧪 Rodar e testar · Barbearia Altas Horas

> [!IMPORTANT]
> ✅ = rodado de verdade, com a saída copiada da execução (16/11/2026, Fatia 1). ⏳ = vira ✅ quando a fatia dele entrar na `develop`.

| | |
|---|---|
| 🎯 **Para que serve** | subir o projeto do zero e conferir, em ~15 minutos, as regras que mais importam |
| 👥 **Para quem** | recrutador, revisor ou quem for usar o projeto como template |
| 🧰 **Precisa de** | Docker Desktop · JDK 25 · um navegador · `curl` |
| 🔒 **Sem segredo** | tudo roda com dados **fictícios** e o gateway em **sandbox** (nenhum dinheiro real) |

## Sumário
1. [Subir o projeto](#1-subir-o-projeto)
2. [Backend: a API sozinha](#2-backend-a-api-sozinha)
3. [Frontend: as telas](#3-frontend-as-telas)
4. [Os dois juntos: a jornada completa](#4-os-dois-juntos-a-jornada-completa)
5. [Testes automatizados](#5-testes-automatizados)

---

## 1. Subir o projeto

```bash
git clone https://github.com/k3vinrich4rd/barbearia-altas-horas.git
cd barbearia-altas-horas
docker compose up -d                                         # PostgreSQL 18 na porta 5433
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

O perfil `dev` funciona sem `.env` (usa os padrões locais do `compose.yaml`) e cria dados fictícios: o barbeiro **Carlos Eduardo Cândido**, os 5 serviços reais com preço e a jornada de terça a sábado (07h–12h e 13h–20h).

| O que sobe | Endereço | |
|---|---|:-:|
| API | `http://localhost:8080` | ✅ |
| Health | `http://localhost:8080/actuator/health` | ✅ |
| Front (Next.js) | `http://localhost:3000` | ⏳ Fatia 7 |
| API e front dentro do compose | `docker compose up --build` | ⏳ Fatia 8 |

Resposta real do health (16/11/2026):

```http
HTTP/1.1 200
X-Trace-Id: 282f13e49d0c490fa4bd12f6194c37b9
Content-Type: application/vnd.spring-boot.actuator.v3+json

{"groups":["liveness","readiness"],"status":"UP"}
```

E de uma rota que ainda não existe, no padrão RFC 9457 que toda a API vai seguir:

```http
HTTP/1.1 404
Content-Type: application/problem+json

{"detail":"O recurso pedido não existe.","instance":"/servicos","status":404,"title":"Não encontrado",
 "type":"https://github.com/k3vinrich4rd/barbearia-altas-horas/blob/main/docs/contrato/contrato-api.md#recurso_nao_encontrado",
 "code":"RECURSO_NAO_ENCONTRADO","traceId":"bf302b905aa348aabe50c5322f03f4b6","errors":[]}
```

> [!TIP]
> Já tem um PostgreSQL instalado na 5432? Por isso o compose usa a **5433**. Para outra porta: `POSTGRES_PORTA=5444 docker compose up -d` e a mesma variável ao subir a API.

## 2. Backend: a API sozinha

| # | O que fazer | O que deve acontecer | O que prova | |
|---|---|---|---|:-:|
| 1 | `GET /actuator/health` | `200 UP` | a API subiu e o Flyway aplicou as migrações | ✅ |
| 2 | `GET /servicos` | os 5 serviços com preço em centavos | catálogo vem do banco | ⏳ |
| 3 | `GET /disponibilidade?servicoId=…&data=…` | horários de 30 em 30 min, sem 12h00–13h00 e só a partir de agora + 2 h | regras da agenda como dado | ⏳ |
| 4 | cadastro e login (`POST /auth/cadastro`, `POST /auth/login`) | `201` e `200` | autenticação | ⏳ |
| 5 | `POST /agendamentos` | `201` com `AGUARDANDO_PAGAMENTO` e `expiraEm` (15 min) | horário travado até pagar | ⏳ |
| 6 | repetir o passo 5 no mesmo horário | `409 HORARIO_INDISPONIVEL` em `application/problem+json` | anti double booking no banco + RFC 9457 | ⏳ |
| 7 | `POST /pagamentos/cobrancas` **sem** `aceitePolitica` | `422 ACEITE_POLITICA_OBRIGATORIO` | ninguém paga sem aceitar a política | ⏳ |
| 8 | mesma chamada com `"metodo": "PIX"` e `"aceitePolitica": true` | `201` com QR Code e copia e cola de **100%** do preço | pagamento integral | ⏳ |
| 9 | confirmar a cobrança no painel do **sandbox** do gateway | o webhook chega e a reserva vira `CONFIRMADO` | só o gateway confirma | ⏳ |
| 10 | reenviar o mesmo webhook | `200` e nada muda | idempotência | ⏳ |
| 11 | criar outra reserva e não pagar por 15 min | vira `EXPIRADO` e o horário volta no passo 3 | ninguém segura horário de graça | ⏳ |
| 12 | `POST /agendamentos/{id}/cancelamento` | `404` | não existe cancelamento pelo cliente | ⏳ |

## 3. Frontend: as telas

| # | Tela | O que fazer | O que deve acontecer | |
|---|---|---|---|:-:|
| 1 | Início | abrir `http://localhost:3000` | preto, branco e prata, logo da barbearia e botão "Agendar" | ⏳ |
| 2 | Agendar | escolher serviço, dia e horário | vai para o checkout com contagem de 15 min | ⏳ |
| 3 | Checkout | tentar pagar sem marcar o aceite | botão de pagar desabilitado | ⏳ |
| 4 | Checkout | marcar o aceite e escolher Pix | aparece o QR Code | ⏳ |
| 5 | Checkout | escolher Cartão | abre o checkout do gateway (sandbox) | ⏳ |
| 6 | Meus horários | abrir depois de pagar | "Confirmado", **sem** botões de cancelar ou remarcar | ⏳ |
| 7 | Celular | modo mobile do navegador | layout legível e navegação por teclado | ⏳ |

## 4. Os dois juntos: a jornada completa

1. `docker compose up --build` e conferir o health.
2. No front, cadastrar um cliente fictício e agendar um **Corte Degradê** (R$ 55,00).
3. Aceitar a política e pagar com **Pix** no sandbox: a tela muda sozinha para "Confirmado".
4. Numa janela anônima, agendar o horário seguinte **sem conta** e pagar com **cartão** de teste do sandbox.
5. Numa terceira reserva, não pagar: depois de 15 min o horário volta a aparecer livre.
6. Entrar no painel com o admin do seu `.env`, concluir um atendimento e marcar falta em outro.
7. Conferir o log da API: nenhum CPF, telefone ou CEP aparece.

**Pronto quando:** os 7 passos passam numa base limpa, sem mexer no banco à mão.

## 5. Testes automatizados

```bash
cd backend && ./mvnw verify     # unitários, integração com PostgreSQL real (Testcontainers) e arquitetura
```

Resultado real da Fatia 1 (16/11/2026, Docker Desktop no Windows): **26 testes, 0 falhas, 0 pulados**, `BUILD SUCCESS`, cobertura de instruções **87%** (JaCoCo em `backend/target/site/jacoco/`).

| Classe | Testes | O que prova |
|---|:-:|---|
| `ContextoSobeIT` | 1 | a API sobe no PostgreSQL real e o health responde UP |
| `ErroPadraoTest` | 3 | 404 e 405 em `problem+json` com `code` e `traceId`; `X-Trace-Id` fora do formato é descartado |
| `V1MigracaoIT` | 6 | V1 aplicada, `btree_gist`, constraint e índices; regras reais na configuração; o banco recusa `AGUARDANDO_PAGAMENTO` sem prazo, `CONFIRMADO` sem aceite e método de cobrança fora de PIX/cartão |
| `SobreposicaoConstraintIT` | 6 | mesmo horário e sobreposição parcial dão `23P01`; encostadas `[)` passam; `CANCELADO` e `EXPIRADO` não ocupam |
| `ConcorrenciaReservaIT` | 3 | 12 conexões disputando o mesmo horário: **exatamente 1 vence** (repetido 3 vezes) |
| `SeedDevIT` | 3 | perfil `dev` com Carlos, os 5 serviços e a jornada com almoço |
| `ModulithTest` | 2 | 8 módulos e fronteiras verificadas |
| `ArquiteturaTest` | 2 | ninguém lê o relógio do sistema fora do `Clock`; dinheiro nunca em `double` |

| Tipo | Ferramenta | Cobre |
|---|---|---|
| Unitário | JUnit 5 · Mockito · AssertJ | regras da agenda, valor da cobrança, máquina de estados |
| Integração | Spring Boot Test · Testcontainers (PostgreSQL) | constraint anti-sobreposição, concorrência, webhook, expiração |
| Arquitetura | ArchUnit · Spring Modulith | fronteiras entre módulos, uso do `Clock` |
| Contrato | Redocly CLI | o `openapi.yaml` sem erro de lint |

Contrato atual (v1.1.1): `npx @redocly/cli@2.78.0 lint docs/contrato/openapi.yaml` → **0 erros** (✅ executado em 16/11/2026).
