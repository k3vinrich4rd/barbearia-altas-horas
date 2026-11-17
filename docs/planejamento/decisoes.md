# 🧭 Registro de decisões

| | |
|---|---|
| 🎯 **Para que serve** | a lista curta de tudo o que foi decidido, com fonte, data e onde está o detalhe |
| 👥 **Quem usa** | o Kevin, o Alicerce (arquitetura) e quem pega a próxima fatia |
| ⏰ **Quando usar** | antes de rediscutir algo já decidido; ao abrir um ADR novo |
| 🧰 **Como** | uma linha por decisão; o porquê completo está no ADR ou no documento citado |
| 🚫 **Não cobre** | regras ainda em aberto: H13 a H15 e H18 a H20 no [dossiê](../contexto.md#4-hipóteses-em-aberto) |

Status: **proposta** = aguarda revisão; **aceita** = aprovada pelo Kevin.

| # | Data | Decisão | Status | Fonte | Detalhe |
|---|---|---|---|---|---|
| D1 | 13/11/2026 | Monólito modular com Spring Modulith, 8 módulos, 14 regras ArchUnit | aceita (revisão do Alicerce, 13/11/2026) | arquitetura do Alicerce; comparação ponderada no ADR | [ADR 0001](../adr/0001-monolito-modular-com-spring-modulith.md), [arquitetura](../arquitetura/README.md) |
| D2 | 13/11/2026 | Módulos conversam por API pública e eventos; `event_publication` como outbox | aceita (revisão do Alicerce, 13/11/2026) | documentação do Spring Modulith | [ADR 0002](../adr/0002-comunicacao-entre-modulos.md) |
| D3 | 13/11/2026 | Anti double booking no banco: `EXCLUDE USING gist` com `tstzrange '[)'` + `btree_gist`; expiração preguiçosa com lock ordenado | aceita (revisão do Alicerce, 13/11/2026) | documentação do PostgreSQL; validado à mão no PostgreSQL 17 ([ERD](../banco/erd.md#validação-manual)) | [ADR 0003](../adr/0003-anti-double-booking-no-banco.md) |
| D4 | 13/11/2026 | Tempo em `timestamptz` + `Clock` injetado; dinheiro em centavos (`long`) | aceita (revisão do Alicerce, 13/11/2026) | — | [ADR 0004](../adr/0004-tempo-e-dinheiro.md) |
| D5 | 13/11/2026 | Erros RFC 9457 com `code` e `traceId`; 404 para recurso alheio | aceita (revisão do Alicerce, 13/11/2026) | RFC 9457; OWASP API Security Top 10 2023 | [ADR 0005](../adr/0005-erros-rfc-9457.md) |
| D6 | 13/11/2026 | JWT curto + refresh rotativo em cookie `HttpOnly` pelo BFF do Next.js | aceita (revisão do Alicerce, 13/11/2026) | — | [ADR 0006](../adr/0006-sessao-jwt-refresh-cookie-bff.md) |
| D7 | 13/11/2026 | Gateway: **Asaas sandbox** (Mercado Pago como alternativa documentada) | proposta | `https://docs.asaas.com/docs/sandbox`, `https://docs.asaas.com/docs/sobre-os-webhooks`, `https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks` (lidos em 13/11/2026) | [ADR 0007](../adr/0007-gateway-de-pagamento-sandbox.md) |
| D8 | 13/11/2026 | Webhook: idempotência pelo `id` do evento + reconsulta da cobrança; conciliação no `GET` do guest | proposta | Asaas: entrega "pelo menos uma vez" e fila pausada após 15 falhas (`https://docs.asaas.com/docs/sobre-os-webhooks`) | [ADR 0007](../adr/0007-gateway-de-pagamento-sandbox.md) |
| D9 | 13/11/2026 | CEP: ViaCEP → BrasilAPI, cache Caffeine, nunca derruba o cadastro | proposta | — | [ADR 0008](../adr/0008-cep-com-cache-e-degradacao.md) |
| D10 | 13/11/2026 | CPF **não é guardado** (só em trânsito para o gateway); marketing por consentimento opcional | proposta (diverge do Alicerce) | LGPD art. 6º, III (necessidade) | [ADR 0009](../adr/0009-lgpd-marketing-cpf-e-retencao.md) |
| D11 | 13/11/2026 | Java 25 LTS + **Spring Boot 4.1.2** (GA de 22/10/2026); a 4.2.0 ainda é RC | proposta | `https://api.github.com/repos/spring-projects/spring-boot/releases` (consultado em 13/11/2026) | [ADR 0010](../adr/0010-versoes-da-plataforma.md) |
| D12 | 13/11/2026 | Pool Hikari de **5** conexões (Aiven free: `max_connections` 20, 3 a 6 reservadas) e nenhuma chamada HTTP dentro de transação | proposta | `https://aiven.io/docs/products/postgresql/reference/pg-connection-limits` | verificação interna (13/11/2026) |
| D13 | 13/11/2026 | Render free hiberna em 15 min: job de expiração roda também na subida; correção não depende dele (R15, R16) | proposta | `https://render.com/docs/free` | [arquitetura](../arquitetura/README.md), ataque interno de arquitetura |
| D14 | 13/11/2026 | Vercel Hobby só para o beta de portfólio (uso não comercial); uso real com clientes pagando exige rever (RISC-03, H17) | proposta | `https://vercel.com/docs/limits/fair-use-guidelines`, `https://vercel.com/legal/terms` | [dossiê](../contexto.md#7-riscos-e-ameaças) |
| D15 | 13/11/2026 | Regras da barbearia como dados (`configuracao_agenda`, serviços, jornada), nunca fixas no código | proposta | prompt aprovado pelo Kevin | [DDL](../banco/V1__agenda.proposta.sql) |
| D16 | 13/11/2026 | Ordem do trabalho: backend S00 a S16, front F00 a F05, deploy por último | proposta | prompt aprovado pelo Kevin (seção 8) | [roadmap](../../README.md#-roadmap-em-fatias) |

### Ajustes da revisão do Alicerce (13/11/2026)

O PR #1 foi aprovado com ajustes pelo Alicerce (arquitetura). Cada linha abaixo resolve um item da revisão.

| # | Item | Decisão | Status | Fonte | Detalhe |
|---|---|---|---|---|---|
| D17 | 1 | `hash_corpo` passa a ser **HMAC-SHA256** de `reservaId` + CPF com o segredo `IDEMPOTENCIA_HMAC_SEGREDO` (variável de ambiente); SHA-256 puro permitiria achar o CPF por força bruta (10^9 combinações) | proposta | revisão do Alicerce | [DDL](../banco/V1__agenda.proposta.sql), [contrato](../contrato/contrato-api.md#1-convenções), [ADR 0009](../adr/0009-lgpd-marketing-cpf-e-retencao.md) |
| D18 | 2 | `cpfMascarado` só no `201` da criação da cobrança (schema `CobrancaCriada`); sai da repetição `200` e da listagem do painel | proposta | revisão do Alicerce | [contrato 4.7](../contrato/contrato-api.md#47-pagamento-do-sinal), [OpenAPI](../contrato/openapi.yaml) |
| D19 | 3 | Expiração preguiçosa numa instrução só (`UPDATE ... WHERE id IN (SELECT id ... ORDER BY id FOR UPDATE)`), incrementando `version`, `:agora` do `Clock`, mesmo caminho na remarcação; `40P01` refaz a transação inteira uma vez; o risco real de deadlock é com o job | proposta | revisão do Alicerce; sintaxe conferida com `EXPLAIN` no PostgreSQL 17.12 | [ADR 0003](../adr/0003-anti-double-booking-no-banco.md) |
| D20 | 4 | Job de expiração em lotes com `SELECT id ... ORDER BY id LIMIT :lote FOR UPDATE SKIP LOCKED` | proposta | revisão do Alicerce; `EXPLAIN` no PostgreSQL 17.12 | [arquitetura 6.2](../arquitetura/README.md#62-expiração) |
| D21 | 5 | Cobrança gravada **antes** como `PENDENTE` (chave de idempotência, id do provedor nulo), Asaas chamado com `externalReference = cobranca.id`, depois a linha recebe o id do Asaas. A chave de idempotência é **nossa**: não está confirmado que o Asaas aceite uma | proposta | revisão do Alicerce | [arquitetura 6.1](../arquitetura/README.md#61-guest-com-sinal-caminho-feliz-e-de-erro), [ADR 0007](../adr/0007-gateway-de-pagamento-sandbox.md) |
| D22 | 6 | Webhook: evento que já existe com `processado_em IS NULL` é **reprocessado** (trava `FOR UPDATE`, marca `processado_em` na mesma transação da cobrança); falha responde `5xx` para o Asaas reenviar; índice parcial para varrer os não processados | proposta | revisão do Alicerce; fila do Asaas pausa após 15 falhas (`https://docs.asaas.com/docs/sobre-os-webhooks`) | [DDL](../banco/V1__agenda.proposta.sql), [ERD](../banco/erd.md#2-constraints-que-protegem-regras), [ADR 0007](../adr/0007-gateway-de-pagamento-sandbox.md) |
| D23 | 7 | `summary` em todas as operações do OpenAPI. `npx @redocly/cli lint` (2.78.0, regras recomendadas): antes 22 erros e 14 avisos; depois **0 erros e 14 avisos** (licença sem URL, servidor localhost, tags sem descrição, 3 operações sem 4xx, caminho ambíguo `/agendamentos/{id}/...` × `/agendamentos/guest/{id}`) | aceita (verificável) | execução real em 13/11/2026 | [OpenAPI](../contrato/openapi.yaml) |
| D24 | 8 | ADR 0002 cita o pacote `<modulo>.eventos`, o `@NamedInterface("eventos")` e o `allowedDependencies` | aceita (revisão do Alicerce, 13/11/2026) | arquitetura do Alicerce | [ADR 0002](../adr/0002-comunicacao-entre-modulos.md) |
| D25 | 9 | O BFF guarda o token de acesso em outro cookie `HttpOnly; Secure; SameSite=Strict; Path=/api` com `Max-Age` igual à validade do JWT (as funções da Vercel não têm estado) | aceita (revisão do Alicerce, 13/11/2026) | — | [ADR 0006](../adr/0006-sessao-jwt-refresh-cookie-bff.md) |
| D26 | 10 | Sai a coluna `devolucao_pendente`; "a devolver" é o status (`PAGA_APOS_EXPIRACAO` ou `DEVOLUCAO_PENDENTE`), com índice parcial por status | proposta | revisão do Alicerce; DDL reaplicado sem erro no PostgreSQL 17.12 | [DDL](../banco/V1__agenda.proposta.sql), [modelo de domínio](../dominio/modelo-de-dominio.md) |

### Segundo ajuste da revisão do Alicerce no PR #2 (13/11/2026)

| # | Decisão | Status | Fonte | Detalhe |
|---|---|---|---|---|
| D27 | Deadlock (`40P01`) que persiste depois do retry da transação responde **`409 CONFLITO_CONCORRENTE`** ("tente de novo"), não `HORARIO_INDISPONIVEL`, que faria o front esconder um horário que pode estar livre. Vale para `POST /agendamentos`, `POST /agendamentos/guest` e remarcação; o front pode repetir **uma vez**. Substitui a proposta de `409 HORARIO_INDISPONIVEL` feita em D19 | aceita (revisão do Alicerce) | revisão do Alicerce | [ADR 0003](../adr/0003-anti-double-booking-no-banco.md), [ADR 0005](../adr/0005-erros-rfc-9457.md), [contrato](../contrato/contrato-api.md#conflito_concorrente), [OpenAPI](../contrato/openapi.yaml) (`Erro409Agendamento`) |
| D28 | Job de reprocessamento de eventos de webhook presos: ShedLock, a cada 5 min e na subida; só `processado_em IS NULL` e `recebido_em < agora - N` (`agora` do `Clock`); lotes com `FOR UPDATE SKIP LOCKED`; mesmo caminho idempotente do webhook; conta `tentativas` e, na M-ésima falha, marca `REVISAO_MANUAL` para o painel. N e M são as HIPÓTESES **H19** (10 min) e **H20** (5) | aceita (revisão do Alicerce); N e M em aberto | revisão do Alicerce; DDL e instrução de lote conferidos no PostgreSQL 17.12 (Docker), inclusive o `SKIP LOCKED` com outra sessão segurando a trava | [ADR 0007](../adr/0007-gateway-de-pagamento-sandbox.md), [arquitetura 6.2.1](../arquitetura/README.md#621-reprocessamento-de-eventos-de-webhook-presos), [DDL](../banco/V1__agenda.proposta.sql), [ERD](../banco/erd.md) |
| D29 | ADRs 0001 a 0006 passam a **aceito**, em 13/11/2026, pela revisão do Alicerce | aceita | revisão do Alicerce no PR #2 | [ADRs](../adr/README.md) |
| D30 | Verificação do OpenAPI depois destes ajustes: `npx @redocly/cli lint` 2.78.0 (regras recomendadas) com **0 erros e 14 avisos** (os mesmos 14 de D23); `openapi-spec-validator` 0.9.0: **OK** | aceita (verificável) | execução real em 13/11/2026 | [OpenAPI](../contrato/openapi.yaml) |

### Definições oficiais da barbearia (16/11/2026)

O Kevin confirmou com a barbearia as regras reais. Elas fecham as HIPÓTESES H1 a H8, H10 a H12, H16 e H17 do [dossiê](../contexto.md#4-hipóteses-em-aberto).

| # | Decisão | Status | Fonte | Detalhe |
|---|---|---|---|---|
| D31 | **Pagamento 100% antecipado** para todo agendamento (cliente logado e guest), por **Pix** ou **cartão de crédito**; o conceito de "sinal" sai do modelo. Cliente logado também nasce `AGUARDANDO_PAGAMENTO` | aceita | Kevin, 16/11/2026 | [contrato 4.7](../contrato/contrato-api.md#47-pagamento-100-antecipado), R6 a R8 |
| D32 | Cartão de crédito só pelo **checkout hospedado do gateway** (`urlCheckout`): o número do cartão nunca passa pela API nem pelo front próprio, o que tira o projeto do escopo pesado do PCI DSS | proposta | decisão técnica do Claude (orquestração) | [OpenAPI](../contrato/openapi.yaml) `Cobranca.cartao` |
| D33 | **Sem cancelamento e sem remarcação pelo cliente**: as rotas `/agendamentos/{id}/cancelamento` e `/remarcacao` saem do contrato; só a barbearia cancela, com motivo | aceita | Kevin, 16/11/2026 | R18, R22 |
| D34 | **Aceite obrigatório** antes do pagamento: `aceitePolitica: true` + `versaoPolitica` no corpo da cobrança; a reserva guarda versão e data do aceite (prova para o caso de contestação); sem aceite → `422 ACEITE_POLITICA_OBRIGATORIO` | aceita | Kevin, 16/11/2026 | R18, [contrato](../contrato/contrato-api.md#aceite_politica_obrigatorio) |
| D35 | **Pagamento tardio**: com o horário livre, a reserva volta de `EXPIRADO` para `CONFIRMADO` pela mesma constraint; com o horário ocupado, `PAGA_APOS_EXPIRACAO` vira pendência administrativa | aceita | Kevin, 16/11/2026 | R14 |
| D36 | Agenda: terça a sábado, 07h00 às 20h00, almoço 12h00 às 13h00 (intervalo editável da jornada), slots de 30 min, sem intervalo entre clientes, antecedência de 2 h, horizonte de 30 dias; 1 barbeiro (Carlos Eduardo Cândido, ADMIN + BARBEIRO) sem apagar a entidade `Barbeiro` | aceita | Kevin, 16/11/2026 | R3 a R5, H1 a H5, H11, H12 |
| D37 | Catálogo real com 5 serviços e sem combos; "Corte Moderno / Visagismo" dura 60 min (2 slots) | aceita | Kevin, 16/11/2026 | H3 |
| D38 | Deploy em duas fases: **Fase 1** gratuita (Vercel + Render + Aiven, gateway em sandbox); **Fase 2** (produção real e lojas mobile) planejada na área interna da equipe | aceita | Kevin, 16/11/2026 | H17 |
| D39 | Documentação pública enxuta: o repositório mostra código, README, contrato, ADRs, arquitetura e guia rápido; pesquisas de IA, recados entre agentes, plano de fatias detalhado, credenciais e roteiros completos ficam em `.equipe/` (ignorada pelo Git, padrão do RKF Bank) | aceita | Kevin, 16/11/2026 | [README](../../README.md) |

### Fatia 1 (16/11/2026)

| # | Decisão | Status | Fonte | Detalhe |
|---|---|---|---|---|
| D40 | PostgreSQL local do `compose.yaml` na porta **5433** (`POSTGRES_PORTA`): a 5432 costuma estar ocupada por um PostgreSQL instalado no Windows, e a API conectava nele sem avisar | aceita | erro real ao subir o perfil `dev` em 16/11/2026 (`autenticação do tipo senha falhou`, mensagem do PostgreSQL local) | [compose.yaml](../../compose.yaml) |
| D41 | `event_publication` (outbox do Modulith) e uso do ShedLock entram na fatia que publicar o primeiro evento ou agendar o primeiro job, numa `V2`, com o schema oficial da versão fixada no `pom.xml`; a V1 já cria a tabela `shedlock` | aceita | a proposta de 13/11 pedia conferir o schema oficial antes | [V1](../../backend/src/main/resources/db/migration/V1__agenda.sql) |
| D42 | Erros do Spring MVC (404 de rota, 405, 415, JSON malformado, validação) passam pelo mesmo tratador e saem em RFC 9457 com `code` e `traceId`; códigos novos `METODO_NAO_PERMITIDO` e `MIDIA_NAO_SUPORTADA` (contrato 1.1.1) | aceita | `ErroPadraoTest` | [contrato](../contrato/contrato-api.md#5-erros) |
| D43 | `traceId` de 32 hex por requisição (MDC, cabeçalho `X-Trace-Id` e corpo do erro); um `X-Trace-Id` recebido só vale no formato W3C, para ninguém injetar texto no log | aceita | `ErroPadraoTest` | `RastreioFilter` |
| D44 | Banco garante as regras de pagamento além do código: `reserva_aceite_ck` (só confirma quem aceitou a política), `reserva_expiracao_ck` (todo `AGUARDANDO_PAGAMENTO` tem prazo) e `cobranca_metodo_ck` (só PIX ou CARTAO_CREDITO) | aceita | `V1MigracaoIT` | [V1](../../backend/src/main/resources/db/migration/V1__agenda.sql) |

### Fatia 2 (16/11/2026)

| # | Decisão | Status | Fonte | Detalhe |
|---|---|---|---|---|
| D45 | Arquitetura: **manter o monólito modular** e usar portas e adaptadores só onde há regra difícil ou sistema externo (disponibilidade, reserva, pagamento, CEP); CRUD fica em camadas simples | aceita | pergunta do Kevin sobre reescrever em hexagonal | [ADR 0011](../adr/0011-hexagonal-seletivo-no-monolito-modular.md) |
| D46 | `hibernate.type.java_time_use_direct_jdbc: true`: com `jdbc.time_zone: UTC`, o Hibernate aplicava o fuso também às colunas `time` e a jornada das 07:00 era lida como 04:00 | aceita | falha real do teste A27 (`expected 07:00 but was 04:00`) | `application.yml` |
| D47 | No teste de concorrência, quem perde a disputa pode receber `23P01` **ou** `40P01` (deadlock entre as que esperam); o que não muda é **uma** reserva vencedora. Confirma na prática o retry + `409 CONFLITO_CONCORRENTE` previsto em D27 | aceita | falha real do `ConcorrenciaReservaIT` com a máquina sob carga | [ADR 0003](../adr/0003-anti-double-booking-no-banco.md) |
| D48 | `JOIN FETCH` sem `DISTINCT`: o Hibernate 6+ já devolve cada entidade uma vez, e o PostgreSQL recusa `SELECT DISTINCT` com `ORDER BY lower(...)` | aceita | erro real do PostgreSQL na primeira execução | `BarbeiroRepository` |
| D49 | Foco do produto: a agenda é **da Barbearia Altas Horas e do Eduardo Cândido**. O modelo aceita mais de um barbeiro (entidade `Barbeiro`), mas nada é construído para outras barbearias agora | aceita | Kevin, 16/11/2026 | [README](../../README.md) |
| D50 | GitHub Copilot entra no fluxo como **revisor e executor de tarefas pequenas**, guiado por `.github/copilot-instructions.md` (as mesmas regras do `CLAUDE.md`) e com o ambiente do agente definido em `copilot-setup-steps.yml` | aceita | Kevin, 16/11/2026 | [automação](../automacao.md) |

### Fatia 3 (17/11/2026)

| # | Decisão | Status | Fonte | Detalhe |
|---|---|---|---|---|
| D51 | "Esqueci minha senha" por e-mail fica para depois do beta; nesta fatia entra a **troca de senha logado** (`PUT /me/senha`) | aceita | Kevin, 17/11/2026 | [spec da Fatia 3](../sdd/fatia-3-conta-e-acesso.md) |
| D52 | Limites e validades adotados (fecham H14 e H15): login 5 erros em 15 min por e-mail + IP e 20 por IP; cadastro 5 por hora por IP; acesso 15 min; refresh 7 dias, renovado a cada uso | aceita | Kevin, 17/11/2026 | `application.yml` (`app.limites.*`, `app.sessao.*`) |
| D53 | Admin inicial = o Eduardo, com `ADMIN` e `BARBEIRO`, vinculado ao cadastro de barbeiro por **evento** (`AdminInicialCriado`), e com troca obrigatória da senha inicial (`403 TROCA_DE_SENHA_OBRIGATORIA`) | aceita | Kevin, 17/11/2026 | `AdminInicial`, `VinculoDoBarbeiro` |
| D54 | **Negar por padrão**: só as rotas listadas são públicas; rota não declarada responde `401` a quem não está logado. Consequência: o `404` de rota inexistente só aparece para quem tem login | aceita | spec da Fatia 3 (E13) | `SegurancaConfig` |
| D55 | JWT validado pelo Resource Server do Spring Security com o **`Clock` injetado** (o validador padrão usa o relógio do sistema e o teste de expiração dependeria da data do dia) | aceita | implementação | `JwtConfig` |
| D56 | O refresh é opaco e não carrega a versão da credencial: senha trocada **revoga todos os refresh do usuário** em vez de comparar versões na renovação (ajuste de E8) | aceita | implementação | `Sessoes.trocarSenha` |
| D58 | CSRF **ligado** e exigido para requisição que altera dados e chega com cookie, em vez de desligado. O CodeQL acusou o CSRF desligado; o Kevin pediu que o alerta deixasse de existir em vez de ser dispensado. Para o uso normal da API (token no cabeçalho) nada muda; qualquer fluxo futuro com cookie já nasce protegido | aceita | alerta `java/spring-disabled-csrf-protection` na PR #16 | `SegurancaConfig`, teste A44 |
| D57 | Fluxo de entrega: feature → `develop` → `main` automático, com CI verde, sem aguardar aprovação do merge; a spec de cada fatia continua precisando do OK | aceita | Kevin, 17/11/2026 | [SDD](../sdd/README.md) |

### Fatia 4 (17/11/2026)

| # | Decisão | Status | Fonte | Detalhe |
|---|---|---|---|---|
| D59 | **Beta sem gateway real**: pagamento em modo `simulado` em todo o beta, inclusive no deploy gratuito; nenhuma conta em serviço externo, nenhum custo e nenhum dinheiro real. O adaptador do Asaas é escrito e testado, mas só é ligado (`app.pagamento.modo=asaas`) na saída do beta | aceita | Kevin, 17/11/2026 | [spec da Fatia 4](../sdd/fatia-4-agendamento-e-pagamento.md) (E28) |
| D60 | Fatia 4 em duas PRs: 4A (reserva e expiração) e 4B (cobrança e webhook) | aceita | Kevin, 17/11/2026 | spec da Fatia 4 |
| D61 | Antes da produção, revisar com advogado a regra "sem cancelamento e sem estorno" frente ao direito de arrependimento do CDC (art. 49) para compras feitas fora do estabelecimento | proposta | levantado na saída do beta | `.equipe/saida-do-beta.md` (privado) |
| D62 | Tabela `shedlock` com `timestamp` **sem fuso** (V3): com a hora do banco, o ShedLock grava `timezone('utc', now())`; numa coluna `timestamptz` o valor era convertido pelo fuso da sessão e a trava "acabava" 3 h antes (pego pelo teste A19) | aceita | teste A19, documentação do ShedLock | [V3](../../backend/src/main/resources/db/migration/V3__eventos.sql) |
| D63 | O listener do pagamento para `ReservaExpirada` (cancela a cobrança `PENDENTE`, E24) entra já na 4A: sem um listener, o outbox não grava a publicação e o A20 não teria o que provar. O teste do efeito na cobrança (A40) fica na 4B | aceita | spec da Fatia 4 (E11, E24) | `CancelaCobrancaDaReservaExpirada` |
| D64 | Os testes desligam o agendador de jobs (`app.jobs.habilitados=false`) e chamam o job à mão; só o `ExpiracaoJobIT` liga, num banco próprio, com uma reserva vencida criada por callback do Flyway antes da subida (prova o A21 de verdade) | aceita | Fatia 4A | `ExpiracaoJobIT` |
| D65 | O limite do visitante conta só reserva **criada**: tentativa recusada (409, 422) não gasta a cota. Em rede móvel muita gente divide o mesmo IP (CGNAT); contar tentativas travaria clientes legítimos | aceita | revisor da fatia (17/11/2026) | `ReservarHorario`, A13 |

## Pontos em que uma IA discordou da fonte oficial

| Ponto | O que a IA disse | O que a fonte diz | Valeu |
|---|---|---|---|
| Data da 4.1.2 | Gemini: 23/10/2026 | API do GitHub: 22/10/2026 17:04 UTC | a fonte |
| Simular Pix no Asaas | Gemini: `receiveInCash` | a doc manda **não** usar e oferece "Confirmar pagamento" (só sandbox) | a fonte |
| Simular Pix no Mercado Pago | Gemini: simulador genérico | `payer.first_name = "APRO"` na API de Orders | a fonte |
| Postgres free do Render | Gemini: 256 MB / 100 conexões | não confirmado; não usado (o banco é o Aiven) | descartado |
| Expiração preguiçosa | Gemini: tirar o `UPDATE` da inserção | mantido com lock ordenado e nova tentativa em `40P01` (ADR 0003) | decisão registrada |
| CPF | Gemini: guardar cifrado | mantido sem guardar no beta; crítica registrada no ADR 0009 | decisão com o Alicerce e o Kevin |
