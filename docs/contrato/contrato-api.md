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
| Idempotência | `X-Idempotency-Key` (UUID v4) obrigatória em `POST /pagamentos/cobrancas` (R10). Guardada como hash; mesma chave + mesmo corpo → mesma resposta; corpo diferente → `422` |
| Concorrência | recursos do painel com `version` no corpo; versão antiga → `409 CONFLITO_DE_VERSAO` |
| Paginação | `?pagina=0&tamanho=20` (máximo 50); resposta `{ "itens": [...], "pagina": 0, "tamanho": 20, "total": 3 }` |
| Campos desconhecidos | recusados com `400 CAMPO_DESCONHECIDO` (`fail-on-unknown-properties=true`, OWASP API3) |
| CORS | só as origens de `CORS_ORIGENS` (a do BFF); headers aceitos: `Authorization`, `Content-Type`, `X-Idempotency-Key`, `X-Guest-Token`, `X-Request-Id`; expostos: `Retry-After`, `X-Request-Id` |
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
| `GET` | `/me/dados` | exportar meus dados (LGPD) | 👤 ✂️ 🛠 | `200` | `401` | [4.9](#49-privacidade) |
| `DELETE` | `/me` | excluir minha conta (LGPD) | 👤 | `204` | `401`, `403 ACESSO_NEGADO` (admin não se exclui) | [4.9](#49-privacidade) |
| `GET` | `/me/consentimentos` | meus consentimentos | 👤 | `200` | `401` | [4.9](#49-privacidade) |
| `PUT` | `/me/consentimentos/marketing` | aceita ou revoga marketing | 👤 | `200` | `400`, `401` | [4.9](#49-privacidade) |
| `POST` | `/agendamentos` | reserva como cliente (`CONFIRMADO`) | 👤 | `201` | `400`, `401`, `403`, `404`, `409 HORARIO_INDISPONIVEL`, `422 FORA_DO_EXPEDIENTE`, `422 ANTECEDENCIA_INSUFICIENTE`, `422 SERVICO_INATIVO` | [4.5](#45-agendamentos-do-cliente) |
| `GET` | `/me/agendamentos` | meus agendamentos (paginado) | 👤 | `200` | `401` | [4.5](#45-agendamentos-do-cliente) |
| `GET` | `/agendamentos/{id}` | um agendamento meu | 👤 | `200` | `401`, `404` (inclusive de outro dono) | [4.5](#45-agendamentos-do-cliente) |
| `POST` | `/agendamentos/{id}/cancelamento` | cancela dentro da janela | 👤 | `200` | `401`, `404`, `422 JANELA_CANCELAMENTO_ENCERRADA`, `422 TRANSICAO_INVALIDA` | [4.5](#45-agendamentos-do-cliente) |
| `POST` | `/agendamentos/{id}/remarcacao` | remarca dentro da janela | 👤 | `200` | `401`, `404`, `409 HORARIO_INDISPONIVEL`, `422` | [4.5](#45-agendamentos-do-cliente) |
| `POST` | `/agendamentos/guest` | reserva sem conta (`AGUARDANDO_PAGAMENTO`) | 🌐 (limite por IP) | `201` | `400`, `404`, `409 HORARIO_INDISPONIVEL`, `422`, `429` | [4.6](#46-guest) |
| `GET` | `/agendamentos/guest/{id}` | status da reserva do guest | 🧾 | `200` | `404` (token errado também) | [4.6](#46-guest) |
| `POST` | `/pagamentos/cobrancas` | cria a cobrança Pix do sinal (pede o CPF) | 🧾 | `201` (`200` na repetição) | `400 CPF_INVALIDO`, `400 IDEMPOTENCY_KEY_AUSENTE`, `404`, `422 RESERVA_EXPIRADA`, `422 IDEMPOTENCIA_CONFLITO`, `429`, `503 GATEWAY_INDISPONIVEL` | [4.7](#47-pagamento-do-sinal) |
| `POST` | `/pagamentos/webhook` | aviso do gateway | 🔁 | `200` | `401 WEBHOOK_NAO_AUTENTICADO` | [4.8](#48-webhook) |
| `GET` | `/admin/agenda` | agenda do dia ou da semana | 🛠 ✂️ (só a própria, **H9**) | `200` | `401`, `403`, `404` (barbeiro pedindo outro) | [Painel](../admin/painel-admin.md) |
| `PATCH` | `/admin/agendamentos/{id}/status` | conclui, marca falta ou cancela | 🛠 ✂️ (só a própria) | `200` | `401`, `403`, `404`, `409 CONFLITO_DE_VERSAO`, `422 TRANSICAO_INVALIDA`, `422 MOTIVO_OBRIGATORIO` | [Painel](../admin/painel-admin.md) |
| `GET` `POST` | `/admin/servicos` | lista (inclusive inativos) e cria | 🛠 | `200` / `201` | `400`, `401`, `403`, `409 NOME_JA_EXISTE` | [Painel](../admin/painel-admin.md) |
| `PUT` | `/admin/servicos/{id}` | altera nome, duração, preço, ativo | 🛠 | `200` | `400`, `401`, `403`, `404`, `409` | [Painel](../admin/painel-admin.md) |
| `GET` `POST` | `/admin/barbeiros` | lista e cria | 🛠 | `200` / `201` | `400`, `401`, `403` | [Painel](../admin/painel-admin.md) |
| `PUT` | `/admin/barbeiros/{id}` | altera nome, ativo, login vinculado | 🛠 | `200` | `400`, `401`, `403`, `404`, `409` | [Painel](../admin/painel-admin.md) |
| `PUT` | `/admin/barbeiros/{id}/jornada` | substitui a jornada semanal | 🛠 | `200` | `400`, `401`, `403`, `404`, `422 JORNADA_SOBREPOSTA` | [Painel](../admin/painel-admin.md) |
| `POST` | `/admin/barbeiros/{id}/folgas` | cria folga | 🛠 | `201` | `400`, `401`, `403`, `404`, `422 FOLGA_COM_RESERVAS` | [Painel](../admin/painel-admin.md) |
| `DELETE` | `/admin/barbeiros/{id}/folgas/{folgaId}` | remove folga | 🛠 | `204` | `401`, `403`, `404` | [Painel](../admin/painel-admin.md) |
| `GET` | `/admin/pagamentos` | sinais por status (inclusive devoluções) | 🛠 | `200` | `401`, `403` | [Painel](../admin/painel-admin.md) |
| `POST` | `/admin/pagamentos/{id}/devolucao` | registra devolução manual feita | 🛠 | `200` | `401`, `403`, `404`, `422 TRANSICAO_INVALIDA` | [Painel](../admin/painel-admin.md) |
| `GET` `PUT` | `/admin/configuracao` | regras configuráveis da agenda | 🛠 | `200` | `400`, `401`, `403`, `409` | [Painel](../admin/painel-admin.md) |
| `GET` | `/admin/auditoria` | ações do painel (paginado) | 🛠 | `200` | `401`, `403` | [Painel](../admin/painel-admin.md) |

## 3. Requisitos por endpoint

| Rota | Dono do recurso (BOLA) | Campos que o cliente escreve (BOPLA) | Paginação | Limite de taxa (H14) | Idempotência | p95 esperado (meta, sem medição) |
|---|---|---|---|---|---|---|
| `GET /disponibilidade` | — | — | um dia por chamada | — | — | < 300 ms |
| `POST /auth/login` | — | `email`, `senha` | — | 5 erros/15 min por e-mail + IP | — | — |
| `POST /auth/cadastro` | — | `nome`, `email`, `whatsapp`, `senha`, `cep?`, `aceitaMarketing` | — | 5/h por IP | — | — |
| `POST /agendamentos` | o próprio cliente (do token) | `servicoId`, `barbeiroId`, `inicio` | — | — | — (o horário já é único) | < 400 ms |
| `GET /agendamentos/{id}` | `cliente_id = sub` senão `404` | — | — | — | — | — |
| `POST /agendamentos/guest` | — | `servicoId`, `barbeiroId`, `inicio`, `nome`, `email`, `whatsapp` | — | 3/h por IP | — | < 400 ms |
| `GET /agendamentos/guest/{id}` | hash do `X-Guest-Token` = `token_hash` senão `404` | — | — | — | — | — |
| `POST /pagamentos/cobrancas` | `X-Guest-Token` da reserva | `reservaId`, `cpf` | — | 5/h por IP | **obrigatória** | depende do gateway |
| `POST /pagamentos/webhook` | autenticação do provedor | corpo do provedor (só os campos usados são lidos) | — | — | id do evento único | < 300 ms (só grava) |
| `/admin/**` | ✂️ barbeiro só o próprio `barbeiroId` (H9) | DTO por operação | máx. 50 | — | `version` | — |

## 4. Recursos e payloads

### 4.1 Catálogo

`GET /servicos` → `200`
```json
[
  { "id": "6f1c2a8e-0000-4000-8000-000000000001", "nome": "Corte", "duracaoMin": 30, "precoCentavos": 4000, "precoFormatado": "R$ 40,00" },
  { "id": "6f1c2a8e-0000-4000-8000-000000000002", "nome": "Barba", "duracaoMin": 20, "precoCentavos": 3000, "precoFormatado": "R$ 30,00" }
]
```
`GET /barbeiros` → `200`
```json
[ { "id": "b0000000-0000-4000-8000-000000000001", "nomeExibicao": "Barbeiro Teste 1" } ]
```
> Serviços e preços do seed são **fictícios** (HIPÓTESE H3).

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
      "nomeExibicao": "Barbeiro Teste 1",
      "horarios": ["2026-12-01T09:00:00-03:00", "2026-12-01T09:30:00-03:00", "2026-12-01T11:00:00-03:00"]
    }
  ]
}
```
Regras: jornada − folgas − reservas que ocupam horário (ignorando `AGUARDANDO_PAGAMENTO` vencidas, R15); passos de `granularidadeMin` (H11); só a partir de `agora + antecedenciaMinimaMin` (H5); até `horizonteDias` (H12, senão `422 FORA_DO_HORIZONTE`). Validação: `data` obrigatória, `servicoId` obrigatório e ativo.

### 4.3 CEP

`GET /cep/01001000` → `200`
```json
{ "cep": "01001000", "logradouro": "Praça da Sé", "bairro": "Sé", "cidade": "São Paulo", "uf": "SP", "fonte": "VIACEP" }
```
Aceita `01001-000` ou `01001000`; outro formato → `400 CEP_INVALIDO` sem chamada externa. Os dois provedores fora → `503 CEP_INDISPONIVEL` (o cadastro **não** usa esta rota para decidir: R31).

### 4.4 Autenticação e conta

`POST /auth/cadastro`
```json
{ "nome": "Cliente Teste A", "email": "cliente.a@exemplo.test", "whatsapp": "(11) 90000-0001", "senha": "Senha-de-teste-123", "cep": "01001-000", "aceitaMarketing": false }
```
`201` + `Location: /me`
```json
{ "id": "c0000000-0000-4000-8000-00000000000a", "nome": "Cliente Teste A", "email": "cliente.a@exemplo.test", "papeis": ["CLIENTE"], "endereco": { "cep": "01001000", "bairro": "Sé", "cidade": "São Paulo", "uf": "SP", "completo": true } }
```
Validações: `nome` 2–120; `email` válido e único (R24); `whatsapp` 10 a 13 dígitos depois de sanitizar; `senha` 10–72 caracteres (limite do BCrypt); `cep` opcional, 8 dígitos; `aceitaMarketing` opcional, padrão `false` (R28). CEP não resolvido → `endereco.completo: false` e bairro/cidade nulos (R31). Resposta **nunca** tem `senha` nem `senhaHash`.

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

`GET /me` → `200` com o mesmo formato do cadastro.

### 4.5 Agendamentos do cliente

`POST /agendamentos` (👤)
```json
{ "servicoId": "6f1c2a8e-0000-4000-8000-000000000001", "barbeiroId": "b0000000-0000-4000-8000-000000000001", "inicio": "2026-12-01T10:00:00-03:00" }
```
`201` + `Location: /agendamentos/{id}`
```json
{
  "id": "a0000000-0000-4000-8000-000000000101",
  "status": "CONFIRMADO",
  "origem": "CLIENTE",
  "servico": { "id": "6f1c2a8e-0000-4000-8000-000000000001", "nome": "Corte", "duracaoMin": 30 },
  "barbeiro": { "id": "b0000000-0000-4000-8000-000000000001", "nomeExibicao": "Barbeiro Teste 1" },
  "inicio": "2026-12-01T10:00:00-03:00",
  "fim": "2026-12-01T10:30:00-03:00",
  "precoCentavos": 4000,
  "sinalCentavos": 0,
  "podeCancelarAte": "2026-12-01T08:00:00-03:00"
}
```
Erros típicos: horário já ocupado (inclusive por corrida) → `409 HORARIO_INDISPONIVEL`; fora da jornada ou em folga → `422 FORA_DO_EXPEDIENTE`; antes de `agora + antecedência` → `422 ANTECEDENCIA_INSUFICIENTE`; `inicio` fora da granularidade → `400 VALIDACAO_INVALIDA`. Mandar `status` ou `sinalCentavos` no corpo → `400 CAMPO_DESCONHECIDO`.

`GET /me/agendamentos?pagina=0&tamanho=20` → `200` página do formato acima, mais recentes primeiro.

`POST /agendamentos/{id}/cancelamento` `{ "motivo": "imprevisto" }` (motivo opcional para o cliente) → `200` com `status: "CANCELADO"`. Depois de `podeCancelarAte` → `422 JANELA_CANCELAMENTO_ENCERRADA` (R18).

`POST /agendamentos/{id}/remarcacao` `{ "inicio": "2026-12-01T11:00:00-03:00", "barbeiroId": "b0000000-0000-4000-8000-000000000001" }` → `200` com o novo horário; se ocupado, `409` e nada muda (R19).

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
  "precoCentavos": 4000,
  "sinalCentavos": 2000,
  "expiraEm": "2026-11-30T15:15:00-03:00",
  "tokenGuest": "g1_Qm9...",
  "politicaSinal": "HIPÓTESE: em caso de falta, o sinal fica com a barbearia. Cancelamento dentro do prazo tem o sinal devolvido manualmente."
}
```
> O `tokenGuest` aparece **uma vez** (o banco guarda só o hash). É o que permite ao guest consultar a reserva e pagar: sem conta, sem expor a reserva a quem só tem o id. O CPF **não** é pedido aqui (R9).

`GET /agendamentos/guest/{id}` com `X-Guest-Token: g1_Qm9...` → `200` com `status` atual (o front consulta a cada poucos segundos depois do Pix). Se a cobrança estiver `PENDENTE`, a API reconsulta o gateway (no máximo a cada 10 s) para confirmar mesmo que o webhook atrase (ADR 0007). Token errado ou ausente → `404`.

### 4.7 Pagamento do sinal

`POST /pagamentos/cobrancas` com `X-Guest-Token` e `X-Idempotency-Key: 5d0b3f8e-7c1a-4b7e-9f63-2a0d6c1e9b10`
```json
{ "reservaId": "a0000000-0000-4000-8000-000000000102", "cpf": "123.456.789-09" }
```
`201`
```json
{
  "id": "d0000000-0000-4000-8000-000000000201",
  "reservaId": "a0000000-0000-4000-8000-000000000102",
  "status": "PENDENTE",
  "valorCentavos": 2000,
  "pix": { "copiaECola": "00020126580014br.gov.bcb.pix...", "expiraEm": "2026-11-30T15:15:00-03:00" },
  "cpfMascarado": "***.***.***-09"
}
```
Repetição com a mesma chave e o mesmo corpo → `200` com a mesma cobrança, sem chamar o gateway de novo. Mesma chave e corpo diferente → `422 IDEMPOTENCIA_CONFLITO`. Reserva vencida → `422 RESERVA_EXPIRADA`. Gateway fora ou lento → `503 GATEWAY_INDISPONIVEL` (a reserva segue até expirar). O CPF é validado pelos dígitos, repassado ao gateway e **não é gravado** (ADR 0009). O QR Code é desenhado no front a partir do `copiaECola`.

### 4.8 Webhook

`POST /pagamentos/webhook` (🔁 Asaas, header `asaas-access-token`, ADR 0007). Corpo do provedor (campos que a API lê):
```json
{ "id": "evt_teste_0001", "event": "PAYMENT_RECEIVED", "payment": { "id": "pay_teste_0001", "billingType": "PIX", "status": "RECEIVED" } }
```
Respostas: `200` sem corpo (primeira vez **e** repetição, R12); `401 WEBHOOK_NAO_AUTENTICADO` com token errado ou ausente, sem gravar nada (R11). O corpo é tratado como **sinal de mudança**: a API reconsulta a cobrança no gateway antes de marcar `PAGA` (OWASP API10). Eventos de tipos que a API não usa → `200` e registro como `IGNORADO`.

### 4.9 Privacidade

`GET /me/dados` → `200` (exportação)
```json
{ "geradoEm": "2026-12-01T10:00:00-03:00", "conta": { "nome": "Cliente Teste A", "email": "cliente.a@exemplo.test", "whatsapp": "11900000001", "endereco": { "cep": "01001000", "bairro": "Sé", "cidade": "São Paulo", "uf": "SP" } }, "consentimentos": [ { "finalidade": "MARKETING_CEP", "aceito": false, "versaoTexto": "1.0", "registradoEm": "2026-11-20T09:00:00-03:00" } ], "agendamentos": [ { "id": "a0000000-0000-4000-8000-000000000101", "inicio": "2026-12-01T10:00:00-03:00", "status": "CONFIRMADO" } ] }
```
`DELETE /me` → `204`; anonimiza a conta, revoga sessões e cancela reservas futuras (ADR 0009). O login seguinte dá `401`.

`PUT /me/consentimentos/marketing` `{ "aceito": true, "versaoTexto": "1.0" }` → `200` com o consentimento registrado (cada mudança é uma linha nova, R28).

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
| 404 | <a id="recurso_nao_encontrado"></a>`RECURSO_NAO_ENCONTRADO` | não existe **ou** não pertence a quem pede | Não encontrado |
| 404 | <a id="cep_nao_encontrado"></a>`CEP_NAO_ENCONTRADO` | CEP válido sem endereço nos provedores | CEP não encontrado |
| 404 | <a id="funcionalidade_desligada"></a>`FUNCIONALIDADE_DESLIGADA` | rota atrás de flag desligada | Não encontrado |
| 409 | <a id="email_ja_cadastrado"></a>`EMAIL_JA_CADASTRADO` | e-mail já em uso | E-mail já cadastrado |
| 409 | <a id="horario_indisponivel"></a>`HORARIO_INDISPONIVEL` | violação da constraint `reserva_sem_sobreposicao` | Horário indisponível |
| 409 | <a id="conflito_de_versao"></a>`CONFLITO_DE_VERSAO` | `version` desatualizada | Registro alterado por outra pessoa |
| 409 | <a id="nome_ja_existe"></a>`NOME_JA_EXISTE` | serviço com o mesmo nome | Nome já existe |
| 422 | <a id="fora_do_expediente"></a>`FORA_DO_EXPEDIENTE` | fora da jornada ou em folga | Fora do horário de atendimento |
| 422 | <a id="antecedencia_insuficiente"></a>`ANTECEDENCIA_INSUFICIENTE` | antes da antecedência mínima (H5) | Antecedência mínima não atendida |
| 422 | <a id="fora_do_horizonte"></a>`FORA_DO_HORIZONTE` | data além do horizonte (H12) | Data além do permitido |
| 422 | <a id="janela_cancelamento_encerrada"></a>`JANELA_CANCELAMENTO_ENCERRADA` | cancelar/remarcar fora da janela (H6) | Prazo para alterar encerrado |
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
| `GET` | `/admin/alcance` | agregados por bairro e cidade, só de quem consentiu, grupos < 5 ocultos | 🛠 ✂️ | **pós-beta** |
| `POST` | `/agendamentos/guest/{id}/cancelamento` | guest cancela pelo link | 🧾 | **pós-beta** (H16) |

## 7. Histórico de versões

| Versão | Data | Mudança | Quebra o front? |
|---|---|---|---|
| 1.0 (proposta) | 2026-11-13 | primeira versão: catálogo, disponibilidade, CEP, auth, agendamentos, guest, cobrança, webhook, privacidade e painel admin | não (não há front ainda) |
