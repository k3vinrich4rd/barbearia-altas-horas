# 📑 Contrato da API · v1.0 (proposta)

| | |
|---|---|
| 🎯 **Para que serve** | o acordo entre backend, front (BFF) e testes: rotas, papéis, payloads, status e erros |
| 👥 **Área responsável** | backend (Kevin) · arquitetura: Alicerce · consumidor: front Next.js |
| ⏰ **Quando usar** | antes de criar ou mudar endpoint, DTO, status ou código de erro; na revisão de PR |
| 🧰 **Como** | este Markdown é o acordo humano; o rascunho de máquina é o [`openapi.yaml`](openapi.yaml) (OpenAPI 3.1). Depois do código, o OpenAPI passa a ser **gerado** pelo springdoc e comparado com este documento |
| 🚫 **Não cobre** | regras de status em detalhe ([domínio](../dominio/modelo-de-dominio.md)) · tabelas ([ERD](../banco/erd.md)) |

> Status: **v1.0 proposta, 13/11/2026**, aguardando revisão do Alicerce (arquitetura) e do Kevin. Valores marcados **HIPÓTESE** vêm da [seção 4 do dossiê](../contexto.md#4-hipóteses-em-aberto). Todos os nomes, e-mails, telefones e CPFs dos exemplos são **fictícios** (o CPF de exemplo é só válido pelos dígitos verificadores).

## Sumário
1. [Convenções](#1-convenções)
2. [Endpoints](#2-endpoints)
3. [Requisitos por endpoint](#3-requisitos-por-endpoint)
4. [Recursos e payloads](#4-recursos-e-payloads)
5. [Erros](#5-erros)
6. [Propostas (atrás de flag ou pós-beta)](#6-propostas-atrás-de-flag-ou-pós-beta)
7. [Histórico de versões](#7-histórico-de-versões)

---

## 1. Convenções

| Tema | Regra |
|---|---|
| Base URL | local `http://localhost:8080`; produção definida no deploy. **Sem prefixo `/api`** no backend (o `/api` é do BFF no Next) |
| Formato | JSON UTF-8; campos em `camelCase` em português; enums em MAIÚSCULAS |
| IDs | UUID em texto |
| Dinheiro | inteiro em **centavos** (`precoCentavos: 4000` = R$ 40,00), nunca decimal (ADR 0004) |
| Data e hora | ISO-8601 **com offset** (`2026-12-01T10:00:00-03:00`); datas puras `YYYY-MM-DD` no fuso da barbearia (`America/Sao_Paulo`) |
| Autenticação | `Authorization: Bearer <accessToken>` (JWT, **HIPÓTESE H15**: 15 min). Refresh opaco, de uso único, guardado pelo BFF em cookie `HttpOnly` (ADR 0006) |
| Papéis | `CLIENTE`, `BARBEIRO`, `ADMIN`; o guest não tem conta e usa `X-Guest-Token` só na própria reserva |
| Idempotência | `X-Idempotency-Key` (UUID v4) obrigatória em `POST /pagamentos/cobrancas` (R10). A chave é guardada como SHA-256 (é aleatória, sem dado pessoal). O corpo é comparado por **HMAC-SHA256** de `reservaId` + CPF normalizado com o segredo `IDEMPOTENCIA_HMAC_SEGREDO` (variável de ambiente), nunca por SHA-256 puro, que permitiria descobrir o CPF por força bruta. Mesma chave + mesmo corpo → mesma resposta; corpo diferente → `422` |
| Concorrência | recursos do painel com `version` no corpo; versão antiga → `409 CONFLITO_DE_VERSAO` |
| Paginação | `?pagina=0&tamanho=20` (máximo 50); resposta `{ "itens": [...], "pagina": 0, "tamanho": 20, "total": 3 }` |
| Campos desconhecidos | recusados com `400 CAMPO_DESCONHECIDO` (`fail-on-unknown-properties=true`, OWASP API3) |
| CORS | só as origens de `CORS_ORIGENS` (a do BFF); headers aceitos: `Authorization`, `Content-Type`, `X-Idempotency-Key`, `X-Guest-Token`, `X-Trace-Id`; expostos: `Retry-After`, `X-Trace-Id` |
| Erros | `application/problem+json`, RFC 9457, com `code`, `traceId` e `errors[]` (ADR 0005) |
| Limites | `429 LIMITE_EXCEDIDO` com `Retry-After` (**HIPÓTESE H14**) |
| Swagger | só no perfil `dev` (`/swagger-ui.html`); em `prod`, `404` |

## 2. Endpoints

Legenda de "Quem pode": 🌐 público · 👤 `CLIENTE` · 🧾 guest com `X-Guest-Token` · ✂️ `BARBEIRO` · 🛠 `ADMIN` · 🔁 gateway (autenticação do webhook).

| Método | Rota | O que faz | Quem pode | Sucesso | Erros 9457 (`code`) | Seção |
|---|---|---|---|---|---|---|
| `GET` | `/actuator/health` | saúde da API | 🌐 | `200` | — | — |
| `GET` | `/servicos` | serviços ativos com duração e preço | 🌐 | `200` | — | [4.1](#41-catálogo) |
| `GET` | `/barbeiros` | barbeiros ativos | 🌐 | `200` | — | [4.1](#41-catálogo) |
| `GET` | `/disponibilidade` | horários livres de um dia | 🌐 | `200` | `400 VALIDACAO_INVALIDA`, `404 RECURSO_NAO_ENCONTRADO`, `422 FORA_DO_HORIZONTE` | [4.2](#42-disponibilidade) |
| `GET` | `/cep/{cep}` | endereço pelo CEP (cache) | 🌐 | `200` | `400 CEP_INVALIDO`, `404 CEP_NAO_ENCONTRADO`, `503 CEP_INDISPONIVEL`, `429` | [4.3](#43-cep) |
| `POST` | `/auth/cadastro` | cria conta de cliente | 🌐 | `201` | `400 VALIDACAO_INVALIDA`, `409 EMAIL_JA_CADASTRADO`, `429` | [4.4](#44-autenticação-e-conta) |
| `POST` | `/auth/login` | login (cliente, barbeiro, admin) | 🌐 | `200` | `400`, `401 CREDENCIAIS_INVALIDAS`, `429 LIMITE_EXCEDIDO` | [4.4](#44-autenticação-e-conta) |
| `POST` | `/auth/refresh` | troca o refresh (rotativo) | 🌐 (com refresh) | `200` | `401 REFRESH_INVALIDO`, `401 REFRESH_REUTILIZADO` | [4.4](#44-autenticação-e-conta) |
| `POST` | `/auth/logout` | encerra a família do refresh | 🌐 (com refresh) | `204` | — | [4.4](#44-autenticação-e-conta) |
| `GET` | `/me` | meu perfil | 👤 ✂️ 🛠 | `200` | `401` | [4.4](#44-autenticação-e-conta) |
| `PUT` | `/me/senha` | troca a própria senha e encerra as outras sessões | 👤 ✂️ 🛠 | `204` | `400`, `401 CREDENCIAIS_INVALIDAS` (senha atual errada), `401 NAO_AUTENTICADO` | [4.4](#44-autenticação-e-conta) |
| `GET` | `/me/dados` | exportar meus dados (LGPD) | 👤 ✂️ 🛠 | `200` | `401`, `429` (5 por hora por conta, H22) | [4.9](#49-privacidade) |
| `DELETE` | `/me` | excluir minha conta (LGPD) | 👤 (só cliente) | `204` | `401`, `403 ACESSO_NEGADO` (barbeiro e admin não se excluem), `409 EXCLUSAO_BLOQUEADA_POR_RESERVA` | [4.9](#49-privacidade) |
| `GET` | `/me/consentimentos` | meus consentimentos | 👤 | `200` | `401` | [4.9](#49-privacidade) |
| `PUT` | `/me/consentimentos/marketing` | aceita ou revoga marketing | 👤 | `200` | `400`, `401` | [4.9](#49-privacidade) |
| `GET` | `/politica-privacidade` | política de privacidade e texto da caixa de marketing, com a versão vigente | 🌐 | `200` | — | [4.9](#49-privacidade) |
| `GET` | `/politica-pagamento` | texto e versão da política (sem cancelamento, remarcação ou estorno) | 🌐 | `200` | — | [4.7](#47-pagamento-100-antecipado) |
| `POST` | `/agendamentos` | reserva como cliente (`AGUARDANDO_PAGAMENTO`, 15 min) | 👤 | `201` | `400`, `401`, `403`, `404`, `409 HORARIO_INDISPONIVEL`, `409 CONFLITO_CONCORRENTE`, `422 FORA_DO_EXPEDIENTE`, `422 ANTECEDENCIA_INSUFICIENTE`, `422 SERVICO_INATIVO` | [4.5](#45-agendamentos-do-cliente) |
| `GET` | `/me/agendamentos` | meus agendamentos (paginado) | 👤 | `200` | `401` | [4.5](#45-agendamentos-do-cliente) |
| `GET` | `/agendamentos/{id}` | um agendamento meu | 👤 | `200` | `401`, `404` (inclusive de outro dono) | [4.5](#45-agendamentos-do-cliente) |
| `GET` | `/alcance` | cidades e bairros atendidos, só nomes e sem contagens, últimos 12 meses (**novo na 1.5.0**, 6C) | 🌐 (30/min por IP) | `200` | `429` | [4.6](#46-guest) |
| `POST` | `/agendamentos/guest` | reserva sem conta (`AGUARDANDO_PAGAMENTO`); `cep` opcional (**6C**) | 🌐 (limite por IP) | `201` | `400` (`CEP_INVALIDO` se o `cep` tem formato errado), `404`, `409 HORARIO_INDISPONIVEL`, `409 CONFLITO_CONCORRENTE`, `422`, `429` | [4.6](#46-guest) |
| `GET` | `/agendamentos/guest/{id}` | status da reserva do guest | 🧾 | `200` | `404` (token errado também) | [4.6](#46-guest) |
| `POST` | `/pagamentos/cobrancas` | cobra 100% por Pix ou cartão de crédito (pede CPF e aceite da política) | 👤 🧾 | `201` (`200` na repetição) | `400 CPF_INVALIDO`, `400 IDEMPOTENCY_KEY_AUSENTE`, `401`, `404`, `422 ACEITE_POLITICA_OBRIGATORIO`, `422 RESERVA_EXPIRADA`, `422 IDEMPOTENCIA_CONFLITO`, `429`, `503 GATEWAY_INDISPONIVEL` | [4.7](#47-pagamento-100-antecipado) |
| `POST` | `/pagamentos/webhook` | aviso do gateway | 🔁 | `200` | `401 WEBHOOK_NAO_AUTENTICADO` | [4.8](#48-webhook) |
| `GET` | `/admin/agenda` | agenda do dia ou da semana, com nome e WhatsApp de quem vem e o status da cobrança (`AgendamentoAdmin`) | 🛠 ✂️ (só a própria, **H9**) | `200` | `400`, `401`, `403`, `404` (barbeiro inexistente, alheio ou sem vínculo) | [Painel](../admin/painel-admin.md) |
| `PATCH` | `/admin/agendamentos/{id}/status` | conclui, marca falta (valor retido) ou cancela pela barbearia (só ADMIN cancela) | 🛠 ✂️ | `200` | `400`, `401`, `403`, `404`, `409 CONFLITO_DE_VERSAO`, `422 TRANSICAO_INVALIDA`, `422 MOTIVO_OBRIGATORIO` | [Painel](../admin/painel-admin.md) |
| `GET` `POST` | `/admin/servicos` | lista (inclusive inativos) e cria | 🛠 | `200` / `201` | `400`, `401`, `403`, `409 NOME_JA_EXISTE` | [Painel](../admin/painel-admin.md) |
| `PUT` | `/admin/servicos/{id}` | altera nome, duração, preço, ativo (preço a partir de 1 centavo) | 🛠 | `200` | `400`, `401`, `403`, `404`, `409 NOME_JA_EXISTE`, `409 CONFLITO_DE_VERSAO` | [Painel](../admin/painel-admin.md) |
| `GET` `POST` | `/admin/barbeiros` | lista e cria | 🛠 | `200` / `201` | `400`, `401`, `403`, `409 USUARIO_JA_VINCULADO` | [Painel](../admin/painel-admin.md) |
| `PUT` | `/admin/barbeiros/{id}` | altera nome, ativo, login vinculado | 🛠 | `200` | `400`, `401`, `403`, `404`, `409 USUARIO_JA_VINCULADO`, `409 CONFLITO_DE_VERSAO` | [Painel](../admin/painel-admin.md) |
| `PUT` | `/admin/barbeiros/{id}/jornada` | substitui a jornada semanal | 🛠 | `200` | `400`, `401`, `403`, `404`, `422 JORNADA_SOBREPOSTA` | [Painel](../admin/painel-admin.md) |
| `GET` | `/admin/barbeiros/{id}/folgas` | folgas de hoje em diante (**novo na 1.4.0**) | 🛠 | `200` | `401`, `403`, `404` | [Painel](../admin/painel-admin.md) |
| `POST` | `/admin/barbeiros/{id}/folgas` | cria folga | 🛠 | `201` | `400`, `401`, `403`, `404`, `422 FOLGA_COM_RESERVAS` | [Painel](../admin/painel-admin.md) |
| `DELETE` | `/admin/barbeiros/{id}/folgas/{folgaId}` | remove folga | 🛠 | `204` | `401`, `403`, `404` | [Painel](../admin/painel-admin.md) |
| `GET` | `/admin/pagamentos` | pendências (sem `status`) ou cobranças por status, sem Pix, cartão, CPF nem e-mail (`CobrancaAdmin`) | 🛠 | `200` | `400`, `401`, `403` | [Painel](../admin/painel-admin.md) |
| `POST` | `/admin/pagamentos/{id}/devolucao` | registra devolução manual feita (responde `Cobranca` com `devolvidaEm`) | 🛠 | `200` | `401`, `403`, `404`, `422 TRANSICAO_INVALIDA` | [Painel](../admin/painel-admin.md) |
| `GET` | `/admin/pagamentos/eventos` | eventos do gateway em `REVISAO_MANUAL`, sem payload (**novo na 1.4.0**) | 🛠 | `200` | `400`, `401`, `403` | [Painel](../admin/painel-admin.md) |
| `POST` | `/admin/pagamentos/eventos/{id}/revisao` | marca o evento como revisado, sem reprocessar (**novo na 1.4.0**) | 🛠 | `200` | `401`, `403`, `404`, `422 TRANSICAO_INVALIDA` | [Painel](../admin/painel-admin.md) |
| `GET` `PUT` | `/admin/configuracao` | regras configuráveis da agenda (`fuso` só leitura) | 🛠 | `200` | `400`, `401`, `403`, `409 CONFLITO_DE_VERSAO` | [Painel](../admin/painel-admin.md) |
| `GET` | `/admin/auditoria` | ações do painel (paginado, sem IP) | 🛠 | `200` | `400`, `401`, `403` | [Painel](../admin/painel-admin.md) |
| `GET` | `/admin/alcance` | de onde vêm os atendimentos: cidades e bairros com contagem e percentual, k ≥ 5 (**novo na 1.5.0**, 6C). ADMIN vê tudo (filtro `barbeiroId`); BARBEIRO só os próprios | 🛠 | `200` | `400`, `401`, `403`, `404` | [4.6](#46-guest) |

## 3. Requisitos por endpoint

| Rota | Dono do recurso (BOLA) | Campos que o cliente escreve (BOPLA) | Paginação | Limite de taxa (H14) | Idempotência | p95 esperado (meta, sem medição) |
|---|---|---|---|---|---|---|
| `GET /disponibilidade` | — | — | um dia por chamada | — | — | < 300 ms |
| `POST /auth/login` | — | `email`, `senha` | — | 5 erros/15 min por e-mail + IP | — | — |
| `POST /auth/cadastro` | — | `nome`, `email`, `whatsapp`, `senha`, `cep?`, `aceitaMarketing` | — | 5/h por IP | — | — |
| `POST /agendamentos` | o próprio cliente (do token) | `servicoId`, `barbeiroId`, `inicio` | — | — | — (o horário já é único) | < 400 ms |
| `GET /agendamentos/{id}` | `cliente_id = sub` senão `404` | — | — | — | — | — |
| `POST /agendamentos/guest` | — | `servicoId`, `barbeiroId`, `inicio`, `nome`, `email`, `whatsapp`, `cep?` | — | 3/h por IP | — | < 400 ms |
| `GET /alcance` | — | — | — | 30/min por IP | — | — |
| `GET /admin/alcance` | BARBEIRO só os próprios atendimentos (o `barbeiroId` da query vira o dele) | — | — | — | — | — |
| `GET /agendamentos/guest/{id}` | hash do `X-Guest-Token` = `token_hash` senão `404` | — | — | — | — | — |
| `POST /pagamentos/cobrancas` | `X-Guest-Token` da reserva | `reservaId`, `cpf` | — | 5/h por IP | **obrigatória** | depende do gateway |
| `POST /pagamentos/webhook` | autenticação do provedor | corpo do provedor (só os campos usados são lidos) | — | — | id do evento único | < 300 ms (só grava) |
| `/admin/**` | ✂️ barbeiro só o próprio `barbeiroId` (H9) | DTO por operação | máx. 50 | — | `version` | — |

## 4. Recursos e payloads

### 4.1 Catálogo

`GET /servicos` → `200`
```json
[
  { "id": "6f1c2a8e-0000-4000-8000-000000000005", "nome": "Acabamento (Pezinho) / Sobrancelha", "duracaoMin": 30, "precoCentavos": 2000, "precoFormatado": "R$ 20,00" },
  { "id": "6f1c2a8e-0000-4000-8000-000000000002", "nome": "Corte Degradê / Fade / Navalhado", "duracaoMin": 30, "precoCentavos": 5500, "precoFormatado": "R$ 55,00" }
]
```
Só os **ativos**, em ordem de nome (sem diferença de maiúsculas). `GET /barbeiros` → `200`
```json
[ { "id": "b0000000-0000-4000-8000-000000000001", "nomeExibicao": "Carlos Eduardo Cândido" } ]
```
As três rotas de catálogo (`/servicos`, `/barbeiros`, `/politica-pagamento`) respondem com `Cache-Control: max-age=60, public`.

### 4.2 Disponibilidade

`GET /disponibilidade?servicoId=6f1c2a8e-0000-4000-8000-000000000001&data=2026-12-01&barbeiroId=b0000000-0000-4000-8000-000000000001` (`barbeiroId` opcional: sem ele, todos os ativos)

`200`
```json
{
  "data": "2026-12-01",
  "fuso": "America/Sao_Paulo",
  "servicoId": "6f1c2a8e-0000-4000-8000-000000000001",
  "duracaoMin": 30,
  "barbeiros": [
    {
      "barbeiroId": "b0000000-0000-4000-8000-000000000001",
      "nomeExibicao": "Carlos Eduardo Cândido",
      "horarios": ["2026-12-01T07:00:00-03:00", "2026-12-01T07:30:00-03:00", "2026-12-01T08:00:00-03:00"]
    }
  ]
}
```
Regras: jornada − folgas − reservas que ocupam horário (ignorando `AGUARDANDO_PAGAMENTO` vencidas, R15); passos de `granularidadeMin` (H11); só a partir de `agora + antecedenciaMinimaMin` (H5); até `horizonteDias` (H12); `data` **antes de hoje** ou além do horizonte → `422 FORA_DO_HORIZONTE`. Um início só é oferecido se o serviço **inteiro** couber no intervalo da jornada (o de 60 min não aparece às 11h30 nem às 19h30). Serviço inexistente → `404`; inativo → `422 SERVICO_INATIVO`; `barbeiroId` inexistente ou inativo → `404`; `data` ou `servicoId` ausente ou malformado → `400 VALIDACAO_INVALIDA` com o campo em `errors[]`. Horários em ordem crescente, sempre com segundos e offset; barbeiro sem horário vem com `horarios: []`. Resposta com `Cache-Control: no-store`.

### 4.3 CEP

`GET /cep/01001000` → `200`
```json
{ "cep": "01001000", "logradouro": "Praça da Sé", "bairro": "Sé", "cidade": "São Paulo", "uf": "SP", "fonte": "VIACEP" }
```
Aceita `01001-000` ou `01001000`; outro formato → `400 CEP_INVALIDO` sem chamada externa. Os dois provedores fora → `503 CEP_INDISPONIVEL` (o cadastro **não** usa esta rota para decidir: R31). `fonte` é `VIACEP`, `BRASILAPI` ou `CACHE` (encontrado fica 7 dias em memória; inexistente, 1 hora; falha de provedor nunca fica). Limite de 30 consultas por minuto por IP (H21): passou, `429` com `Retry-After`.

### 4.4 Autenticação e conta

`POST /auth/cadastro`
```json
{ "nome": "Cliente Teste A", "email": "cliente.a@exemplo.test", "whatsapp": "(11) 90000-0001", "senha": "Senha-de-teste-123", "cep": "01001-000", "aceitaMarketing": false }
```
`201` + `Location: /me`
```json
{ "id": "c0000000-0000-4000-8000-00000000000a", "nome": "Cliente Teste A", "email": "cliente.a@exemplo.test", "papeis": ["CLIENTE"], "endereco": { "cep": "01001000", "bairro": "Sé", "cidade": "São Paulo", "uf": "SP", "completo": true } }
```
Validações: `nome` 2–120; `email` válido e único (R24); `whatsapp` 10 a 13 dígitos depois de sanitizar; `senha` de 10 a 72 **bytes** em UTF-8 (o BCrypt corta em 72 bytes, e letra acentuada ocupa 2); `cep` opcional, 8 dígitos; `aceitaMarketing` opcional, padrão `false` (R28). CEP não resolvido → `endereco.completo: false` e bairro/cidade nulos (R31). Resposta **nunca** tem `senha` nem `senhaHash`.

`POST /auth/login`
```json
{ "email": "cliente.a@exemplo.test", "senha": "Senha-de-teste-123" }
```
`200`
```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "tipo": "Bearer", "expiraEmSegundos": 900, "refreshToken": "r1_9Yc...", "refreshExpiraEm": "2026-12-08T10:00:00-03:00", "usuario": { "id": "c0000000-0000-4000-8000-00000000000a", "nome": "Cliente Teste A", "papeis": ["CLIENTE"] } }
```
> O `refreshToken` vem no corpo **só para o BFF**: o Next guarda em cookie `HttpOnly` e nunca entrega ao JavaScript da página (ADR 0006). Mesmo erro (`401 CREDENCIAIS_INVALIDAS`) para e-mail inexistente e senha errada.

`POST /auth/refresh` `{ "refreshToken": "r1_9Yc..." }` → `200` com novo par. Refresh já usado → `401 REFRESH_REUTILIZADO` e a família inteira é revogada (R27).

`POST /auth/logout` `{ "refreshToken": "r1_9Yc..." }` → `204`.

`GET /me` → `200` com o mesmo formato do cadastro, mais `deveTrocarSenha`.

`PUT /me/senha` `{ "senhaAtual": "...", "novaSenha": "..." }` → `204`. A versão da credencial sobe e **todos** os refresh do usuário são revogados; senha atual errada → `401 CREDENCIAIS_INVALIDAS`.

**Senha inicial do admin.** O primeiro admin nasce com `deveTrocarSenha: true` (a senha veio de variável de ambiente). Enquanto for verdadeiro, `/admin/**` responde `403 TROCA_DE_SENHA_OBRIGATORIA`.

**Limites (R26).** Login: 5 erros em 15 min por e-mail + IP, e 20 por IP; cadastro: 5 por hora por IP. Passou → `429 LIMITE_EXCEDIDO` com `Retry-After` em segundos. Toda resposta `401` de rota protegida leva `WWW-Authenticate: Bearer`, e rota não declarada responde `401` para quem não está logado (negar por padrão).

### 4.5 Agendamentos do cliente

`POST /agendamentos` (👤)
```json
{ "servicoId": "6f1c2a8e-0000-4000-8000-000000000001", "barbeiroId": "b0000000-0000-4000-8000-000000000001", "inicio": "2026-12-01T10:00:00-03:00" }
```
`201` + `Location: /agendamentos/{id}`
```json
{
  "id": "a0000000-0000-4000-8000-000000000101",
  "status": "AGUARDANDO_PAGAMENTO",
  "origem": "CLIENTE",
  "servico": { "id": "6f1c2a8e-0000-4000-8000-000000000001", "nome": "Corte Degradê / Fade / Navalhado", "duracaoMin": 30 },
  "barbeiro": { "id": "b0000000-0000-4000-8000-000000000001", "nomeExibicao": "Carlos Eduardo Cândido" },
  "inicio": "2026-12-01T10:00:00-03:00",
  "fim": "2026-12-01T10:30:00-03:00",
  "precoCentavos": 5500,
  "expiraEm": "2026-11-30T15:15:00-03:00"
}
```
Erros típicos: horário já ocupado (inclusive por corrida) → `409 HORARIO_INDISPONIVEL`; deadlock que persistiu depois do retry interno → `409 CONFLITO_CONCORRENTE` (o horário pode estar livre: o front **não** o esconde e pode repetir a requisição **uma vez**; vale também para o guest, ADR 0003); fora da jornada ou em folga → `422 FORA_DO_EXPEDIENTE`; antes de `agora + antecedência` → `422 ANTECEDENCIA_INSUFICIENTE`; `inicio` fora da granularidade → `400 VALIDACAO_INVALIDA`. Mandar `status` ou `precoCentavos` no corpo → `400 CAMPO_DESCONHECIDO`. O horário fica travado por 15 minutos até o pagamento (R6, R7); o próximo passo é a [cobrança](#47-pagamento-100-antecipado).

`GET /me/agendamentos?pagina=0&tamanho=20` → `200` página do formato acima, mais recentes primeiro.

> **Sem cancelamento e sem remarcação pelo cliente (R18).** Não existe rota para isso, de propósito: depois do pagamento de 100%, o horário é do cliente e não há estorno por desistência ou falta. Só a barbearia cancela, pelo painel, com motivo (R22).

### 4.6 Guest

`POST /agendamentos/guest` (🌐, limite por IP)
```json
{ "servicoId": "6f1c2a8e-0000-4000-8000-000000000001", "barbeiroId": "b0000000-0000-4000-8000-000000000001", "inicio": "2026-12-01T11:00:00-03:00", "nome": "Visitante Teste", "email": "visitante@exemplo.test", "whatsapp": "11900000002" }
```
`201` + `Location: /agendamentos/guest/{id}`
```json
{
  "id": "a0000000-0000-4000-8000-000000000102",
  "status": "AGUARDANDO_PAGAMENTO",
  "origem": "GUEST",
  "inicio": "2026-12-01T11:00:00-03:00",
  "fim": "2026-12-01T11:30:00-03:00",
  "precoCentavos": 5500,
  "expiraEm": "2026-11-30T15:15:00-03:00",
  "tokenGuest": "g1_Qm9..."
}
```
> O `tokenGuest` aparece **uma vez** (o banco guarda só o hash). É o que permite ao guest consultar a reserva e pagar: sem conta, sem expor a reserva a quem só tem o id. O CPF **não** é pedido aqui (R9).

**Alcance por região (Fatia 6C).** O corpo aceita `cep` **opcional** (`01001000` ou `01001-000`). Formato errado: `400 CEP_INVALIDO` e nada é criado. CEP inexistente ou provedor fora **nunca** impedem a reserva (R31): ela nasce sem região. A resposta do `POST` não muda (não devolve CEP nem região).

`GET /alcance` (🌐, 30 por minuto por IP, `429` com `Retry-After`) → `200`, sem contagens, últimos 12 meses, cache de 1 hora no servidor:
```json
{ "atualizadoEm": "2026-11-17T08:00:00-03:00", "totalCidades": 1, "totalBairros": 1,
  "regioes": [ { "cidade": "Osasco", "uf": "SP", "bairros": ["Centro"] } ] }
```
`GET /admin/alcance?de=2026-11-01&ate=2026-11-17&barbeiroId=...` (🛠, nunca em cache) → `200`. `de` e `ate` são dias civis (padrão: os últimos 30 dias); `barbeiroId` só vale para ADMIN (o BARBEIRO vê sempre os próprios). Período invertido ou com mais de 366 dias: `400 VALIDACAO_INVALIDA`; barbeiro inexistente: `404`. Só entram reservas `CONFIRMADO` e `CONCLUIDO`; o `percentual` é sobre o `total` do período. Bairro com menos de 5 atendimentos vira "Outros bairros" e cidade com menos de 5 vira "Outras cidades" (`uf` nula):
```json
{ "periodo": { "de": "2026-11-01", "ate": "2026-11-17" }, "total": 9, "semRegiao": 1,
  "cidades": [
    { "cidade": "Osasco", "uf": "SP", "atendimentos": 6, "percentual": 66.7, "abaixoDoLimite": false,
      "bairros": [ { "bairro": "Centro", "atendimentos": 6, "percentual": 66.7, "abaixoDoLimite": false } ] },
    { "cidade": "Outras cidades", "uf": null, "atendimentos": null, "percentual": null, "abaixoDoLimite": true, "bairros": [] } ] }
```

`GET /agendamentos/guest/{id}` com `X-Guest-Token: g1_Qm9...` → `200` com `status` atual (o front consulta a cada poucos segundos depois do Pix ou do checkout do cartão). Se a cobrança estiver `PENDENTE`, a API reconsulta o gateway (no máximo a cada 10 s) para confirmar mesmo que o webhook atrase (ADR 0007). Token errado ou ausente → `404`.

### 4.7 Pagamento 100% antecipado

`GET /politica-pagamento` (🌐) → `200 { "versao": 1, "texto": "Após o pagamento antecipado de 100%, não há cancelamento, remarcação ou estorno em caso de desistência ou falta." }`. O front mostra esse texto com um checkbox **desmarcado**; o botão de pagar só libera com ele marcado.

`POST /pagamentos/cobrancas` com `Authorization: Bearer` (cliente) **ou** `X-Guest-Token` (guest) e `X-Idempotency-Key: 5d0b3f8e-7c1a-4b7e-9f63-2a0d6c1e9b10`
```json
{ "reservaId": "a0000000-0000-4000-8000-000000000102", "metodo": "PIX", "cpf": "123.456.789-09", "aceitePolitica": true, "versaoPolitica": 1 }
```
`201`
```json
{
  "id": "d0000000-0000-4000-8000-000000000201",
  "reservaId": "a0000000-0000-4000-8000-000000000102",
  "status": "PENDENTE",
  "metodo": "PIX",
  "valorCentavos": 5500,
  "pix": { "copiaECola": "00020126580014br.gov.bcb.pix...", "qrCodeBase64": "iVBORw0KGgo...", "expiraEm": "2026-11-30T15:15:00-03:00" },
  "cartao": null,
  "cpfMascarado": "***.***.***-09"
}
```
Com `"metodo": "CARTAO_CREDITO"`, a resposta traz `"pix": null` e `"cartao": { "urlCheckout": "https://sandbox.asaas.com/i/...", "expiraEm": "..." }`: o front abre o checkout hospedado do gateway e **o número do cartão nunca passa pela API** (escopo PCI DSS reduzido, ADR 0007). Nos dois casos o valor é **100% do `precoCentavos`** congelado na reserva (R8). Sem `aceitePolitica: true` → `422 ACEITE_POLITICA_OBRIGATORIO`; a API grava a versão e a data do aceite na reserva.

O `cpfMascarado` aparece **só nesta resposta `201`**: ele é montado a partir do CPF que acabou de chegar no corpo, e o CPF não é guardado. Repetição com a mesma chave e o mesmo corpo → `200` com a mesma cobrança **sem** `cpfMascarado`, sem chamar o gateway de novo. A listagem do painel (`GET /admin/pagamentos`) também não traz CPF. Mesma chave e corpo diferente → `422 IDEMPOTENCIA_CONFLITO`. Reserva vencida → `422 RESERVA_EXPIRADA`. Gateway fora ou lento → `503 GATEWAY_INDISPONIVEL` (a reserva segue até expirar). O CPF é validado pelos dígitos, repassado ao gateway e **não é gravado** (ADR 0009). 

### 4.8 Webhook

`POST /pagamentos/webhook` (🔁 Asaas, header `asaas-access-token`, ADR 0007). Corpo do provedor (campos que a API lê):
```json
{ "id": "evt_teste_0001", "event": "PAYMENT_RECEIVED", "payment": { "id": "pay_teste_0001", "billingType": "PIX", "status": "RECEIVED" } }
```
Respostas: `200` sem corpo (primeira vez **e** repetição, R12); `401 WEBHOOK_NAO_AUTENTICADO` com token errado ou ausente, sem gravar nada (R11). O corpo é tratado como **sinal de mudança**: a API reconsulta a cobrança no gateway antes de marcar `PAGA` (OWASP API10). Eventos de tipos que a API não usa → `200` e registro como `IGNORADO`.

**Pagamento tardio (R14).** Se a aprovação chega depois dos 15 minutos: com o horário ainda livre, a reserva volta de `EXPIRADO` para `CONFIRMADO` (passa pela mesma constraint do banco); com o horário já ocupado por outro cliente, a cobrança fica `PAGA_APOS_EXPIRACAO` e aparece no painel como **pendência administrativa** para tratamento manual.

### 4.9 Privacidade

`GET /me/dados` → `200` (exportação), com `Cache-Control: no-store`. Traz a conta, o **histórico inteiro** de consentimentos (do mais antigo para o mais novo) e só as reservas em que a conta é o cliente. Nunca sai hash de senha, refresh token, token de visitante, IP nem dado de outra pessoa. Limite de 5 por hora por conta (H22): passou, `429` com `Retry-After`.
```json
{ "geradoEm": "2026-12-01T10:00:00-03:00", "conta": { "nome": "Cliente Teste A", "email": "cliente.a@exemplo.test", "whatsapp": "11900000001", "endereco": { "cep": "01001000", "bairro": "Sé", "cidade": "São Paulo", "uf": "SP" } }, "consentimentos": [ { "finalidade": "MARKETING_CEP", "aceito": false, "versaoTexto": "1.0", "registradoEm": "2026-11-20T09:00:00-03:00" } ], "agendamentos": [ { "id": "a0000000-0000-4000-8000-000000000101", "inicio": "2026-12-01T10:00:00-03:00", "fim": "2026-12-01T10:30:00-03:00", "status": "CONFIRMADO", "servico": "Corte Clássico", "precoCentavos": 4500, "motivoCancelamento": null } ] }
```
`DELETE /me` → `204`; só `CLIENTE` (barbeiro e admin: `403 ACESSO_NEGADO`) e sem pedir a senha no beta. Numa única transação: anonimiza a conta (o e-mail fica livre para novo cadastro), apaga os refresh tokens, cancela as reservas futuras **não pagas** e grava a revogação final do consentimento (ADR 0009). O login e o token antigo dão `401` em seguida. Com reserva futura **paga** (`CONFIRMADO`) ou cobrança aguardando devolução a exclusão é recusada com `409 EXCLUSAO_BLOQUEADA_POR_RESERVA` e nada muda: o titular combina o cancelamento com a barbearia (R14, R18) e exclui depois.

`GET /me/consentimentos` → `200` com o **estado atual** de cada finalidade (a linha mais recente); conta sem nenhum registro (o admin inicial) recebe `[]`.

`PUT /me/consentimentos/marketing` `{ "aceito": true, "versaoTexto": "1.0" }` → `200` com o consentimento registrado (cada mudança é uma linha nova, R28). `versaoTexto` precisa ser a vigente (a de `GET /politica-privacidade`): outra versão é `400 VALIDACAO_INVALIDA` com `errors[].field = versaoTexto`. Pedir o estado que já vale, na mesma versão, devolve a linha existente e não grava outra. Revogar não apaga CEP, bairro nem cidade da conta.

`GET /politica-privacidade` (pública) → `200` `{ "versao": "1.0", "textoConsentimento": "...", "politica": "...", "atualizadaEm": "2026-11-17T00:00:00-03:00" }`. A `versao` é a mesma que o `PUT` exige; texto novo é versão nova, e a anterior continua guardada. A política da 1.0 é um **rascunho técnico, não é parecer jurídico**.

### 4.10 Regras de negócio citadas

As regras R1 a R32 ficam no [dossiê, seção 3](../contexto.md#3-regras-de-negócio), para não duplicar. Testes e PRs citam o número.

## 5. Erros

Formato (ADR 0005):
```json
{
  "type": "https://github.com/k3vinrich4rd/barbearia-altas-horas/blob/main/docs/contrato/contrato-api.md#validacao_invalida",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos estão inválidos.",
  "instance": "/auth/cadastro",
  "code": "VALIDACAO_INVALIDA",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [ { "field": "email", "message": "deve ser um e-mail válido" } ]
}
```
O cliente decide pelo `status` e pelo `code`, nunca pelo texto. O `type` aponta para a linha do código abaixo.

| Status | `code` | Quando | `title` |
|---|---|---|---|
| 400 | <a id="validacao_invalida"></a>`VALIDACAO_INVALIDA` | Bean Validation falhou; `errors[]` preenchido | Dados inválidos |
| 400 | <a id="json_malformado"></a>`JSON_MALFORMADO` | corpo não é JSON válido | Corpo inválido |
| 400 | <a id="campo_desconhecido"></a>`CAMPO_DESCONHECIDO` | campo que a operação não aceita (BOPLA) | Campo não permitido |
| 400 | <a id="cep_invalido"></a>`CEP_INVALIDO` | CEP sem 8 dígitos | CEP inválido |
| 400 | <a id="cpf_invalido"></a>`CPF_INVALIDO` | dígitos verificadores errados | CPF inválido |
| 400 | <a id="idempotency_key_ausente"></a>`IDEMPOTENCY_KEY_AUSENTE` | `X-Idempotency-Key` ausente ou não é UUID | Chave de idempotência obrigatória |
| 401 | <a id="nao_autenticado"></a>`NAO_AUTENTICADO` | sem token ou token inválido/vencido | Autenticação necessária |
| 401 | <a id="credenciais_invalidas"></a>`CREDENCIAIS_INVALIDAS` | e-mail ou senha errados | E-mail ou senha inválidos |
| 401 | <a id="refresh_invalido"></a>`REFRESH_INVALIDO` | refresh inexistente, vencido ou revogado | Sessão expirada |
| 401 | <a id="refresh_reutilizado"></a>`REFRESH_REUTILIZADO` | refresh já usado (família revogada) | Sessão encerrada por segurança |
| 401 | <a id="webhook_nao_autenticado"></a>`WEBHOOK_NAO_AUTENTICADO` | token/assinatura do provedor ausente ou errado | Notificação não autenticada |
| 403 | <a id="acesso_negado"></a>`ACESSO_NEGADO` | logado, mas sem o papel | Acesso negado |
| 403 | <a id="troca_de_senha_obrigatoria"></a>`TROCA_DE_SENHA_OBRIGATORIA` | admin ainda com a senha inicial chamando `/admin/**` | Troca de senha obrigatória |
| 404 | <a id="recurso_nao_encontrado"></a>`RECURSO_NAO_ENCONTRADO` | não existe **ou** não pertence a quem pede | Não encontrado |
| 404 | <a id="cep_nao_encontrado"></a>`CEP_NAO_ENCONTRADO` | CEP válido sem endereço nos provedores | CEP não encontrado |
| 404 | <a id="funcionalidade_desligada"></a>`FUNCIONALIDADE_DESLIGADA` | rota atrás de flag desligada | Não encontrado |
| 405 | <a id="metodo_nao_permitido"></a>`METODO_NAO_PERMITIDO` | método HTTP não aceito na rota | Método não permitido |
| 415 | <a id="midia_nao_suportada"></a>`MIDIA_NAO_SUPORTADA` | corpo sem `Content-Type: application/json` | Tipo de conteúdo não suportado |
| 409 | <a id="email_ja_cadastrado"></a>`EMAIL_JA_CADASTRADO` | e-mail já em uso | E-mail já cadastrado |
| 409 | <a id="horario_indisponivel"></a>`HORARIO_INDISPONIVEL` | violação da constraint `reserva_sem_sobreposicao` | Horário indisponível |
| 409 | <a id="conflito_concorrente"></a>`CONFLITO_CONCORRENTE` | deadlock (`40P01`) que persistiu depois do retry interno da transação; `detail`: "Muitas reservas ao mesmo tempo. Tente de novo." O front pode repetir **uma vez** e não esconde o horário | Conflito momentâneo |
| 409 | <a id="conflito_de_versao"></a>`CONFLITO_DE_VERSAO` | `version` desatualizada | Registro alterado por outra pessoa |
| 409 | <a id="nome_ja_existe"></a>`NOME_JA_EXISTE` | serviço com o mesmo nome | Nome já existe |
| 409 | <a id="usuario_ja_vinculado"></a>`USUARIO_JA_VINCULADO` | conta já ligada a outro barbeiro (**novo na 1.4.0**, usado pelo cadastro de barbeiros da 5B) | Conta já vinculada |
| 409 | <a id="exclusao_bloqueada_por_reserva"></a>`EXCLUSAO_BLOQUEADA_POR_RESERVA` | `DELETE /me` com reserva futura paga (**novo na 1.5.0**, Fatia 6B): combine o cancelamento com a barbearia | Exclusão bloqueada por reserva |
| 422 | <a id="fora_do_expediente"></a>`FORA_DO_EXPEDIENTE` | fora da jornada ou em folga | Fora do horário de atendimento |
| 422 | <a id="antecedencia_insuficiente"></a>`ANTECEDENCIA_INSUFICIENTE` | antes da antecedência mínima (H5) | Antecedência mínima não atendida |
| 422 | <a id="fora_do_horizonte"></a>`FORA_DO_HORIZONTE` | data além do horizonte (H12) | Data além do permitido |
| 422 | <a id="aceite_politica_obrigatorio"></a>`ACEITE_POLITICA_OBRIGATORIO` | cobrança sem `aceitePolitica: true` | Aceite da política obrigatório |
| 422 | <a id="transicao_invalida"></a>`TRANSICAO_INVALIDA` | transição fora da máquina de estados | Mudança de status não permitida |
| 422 | <a id="motivo_obrigatorio"></a>`MOTIVO_OBRIGATORIO` | cancelamento pela barbearia sem motivo | Motivo obrigatório |
| 422 | <a id="reserva_expirada"></a>`RESERVA_EXPIRADA` | cobrança de reserva vencida | Reserva expirada |
| 422 | <a id="servico_inativo"></a>`SERVICO_INATIVO` | serviço ou barbeiro inativo | Serviço indisponível |
| 422 | <a id="idempotencia_conflito"></a>`IDEMPOTENCIA_CONFLITO` | mesma chave com corpo diferente | Chave de idempotência já usada |
| 422 | <a id="jornada_sobreposta"></a>`JORNADA_SOBREPOSTA` | intervalos da jornada se sobrepõem | Jornada inválida |
| 422 | <a id="folga_com_reservas"></a>`FOLGA_COM_RESERVAS` | folga sobre reservas que ocupam horário | Existem reservas no período |
| 429 | <a id="limite_excedido"></a>`LIMITE_EXCEDIDO` | limite de tentativas (H14), com `Retry-After` | Muitas tentativas |
| 503 | <a id="gateway_indisponivel"></a>`GATEWAY_INDISPONIVEL` | gateway fora ou lento | Pagamento indisponível no momento |
| 503 | <a id="cep_indisponivel"></a>`CEP_INDISPONIVEL` | ViaCEP e BrasilAPI fora | Consulta de CEP indisponível |
| 500 | <a id="erro_interno"></a>`ERRO_INTERNO` | erro não previsto (sem detalhe técnico na resposta) | Erro interno |

## 6. Propostas (atrás de flag ou pós-beta)

Só a assinatura; não entram no beta.

| Método | Rota | O que faz | Quem pode | Situação |
|---|---|---|---|---|
| `GET` | `/auth/social/{provedor}` | inicia o login OIDC (Google, Facebook) | 🌐 | **atrás de flag** `app.features.social-login=false` → `404 FUNCIONALIDADE_DESLIGADA` |
| `GET` | `/auth/social/{provedor}/retorno` | retorno do provedor; vincula conta só com `email_verified=true` **e** prova de posse da conta local | 🌐 | **atrás de flag** |

## 7. Histórico de versões

| Versão | Data | Mudança | Quebra o front? |
|---|---|---|---|
| 1.5.0 (proposta) | 2026-11-17 | Fatia 6B (privacidade): rota nova `GET /politica-privacidade` e schema `PoliticaPrivacidade`; `409 EXCLUSAO_BLOQUEADA_POR_RESERVA` em `DELETE /me`; `429` em `GET /me/dados` (H22); `ExportacaoDados` detalhado (`conta`, `agendamentos`); `GET /me/consentimentos` devolve o estado atual por finalidade. Fatia 6A (CEP): `GET /cep/{cep}` detalhado (erros, `fonte: CACHE`, `429` com `Retry-After`, limite de 30 por minuto por IP); `POST /auth/cadastro` passa a preencher bairro, cidade e uf quando o CEP é resolvido (nunca `404` ou `503` por causa do CEP, R31). Fatia 6C (alcance): `cep` opcional em `POST /agendamentos/guest`; rotas novas `GET /admin/alcance` (`AlcancePainel`) e `GET /alcance` (`AlcancePublico`, pública); sem subir a versão. `redocly lint` 2.78.0: 0 erros | não (campos e códigos já existiam; `cep` é opcional) |
| 1.4.0 (proposta) | 2026-11-17 | Fatia 5 (painel): `AgendamentoAdmin` e `CobrancaAdmin` (sem Pix, cartão, CPF nem e-mail); rotas novas `GET /admin/pagamentos/eventos`, `POST /admin/pagamentos/eventos/{id}/revisao` e `GET /admin/barbeiros/{id}/folgas`; `409 USUARIO_JA_VINCULADO`; `ServicoRequest.precoCentavos` a partir de **1** centavo; `PaginaAuditoria` com `detalhe` (sem IP); `ConfiguracaoAgenda` sem `fuso` na entrada; `Location` nos `201` dos cadastros | não (nenhum campo renomeado; o preço mínimo só recusa o que a reserva já recusava) |
| 1.2.0 (proposta) | 2026-11-17 | Fatia 3: `PUT /me/senha`; `deveTrocarSenha` no login e no perfil; `403 TROCA_DE_SENHA_OBRIGATORIA`; senha medida em bytes; `WWW-Authenticate: Bearer` nos `401`; limite também por IP; o cabeçalho de rastreio é `X-Trace-Id` (o texto citava `X-Trace-Id`) | não (não há front ainda) |
| 1.1.2 (proposta) | 2026-11-16 | Fatia 2: exemplos do catálogo com dados reais; `GET /disponibilidade` detalha `422 FORA_DO_HORIZONTE` para data passada, `422 SERVICO_INATIVO`, `404` de barbeiro, formato fixo dos horários e os cabeçalhos de cache | não (não há front ainda) |
| 1.1.1 (proposta) | 2026-11-16 | Fatia 1: novos códigos `405 METODO_NAO_PERMITIDO` e `415 MIDIA_NAO_SUPORTADA`, que o tratador global já devolve para erros do Spring MVC | não (códigos novos) |
| 1.1 (proposta) | 2026-11-16 | regras reais da barbearia: pagamento **100% antecipado** por Pix ou cartão de crédito para cliente e guest; cliente logado nasce `AGUARDANDO_PAGAMENTO`; aceite obrigatório da política (`aceitePolitica`, `versaoPolitica`, `GET /politica-pagamento`); **saem** cancelamento e remarcação pelo cliente (`JANELA_CANCELAMENTO_ENCERRADA` vira `ACEITE_POLITICA_OBRIGATORIO`); sai `sinalCentavos`; pagamento tardio confirma se o horário estiver livre. `redocly lint` 2.78.0: 0 erros, 14 avisos | não (não há front ainda) |
| 1.0 (proposta) | 2026-11-13 | primeira versão: catálogo, disponibilidade, CEP, auth, agendamentos, guest, cobrança, webhook, privacidade e painel admin | não (não há front ainda) |
| 1.0.2 (proposta) | 2026-11-13 | novo `409 CONFLITO_CONCORRENTE` em `POST /agendamentos`, `POST /agendamentos/guest` e remarcação (deadlock depois do retry; o front pode repetir uma vez) | não (código novo; nenhum renomeado) |
| 1.0.1 (proposta) | 2026-11-13 | revisão do Alicerce: `cpfMascarado` só no `201` da cobrança (sai da repetição `200` e do painel); `devolucaoPendente` sai de `Cobranca` (o status basta); idempotência compara o corpo por HMAC; `summary` em todas as operações do OpenAPI (`redocly lint` 2.78.0: 0 erros, 14 avisos) | não (não há front ainda) |
