# 🧭 Registro de decisões

| | |
|---|---|
| 🎯 **Para que serve** | a lista curta de tudo o que foi decidido, com fonte, data e onde está o detalhe |
| 👥 **Quem usa** | o Kevin, o Alicerce (arquitetura) e quem pega a próxima fatia |
| ⏰ **Quando usar** | antes de rediscutir algo já decidido; ao abrir um ADR novo |
| 🧰 **Como** | uma linha por decisão; o porquê completo está no ADR ou no documento citado |
| 🚫 **Não cobre** | regras da barbearia ainda em aberto: estão como HIPÓTESE (H1 a H18) no [dossiê](../contexto.md#4-hipóteses-em-aberto) |

Status: **proposta** = aguarda revisão; **aceita** = aprovada pelo Kevin.

| # | Data | Decisão | Status | Fonte | Detalhe |
|---|---|---|---|---|---|
| D1 | 13/11/2026 | Monólito modular com Spring Modulith, 8 módulos, 14 regras ArchUnit | proposta — revisão do Alicerce | arquitetura do Alicerce; comparação ponderada no ADR | [ADR 0001](../adr/0001-monolito-modular-com-spring-modulith.md), [arquitetura](../arquitetura/README.md) |
| D2 | 13/11/2026 | Módulos conversam por API pública e eventos; `event_publication` como outbox | proposta — revisão do Alicerce | documentação do Spring Modulith | [ADR 0002](../adr/0002-comunicacao-entre-modulos.md) |
| D3 | 13/11/2026 | Anti double booking no banco: `EXCLUDE USING gist` com `tstzrange '[)'` + `btree_gist`; expiração preguiçosa com lock ordenado | proposta — revisão do Alicerce | documentação do PostgreSQL; validado à mão no PostgreSQL 17 ([ERD](../banco/erd.md#validação-manual)) | [ADR 0003](../adr/0003-anti-double-booking-no-banco.md) |
| D4 | 13/11/2026 | Tempo em `timestamptz` + `Clock` injetado; dinheiro em centavos (`long`) | proposta — revisão do Alicerce | — | [ADR 0004](../adr/0004-tempo-e-dinheiro.md) |
| D5 | 13/11/2026 | Erros RFC 9457 com `code` e `traceId`; 404 para recurso alheio | proposta — revisão do Alicerce | RFC 9457; OWASP API Security Top 10 2023 | [ADR 0005](../adr/0005-erros-rfc-9457.md) |
| D6 | 13/11/2026 | JWT curto + refresh rotativo em cookie `HttpOnly` pelo BFF do Next.js | proposta — revisão do Alicerce | — | [ADR 0006](../adr/0006-sessao-jwt-refresh-cookie-bff.md) |
| D7 | 13/11/2026 | Gateway: **Asaas sandbox** (Mercado Pago como alternativa documentada) | proposta | `https://docs.asaas.com/docs/sandbox`, `https://docs.asaas.com/docs/sobre-os-webhooks`, `https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks` (lidos em 13/11/2026) | [ADR 0007](../adr/0007-gateway-de-pagamento-sandbox.md) |
| D8 | 13/11/2026 | Webhook: idempotência pelo `id` do evento + reconsulta da cobrança; conciliação no `GET` do guest | proposta | Asaas: entrega "pelo menos uma vez" e fila pausada após 15 falhas (`https://docs.asaas.com/docs/sobre-os-webhooks`) | [ADR 0007](../adr/0007-gateway-de-pagamento-sandbox.md) |
| D9 | 13/11/2026 | CEP: ViaCEP → BrasilAPI, cache Caffeine, nunca derruba o cadastro | proposta | — | [ADR 0008](../adr/0008-cep-com-cache-e-degradacao.md) |
| D10 | 13/11/2026 | CPF **não é guardado** (só em trânsito para o gateway); marketing por consentimento opcional | proposta (diverge do Alicerce) | LGPD art. 6º, III (necessidade) | [ADR 0009](../adr/0009-lgpd-marketing-cpf-e-retencao.md) |
| D11 | 13/11/2026 | Java 25 LTS + **Spring Boot 4.1.2** (GA de 22/10/2026); a 4.2.0 ainda é RC | proposta | `https://api.github.com/repos/spring-projects/spring-boot/releases` (consultado em 13/11/2026) | [ADR 0010](../adr/0010-versoes-da-plataforma.md) |
| D12 | 13/11/2026 | Pool Hikari de **5** conexões (Aiven free: `max_connections` 20, 3 a 6 reservadas) e nenhuma chamada HTTP dentro de transação | proposta | `https://aiven.io/docs/products/postgresql/reference/pg-connection-limits` | [verificação](../ia/verificacao-coordenador-2026-11-13.md) |
| D13 | 13/11/2026 | Render free hiberna em 15 min: job de expiração roda também na subida; correção não depende dele (R15, R16) | proposta | `https://render.com/docs/free` | [arquitetura](../arquitetura/README.md), [ataque do Gemini](../ia/ataque-arquitetura-gemini-2026-11-13.md) |
| D14 | 13/11/2026 | Vercel Hobby só para o beta de portfólio (uso não comercial); uso real com clientes pagando exige rever (RISC-03, H17) | proposta | `https://vercel.com/docs/limits/fair-use-guidelines`, `https://vercel.com/legal/terms` | [dossiê](../contexto.md#7-riscos-e-ameaças) |
| D15 | 13/11/2026 | Regras da barbearia como dados (`configuracao_agenda`, serviços, jornada), nunca fixas no código | proposta | prompt aprovado pelo Kevin | [DDL](../banco/V1__agenda.proposta.sql) |
| D16 | 13/11/2026 | Ordem do trabalho: backend S00 a S16, front F00 a F05, deploy por último | proposta | prompt aprovado pelo Kevin (seção 8) | [plano de fatias](plano-de-fatias.md) |

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
| D24 | 8 | ADR 0002 cita o pacote `<modulo>.eventos`, o `@NamedInterface("eventos")` e o `allowedDependencies` | proposta — revisão do Alicerce | arquitetura do Alicerce | [ADR 0002](../adr/0002-comunicacao-entre-modulos.md) |
| D25 | 9 | O BFF guarda o token de acesso em outro cookie `HttpOnly; Secure; SameSite=Strict; Path=/api` com `Max-Age` igual à validade do JWT (as funções da Vercel não têm estado) | proposta — revisão do Alicerce | — | [ADR 0006](../adr/0006-sessao-jwt-refresh-cookie-bff.md) |
| D26 | 10 | Sai a coluna `devolucao_pendente`; "a devolver" é o status (`PAGA_APOS_EXPIRACAO` ou `DEVOLUCAO_PENDENTE`), com índice parcial por status | proposta | revisão do Alicerce; DDL reaplicado sem erro no PostgreSQL 17.12 | [DDL](../banco/V1__agenda.proposta.sql), [modelo de domínio](../dominio/modelo-de-dominio.md) |

## Pontos em que uma IA discordou da fonte oficial

| Ponto | O que a IA disse | O que a fonte diz | Valeu |
|---|---|---|---|
| Data da 4.1.2 | Gemini: 23/10/2026 | API do GitHub: 22/10/2026 17:04 UTC | a fonte |
| Simular Pix no Asaas | Gemini: `receiveInCash` | a doc manda **não** usar e oferece "Confirmar pagamento" (só sandbox) | a fonte |
| Simular Pix no Mercado Pago | Gemini: simulador genérico | `payer.first_name = "APRO"` na API de Orders | a fonte |
| Postgres free do Render | Gemini: 256 MB / 100 conexões | não confirmado; não usado (o banco é o Aiven) | descartado |
| Expiração preguiçosa | Gemini: tirar o `UPDATE` da inserção | mantido com lock ordenado e nova tentativa em `40P01` (ADR 0003) | decisão registrada |
| CPF | Gemini: guardar cifrado | mantido sem guardar no beta; crítica registrada no ADR 0009 | decisão com o Alicerce e o Kevin |
