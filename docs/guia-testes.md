# 🧪 Teste você mesmo, de ponta a ponta · Barbearia Altas Horas

| | |
|---|---|
| 🎯 **Para que serve** | subir o projeto do zero e testar **à mão**, sem depender de ninguém (nem de IA), cada regra que importa |
| 👥 **Para quem** | o Kevin, um recrutador, um revisor |
| 🧰 **Precisa de** | Docker Desktop · JDK 25 · Git Bash (ou qualquer terminal com `curl`) |
| 🔒 **Sem segredo** | dados **fictícios** e gateway em **sandbox**; nenhum dinheiro real |

> [!IMPORTANT]
> **✅ = dá para testar hoje**, com a saída copiada de uma execução real (Fatias 1 a 3, 16 e 17/11/2026).
> **⏳ Fatia N = roteiro já escrito**, que vira ✅ quando a fatia entrar na `develop` (aí ganha o comando e a resposta reais).

## Mapa

| Parte | O que você testa | Hoje |
|---|---|:-:|
| [0. Subir](#0-subir-o-projeto) | banco, API e health | ✅ |
| [1. Testes automatizados](#1-testes-automatizados) | 172 testes com PostgreSQL real | ✅ |
| [2. O banco na mão](#2-o-banco-na-mão-as-regras-que-o-postgresql-garante) | double booking, aceite da política, método de pagamento | ✅ |
| [3. Erros padronizados](#3-erros-padronizados-rfc-9457) | `problem+json`, `traceId` | ✅ |
| [4. Cenário do visitante](#4-cenário-do-visitante-sem-conta) | ver serviços e horários, reservar sem conta, pagar (simulado) | ✅ V1 a V9 · ⏳ Fatia 5 |
| [5. Cenário do cliente](#5-cenário-do-cliente-com-conta) | cadastro, login, sessão, reservar, listar e pagar (simulado) | ✅ C1 a C12 · ⏳ Fatia 6 |
| [6. Cenário do admin](#6-cenário-do-admin-o-barbeiro) | entrar e trocar a senha inicial (✅), agenda e fechamento | ✅ AD1 a AD3 · ⏳ Fatia 5 |
| [7. As telas](#7-as-telas-frontend) | Next.js com checkout e aceite | ⏳ Fatia 7 |
| [8. Jornada completa](#8-jornada-completa) | tudo junto, numa base limpa | ⏳ Fatia 7 |
| [9. RAG da documentação](#9-rag-perguntar-à-documentação) | busca semântica com Spring AI + pgvector e a avaliação | ✅ |
| [Deu erro?](#deu-erro) | problemas comuns | ✅ |

---

## 0. Subir o projeto

```bash
git clone https://github.com/k3vinrich4rd/barbearia-altas-horas.git
cd barbearia-altas-horas
docker compose up -d                                         # PostgreSQL 18 na porta 5433
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Deixe esse terminal aberto (a API fica rodando) e abra **outro** para os testes. O perfil `dev` não precisa de `.env` e cria dados fictícios: o barbeiro **Carlos Eduardo Cândido**, os 5 serviços reais e a jornada de terça a sábado (07h–12h e 13h–20h).

```bash
curl -i http://localhost:8080/actuator/health
```

✅ Resposta real:

```http
HTTP/1.1 200
X-Trace-Id: 282f13e49d0c490fa4bd12f6194c37b9
Content-Type: application/vnd.spring-boot.actuator.v3+json

{"groups":["liveness","readiness"],"status":"UP"}
```

**O que prova:** a API subiu, conectou no banco e o Flyway aplicou a migração `V1` e o seed.

Para parar: `Ctrl+C` na API e `docker compose stop`. Para **zerar o banco**: `docker compose down -v`.

## 1. Testes automatizados

```bash
cd backend && ./mvnw verify
```

✅ Resultado real (Windows + Docker Desktop): **172 testes, 0 falhas, 0 pulados** (17/11/2026, com a Fatia 4B), `BUILD SUCCESS`, cobertura de instruções **95,3%**. O relatório abre em `backend/target/site/jacoco/index.html`.

| Classe | Testes | O que prova |
|---|:-:|---|
| `ContextoSobeIT` | 1 | a API sobe no PostgreSQL real e o health responde UP |
| `ErroPadraoTest` | 3 | 404 e 405 em `problem+json` com `code` e `traceId`; `X-Trace-Id` fora do formato é descartado |
| `V1MigracaoIT` | 6 | V1 aplicada, `btree_gist`, constraint e índices; regras reais na configuração; o banco recusa `AGUARDANDO_PAGAMENTO` sem prazo, `CONFIRMADO` sem aceite e método fora de PIX/cartão |
| `SobreposicaoConstraintIT` | 6 | mesmo horário e sobreposição parcial dão `23P01`; encostadas `[)` passam; `CANCELADO` e `EXPIRADO` não ocupam |
| `ConcorrenciaReservaIT` | 3 | 12 conexões disputando o mesmo horário: **exatamente 1 vence** (repetido 3 vezes) |
| `SeedDevIT` | 4 | perfil `dev` com o Carlos, os 5 serviços, a jornada com almoço e 24 horários pela API |
| `CalculadoraDeHorariosTest` | 12 | a regra dos horários, pura: almoço, serviço de 60 min, antecedência inclusiva, folga, intervalo meio aberto |
| `DisponibilidadeIT` | 19 | a rota inteira no banco real: o que ocupa, pendente vencida, horizonte, erros 400/404/422, formato e **sem N+1** |
| `CatalogoIT` · `PoliticaPagamentoIT` · `PrecoFormatadoTest` | 3 · 1 · 6 | só ativos, só os campos do contrato, cache, política vigente, `R$ 1.234,56` |
| `CadastroIT` · `LoginIT` · `MeIT` | 9 · 5 · 2 | conta criada com senha só em hash, campo a mais recusado, mesma resposta para e-mail inexistente, token vencido pelo relógio |
| `RefreshRotativoIT` · `LogoutIT` · `TrocaDeSenhaIT` | 5 · 1 · 2 | rotação, reuso derruba a família, 8 renovações simultâneas com no máximo uma vencedora, troca de senha encerra as sessões |
| `LimiteTentativasIT` · `LimitadorDeTentativasTest` | 5 · 3 | 429 com `Retry-After` por e-mail + IP e por IP; janela deslizante |
| `AdminInicialIT` · `MatrizAcessoIT` · `SegurancaWebIT` | 5 · 3 · 3 | admin criado por variável sem sobrescrever, troca obrigatória, 401/403/404, negar por padrão, CORS e CSRF onde há cookie |
| `SemDadoPessoalNoLogIT` · `ValueObjectsTest` | 1 · 1 | nenhum e-mail, telefone, CEP, senha ou token no log |
| `ModulithTest` | 3 | 8 módulos e fronteiras verificadas |
| `ArquiteturaTest` | 3 | ninguém lê o relógio do sistema fora do `Clock`; dinheiro nunca em `double`; o núcleo da regra não conhece framework |

Conferir que todo critério da spec tem teste: `sh scripts/conferir-spec.sh 3 --exigir` (44 de 44; fatias 1 e 2: 10 e 29).

Rodar uma classe só: `./mvnw test -Dtest=ConcorrenciaReservaIT`.

## 2. O banco na mão: as regras que o PostgreSQL garante

Abra o `psql` dentro do container (a API pode estar rodando ou não):

```bash
docker exec -it altas-horas-postgres psql -U altashoras
```

Para sair do `psql`: `\q`.

### 2.1 ✅ O catálogo real

```sql
SELECT nome, duracao_min, preco_centavos FROM servico ORDER BY preco_centavos;
```

```text
                nome                 | duracao_min | preco_centavos
-------------------------------------+-------------+----------------
 Acabamento (Pezinho) / Sobrancelha  |          30 |           2000
 Barba Completa / Desenhada          |          30 |           3000
 Corte Clássico / Social             |          30 |           4500
 Corte Degradê / Fade / Navalhado    |          30 |           5500
 Corte Moderno / Visagismo (Tesoura) |          60 |           6000
```

**O que prova:** preço em centavos (inteiro, nunca `float`) e o visagismo ocupando 60 min.

### 2.2 ✅ Dois clientes não pegam o mesmo horário

Primeira reserva, terça 01/12/2026 às 10h00 (Corte Degradê):

```sql
INSERT INTO reserva (barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos, expira_em)
VALUES ('b0000000-0000-4000-8000-000000000001', '6f1c2a8e-0000-4000-8000-000000000002', 'GUEST',
        '2026-12-01 10:00-03', '2026-12-01 10:30-03', '2026-12-01 10:30-03', 'AGUARDANDO_PAGAMENTO', 5500, '2026-12-01 09:00-03');
```

`INSERT 0 1`. Agora outro cliente tenta 10h15, que sobrepõe:

```sql
INSERT INTO reserva (barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos, expira_em)
VALUES ('b0000000-0000-4000-8000-000000000001', '6f1c2a8e-0000-4000-8000-000000000001', 'GUEST',
        '2026-12-01 10:15-03', '2026-12-01 10:45-03', '2026-12-01 10:45-03', 'AGUARDANDO_PAGAMENTO', 4500, '2026-12-01 09:00-03');
```

```text
ERROR:  conflicting key value violates exclusion constraint "reserva_sem_sobreposicao"
```

**O que prova:** quem impede o *double booking* é o banco (`EXCLUDE USING gist`), não um `if` no código. Vale para qualquer caminho de escrita e para duas instâncias da API ([ADR 0003](adr/0003-anti-double-booking-no-banco.md)). Troque `10:15` por `10:30` e o `INSERT` passa: o intervalo é meio aberto `[)`.

### 2.3 ✅ Ninguém é confirmado sem aceitar a política

```sql
UPDATE reserva SET status = 'CONFIRMADO' WHERE inicio = '2026-12-01 10:00-03';
```

```text
ERROR:  new row for relation "reserva" violates check constraint "reserva_aceite_ck"
```

**O que prova:** mesmo que um bug no código tentasse confirmar sem o aceite, o banco recusa (regra R18).

### 2.4 ✅ Limpar

```sql
DELETE FROM reserva WHERE inicio >= '2026-12-01' AND inicio < '2026-12-02';
```

## 3. Erros padronizados (RFC 9457)

```bash
curl -i "http://localhost:8080/disponibilidade?servicoId=00000000-0000-4000-8000-000000000000&data=2026-12-01"
```

✅ O mesmo formato vale para todo erro da API. Exemplo real de uma rota que exige login, chamada sem token:

```http
HTTP/1.1 401
WWW-Authenticate: Bearer

{"detail":"Faça login para continuar.","instance":"/me","status":401,"title":"Não autenticado",
 "type":"https://github.com/k3vinrich4rd/barbearia-altas-horas/blob/main/docs/contrato/contrato-api.md#nao_autenticado",
 "code":"NAO_AUTENTICADO","traceId":"a8cef819f9a74aad85179e1b65f0e1fd","errors":[]}
```

**O que prova:** o front decide pelo `code` (estável), e o `traceId` da tela é o mesmo do log da API. Confira: procure o `traceId` da sua resposta no terminal onde a API está rodando.

> Desde a Fatia 3 a API **nega por padrão**: rota que ninguém declarou responde `401` a quem não está logado (e `404` só a quem tem login). Assim, uma rota nova nunca nasce aberta por esquecimento.

---

## 4. Cenário do visitante (sem conta)

### V1 ✅ Ver os serviços

```bash
curl -i http://localhost:8080/servicos
```

```http
HTTP/1.1 200
Cache-Control: max-age=60, public

[{"id":"6f1c2a8e-0000-4000-8000-000000000005","nome":"Acabamento (Pezinho) / Sobrancelha","duracaoMin":30,"precoCentavos":2000,"precoFormatado":"R$ 20,00"},
 {"id":"6f1c2a8e-0000-4000-8000-000000000004","nome":"Barba Completa / Desenhada","duracaoMin":30,"precoCentavos":3000,"precoFormatado":"R$ 30,00"},
 {"id":"6f1c2a8e-0000-4000-8000-000000000001","nome":"Corte Clássico / Social","duracaoMin":30,"precoCentavos":4500,"precoFormatado":"R$ 45,00"},
 {"id":"6f1c2a8e-0000-4000-8000-000000000002","nome":"Corte Degradê / Fade / Navalhado","duracaoMin":30,"precoCentavos":5500,"precoFormatado":"R$ 55,00"},
 {"id":"6f1c2a8e-0000-4000-8000-000000000003","nome":"Corte Moderno / Visagismo (Tesoura)","duracaoMin":60,"precoCentavos":6000,"precoFormatado":"R$ 60,00"}]
```

**O que prova:** catálogo vindo do banco, em ordem de nome, só com os campos do contrato (nada de `ativo` ou `version`).

### V2 ✅ Ler a política de pagamento

```bash
curl http://localhost:8080/politica-pagamento
```

```json
{"versao":1,"texto":"Após o pagamento antecipado de 100%, não há cancelamento, remarcação ou estorno em caso de desistência ou falta."}
```

### V3 ✅ Ver os horários livres

Corte Moderno / Visagismo (60 min) na terça 01/12/2026. Use uma data **de hoje até 30 dias à frente**; troque se já passou.

```bash
curl -i "http://localhost:8080/disponibilidade?servicoId=6f1c2a8e-0000-4000-8000-000000000003&data=2026-12-01"
```

```http
HTTP/1.1 200
Cache-Control: no-store

{"data":"2026-12-01","fuso":"America/Sao_Paulo","servicoId":"6f1c2a8e-0000-4000-8000-000000000003","duracaoMin":60,
 "barbeiros":[{"barbeiroId":"b0000000-0000-4000-8000-000000000001","nomeExibicao":"Carlos Eduardo Cândido",
 "horarios":["2026-12-01T07:00:00-03:00","2026-12-01T07:30:00-03:00", "...", "2026-12-01T11:00:00-03:00",
             "2026-12-01T13:00:00-03:00", "...", "2026-12-01T19:00:00-03:00"]}]}
```

**O que prova:** 22 horários; depois das 11:00 vem 13:00 (às 11:30 o serviço de 60 min invadiria o almoço) e o último é 19:00 (às 19:30 passaria das 20h). Com o Corte Degradê (`...000002`, 30 min) são 24.

Agora mexa e veja a regra reagir:

| Faça | Tem que voltar |
|---|---|
| a mesma chamada com `data=2026-12-06` (domingo) | `200` com `"horarios":[]` |
| sem o parâmetro `data` | `400 VALIDACAO_INVALIDA` com `"errors":[{"message":"obrigatório","field":"data"}]` |
| `data=2027-03-01` | `422 FORA_DO_HORIZONTE` · "A agenda aceita datas de hoje até 30 dias à frente." |
| `servicoId` com um UUID qualquer | `404 RECURSO_NAO_ENCONTRADO` |
| ocupe um horário pelo `psql` (parte 2.2) e repita | o horário some; apague a reserva e ele volta |
| data de **hoje** | só horários a partir de agora + 2 horas |

### V4 ✅ Reservar sem conta

Escolha um horário livre do V3 (aqui, terça 24/11 às 10h30) e mande nome, e-mail e WhatsApp **fictícios**. Não há CPF nesta etapa.

```bash
curl -s -i -X POST localhost:8080/agendamentos/guest -H "Content-Type: application/json" -d '{
  "servicoId": "6f1c2a8e-0000-4000-8000-000000000002", "barbeiroId": "b0000000-0000-4000-8000-000000000001",
  "inicio": "2026-11-24T10:30:00-03:00",
  "nome": "Visitante Teste", "email": "visitante@exemplo.test", "whatsapp": "(11) 90000-0002" }'
```

Saída real (17/11/2026, 11h48; token encurtado aqui):

```
201  Location: /agendamentos/guest/af072ba4-57eb-45cd-bd37-312e7b868b70
{"id":"af072ba4-…","status":"AGUARDANDO_PAGAMENTO","origem":"GUEST","inicio":"2026-11-24T10:30:00-03:00",
 "expiraEm":"2026-11-17T12:03:12-03:00","tokenGuest":"g1_8LI…(46 caracteres)", ...}
```

**O que prova:** o horário fica travado por **15 minutos** esperando o pagamento de 100%. O `tokenGuest` aparece **só nesta resposta**; o banco guarda apenas o SHA-256 dele. A 4ª reserva sem conta do mesmo IP em uma hora recebe `429 LIMITE_EXCEDIDO` com `Retry-After`.

### V5 ✅ Acompanhar a reserva pelo token

| Chamada | Saída real |
|---|---|
| `GET /agendamentos/guest/{id}` com `X-Guest-Token: <token do V4>` | `200` `{"status":"AGUARDANDO_PAGAMENTO","inicio":"2026-11-24T10:30:00-03:00", ...}` |
| o mesmo com `X-Guest-Token: g1_errado` | `404 RECURSO_NAO_ENCONTRADO` |
| sem o cabeçalho | `404` |

**O que prova:** token errado responde igual a "não existe": a API não confirma que a reserva existe para quem não tem o token.

### V6 ✅ O horário sumiu da agenda

Repita o V3 para a mesma data. Saída real, entre 9h e 11h30: `['09:00', '09:30', '11:00', '11:30']`. As 10h (reservada pelo cliente do C8) e as 10h30 (do V4) saíram. Se ninguém pagar em 15 minutos, o job de expiração libera os dois de novo.

### V7 ✅ Pagar sem aceitar a política

No beta o pagamento é **simulado** (D59): nenhuma conta em gateway e nenhum dinheiro real, mas o caminho de confirmação é o mesmo do webhook de produção. Use o `id` e o `tokenGuest` de uma reserva do V4 (aqui, uma nova, terça 01/12 às 10h) e o CPF fictício `123.456.789-09`.

```bash
curl -s -i -X POST localhost:8080/pagamentos/cobrancas -H "Content-Type: application/json" \
  -H "X-Guest-Token: g1_..." -H "X-Idempotency-Key: $(uuidgen)" \
  -d '{ "reservaId": "<id da reserva>", "metodo": "PIX", "cpf": "123.456.789-09" }'
```

Saída real (17/11/2026, 13h31):

```
422  {"title":"Aceite da política obrigatório","status":422,"code":"ACEITE_POLITICA_OBRIGATORIO",
      "detail":"Aceite a política de pagamento para continuar.","traceId":"8d520969…","errors":[]}
```

**O que prova:** ninguém paga sem aceitar a política; sem o campo, o erro é `422` e não `400`.

### V8 ✅ Gerar o Pix (e repetir sem cobrar duas vezes)

```bash
CHAVE=$(uuidgen)
curl -s -i -X POST localhost:8080/pagamentos/cobrancas -H "Content-Type: application/json" \
  -H "X-Guest-Token: g1_..." -H "X-Idempotency-Key: $CHAVE" \
  -d '{ "reservaId": "<id>", "metodo": "PIX", "cpf": "123.456.789-09", "aceitePolitica": true, "versaoPolitica": 1 }'
# repita o mesmo comando, com a mesma $CHAVE
```

Saída real (17/11/2026, 13h31; copia e cola encurtado aqui):

```
201  {"id":"1ef0b1ad-c4ad-4ee5-a0b8-b6b906005f03","status":"PENDENTE","metodo":"PIX","valorCentavos":5500,
      "pix":{"copiaECola":"SIMULADO-1ef0b1ad-c4…(45 caracteres)","expiraEm":"2026-11-17T13:45:39-03:00"},
      "cpfMascarado":"***.***.***-09"}
200  (mesma chave) o mesmo "id", sem "cpfMascarado"
```

**O que prova:** o Pix cobra **100%** do preço (`5500` = R$ 55,00). O CPF só trafega nesta chamada: a API devolve apenas `***.***.***-09`, e a repetição com a mesma `X-Idempotency-Key` devolve a **mesma** cobrança (`200`), nunca uma segunda.

### V9 ✅ Confirmar o pagamento (simulado)

```bash
curl -s -i -X POST localhost:8080/pagamentos/simulacao/<cobrancaId>/confirmar -H "X-Guest-Token: g1_..."
curl -s localhost:8080/agendamentos/guest/<id da reserva> -H "X-Guest-Token: g1_..."
```

Saída real (17/11/2026, 13h31):

```
200  {"id":"1ef0b1ad-…","status":"PAGA","metodo":"PIX","valorCentavos":5500, ...}
200  {"id":"76d3d50a-…","status":"CONFIRMADO","inicio":"2026-12-01T10:00:00-03:00","origem":"GUEST"}
```

**O que prova:** a confirmação passa pelo **mesmo** fluxo do webhook (evento gravado, gateway reconsultado, reserva confirmada). Prova também a porta de entrada real: `POST /pagamentos/webhook` sem o cabeçalho `asaas-access-token` responde `401 WEBHOOK_NAO_AUTENTICADO`. No modo real (`asaas`) a rota de simulação deixa de existir.

### V10 ⏳ Não pagar em 15 minutos

Faça outra reserva e **não** pague: em 15 minutos ela vira `EXPIRADO` e o horário volta no V3. A expiração já foi provada na Fatia 4A (job a cada minuto); aqui só repete o caminho.

## 5. Cenário do cliente (com conta)

Use um e-mail fictício seu (`@exemplo.test`). Os tokens abaixo estão cortados; os seus serão longos.

### C1 ✅ Criar a conta

```bash
curl -i -X POST http://localhost:8080/auth/cadastro -H "Content-Type: application/json" \
  -d '{"nome":"Cliente Teste A","email":"cliente.a@exemplo.test","whatsapp":"(11) 90000-0001","senha":"Senha-de-teste-123","cep":"01001-000"}'
```

```http
HTTP/1.1 201
Location: /me

{"id":"55b5325b-8484-455c-bf58-1059f3a136bf","nome":"Cliente Teste A","email":"cliente.a@exemplo.test","papeis":["CLIENTE"],
 "endereco":{"cep":"01001000","bairro":null,"cidade":null,"uf":null,"completo":false},"deveTrocarSenha":false}
```

**O que prova:** o CPF não é pedido; a resposta não tem senha; o CEP fica guardado e o endereço completo chega na Fatia 6.

### C2 ✅ Repetir o cadastro

A mesma chamada de novo (ou com o e-mail em MAIÚSCULAS) → `409` com `"code":"EMAIL_JA_CADASTRADO"`.
Mande `"papeis":["ADMIN"]` no corpo → `400 CAMPO_DESCONHECIDO`: ninguém se cadastra como admin.

### C3 ✅ Entrar

```bash
curl -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"email":"cliente.a@exemplo.test","senha":"Senha-de-teste-123"}'
```

```json
{"accessToken":"eyJhbGciOiJI...","tipo":"Bearer","expiraEmSegundos":900,"refreshToken":"r1_wws-...",
 "refreshExpiraEm":"2026-11-24T03:40:11-03:00",
 "usuario":{"id":"55b5325b-8484-455c-bf58-1059f3a136bf","nome":"Cliente Teste A","papeis":["CLIENTE"]},"deveTrocarSenha":false}
```

Guarde os dois valores para os próximos passos:

```bash
ACESSO="cole aqui o accessToken"
REFRESH="cole aqui o refreshToken"
```

### C4 ✅ Ver o próprio perfil

```bash
curl http://localhost:8080/me -H "Authorization: Bearer $ACESSO"
```

Volta o mesmo perfil do C1. Sem o cabeçalho → `401 NAO_AUTENTICADO` com `WWW-Authenticate: Bearer`.

### C5 ✅ Bater na porta do painel

```bash
curl http://localhost:8080/admin/agenda -H "Authorization: Bearer $ACESSO"
```

→ `403` com `"code":"ACESSO_NEGADO"`. Sem token seria `401`.

### C6 ✅ Renovar a sessão, e ver o que acontece com um token copiado

```bash
curl -X POST http://localhost:8080/auth/refresh -H "Content-Type: application/json" -d "{\"refreshToken\":\"$REFRESH\"}"
```

Primeira vez: `200` com um par **novo**. Rode **o mesmo comando de novo**, com o refresh antigo:

```text
"status":401, "code":"REFRESH_REUTILIZADO"
```

**O que prova:** cada refresh serve uma vez. Se o mesmo aparece duas vezes, alguém copiou: a sessão inteira é derrubada, inclusive o token novo.

### C7 ✅ Errar a senha seis vezes

Repita o login do C3 com a senha errada. Resultado real:

```text
401 401 401 401 401 429
HTTP/1.1 429
Retry-After: 898
```

**O que prova:** depois de 5 erros em 15 minutos, nem a senha certa entra; o `Retry-After` diz em quantos segundos libera.

Para sair: `POST /auth/logout` com o refresh → `204`. Para trocar a senha: `PUT /me/senha` com `senhaAtual` e `novaSenha` → `204` (as outras sessões param de renovar).

### C8 ✅ Reservar com a conta

Com o `accessToken` do C3:

```bash
curl -s -i -X POST localhost:8080/agendamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -d '{ "servicoId": "6f1c2a8e-0000-4000-8000-000000000002", "barbeiroId": "b0000000-0000-4000-8000-000000000001",
        "inicio": "2026-11-24T10:00:00-03:00" }'
```

Saída real (17/11/2026, 11h48):

```
201  Location: /agendamentos/20b31b02-5bc5-4bec-9ef7-057a24d37f5d
{"status":"AGUARDANDO_PAGAMENTO","origem":"CLIENTE","servico":{"nome":"Corte Degradê / Fade / Navalhado","duracaoMin":30},
 "inicio":"2026-11-24T10:00:00-03:00","fim":"2026-11-24T10:30:00-03:00","precoCentavos":5500,"expiraEm":"2026-11-17T12:03:11-03:00", ...}
```

**O que prova:** quem tem conta **também** paga 100% antes (R6); o preço vem do serviço e fica congelado na reserva.

E os erros, todos com saída real:

| Tentativa | Resposta |
|---|---|
| outro cliente, mesmo horário | `409 HORARIO_INDISPONIVEL` · "Este horário acabou de ser reservado. Escolha outro." |
| 12h00 (almoço) | `422 FORA_DO_EXPEDIENTE` |
| sem o token | `401 NAO_AUTENTICADO` |
| corpo com `"status": "CONFIRMADO"` | `400 CAMPO_DESCONHECIDO` (ninguém se confirma sozinho) |

### C9 ✅ Meus agendamentos

`GET /me/agendamentos` (paginado: `pagina` a partir de 0, `tamanho` até 50). Saída real: `200 total=1 itens=[('2026-11-24T10:00:00-03:00', 'AGUARDANDO_PAGAMENTO')]`. O dono vem do token, nunca da URL.

### C10 ✅ A reserva de outra pessoa

Outro cliente pede `GET /agendamentos/20b31b02-…` → `404 RECURSO_NAO_ENCONTRADO` (não `403`): a resposta não confirma que o id existe.

### C11 ✅ Tentar cancelar

`POST /agendamentos/{id}/cancelamento` com o token do dono → `404`. **Não existe** cancelamento nem remarcação pelo cliente (R18); a reserva continua `AGUARDANDO_PAGAMENTO`.

### C12 ✅ Pagar com cartão (simulado)

Com o token do cliente (C2) e uma reserva dele (C8, aqui terça 01/12 às 11h):

```bash
curl -s -i -X POST localhost:8080/pagamentos/cobrancas -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" -H "X-Idempotency-Key: $(uuidgen)" \
  -d '{ "reservaId": "<id>", "metodo": "CARTAO_CREDITO", "cpf": "123.456.789-09", "aceitePolitica": true, "versaoPolitica": 1 }'
curl -s -i -X POST localhost:8080/pagamentos/simulacao/<cobrancaId>/confirmar -H "Authorization: Bearer <token de outro cliente>"
curl -s -i -X POST localhost:8080/pagamentos/simulacao/<cobrancaId>/confirmar -H "Authorization: Bearer <accessToken do dono>"
curl -s localhost:8080/me/agendamentos -H "Authorization: Bearer <accessToken do dono>"
```

Saída real (17/11/2026, 13h31):

```
201  {"id":"dfab5416-…","status":"PENDENTE","metodo":"CARTAO_CREDITO","valorCentavos":5500,
      "cartao":{"urlCheckout":"http://localhost:3000/pagamento-simulado…(77 caracteres)"},"cpfMascarado":"***.***.***-09"}
404  RECURSO_NAO_ENCONTRADO   (outro cliente tentando confirmar a cobrança alheia)
200  {"id":"dfab5416-…","status":"PAGA", ...}   (o dono)
200  total=1 itens=[('2026-12-01T11:00:00-03:00', 'CONFIRMADO')]
```

**O que prova:** a API nunca vê o número do cartão, só devolve a `urlCheckout` onde ele seria digitado. Quem não é dono da reserva recebe `404` (não `403`), e depois do pagamento a reserva aparece `CONFIRMADO` em "meus agendamentos".

### C13 ⏳ Fatia 6: dados e exclusão da conta

| # | O que fazer | O que deve acontecer | O que prova | |
|---|---|---|---|:-:|
| C13 | `GET /me/dados` e depois `DELETE /me` | `200` com os dados; `204`; o login seguinte falha | direitos do titular | ⏳ 6 |

## 6. Cenário do admin (o barbeiro)

O admin **não** existe no repositório: ele nasce na subida a partir de duas variáveis que você define, só quando ainda não há nenhum admin.

### AD1 ✅ Criar o primeiro admin

Pare a API e suba de novo com as variáveis (escolha seu e-mail e uma senha de teste de 10 caracteres ou mais):

```bash
cd backend
ADMIN_EMAIL="admin.local@exemplo.test" ADMIN_SENHA="troque-esta-senha-123" ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

No log aparece `Primeiro admin criado (id ...). A troca de senha é obrigatória no primeiro acesso.` (sem e-mail e sem senha). Subir de novo com **outra** `ADMIN_SENHA` não muda nada: a variável nunca sobrescreve um admin que já existe.

### AD2 ✅ Entrar e encontrar a porta fechada

Login igual ao do cliente (C3), com o e-mail e a senha do admin. Resposta real, resumida:

```text
['ADMIN', 'BARBEIRO'] deveTrocarSenha= True
```

Com esse token, `GET /admin/agenda` responde:

```text
"status":403, "code":"TROCA_DE_SENHA_OBRIGATORIA"
```

**O que prova:** quem configurou o servidor conhece a senha inicial, então ela não dá acesso ao painel. O admin é também o barbeiro (o usuário é vinculado ao cadastro do Eduardo).

### AD3 ✅ Trocar a senha e entrar de verdade

```bash
curl -i -X PUT http://localhost:8080/me/senha -H "Authorization: Bearer $ACESSO" -H "Content-Type: application/json" \
  -d '{"senhaAtual":"troque-esta-senha-123","novaSenha":"uma-senha-so-minha-456"}'
```

→ `204`. Entre de novo com a senha nova: `deveTrocarSenha` vem `false` e `/admin/agenda` responde `404` (a rota chega na Fatia 5; o importante é que deixou de ser `403`).

### AD4 em diante ⏳ Fatia 5

| # | O que fazer | O que deve acontecer | O que prova |
|---|---|---|---|
| AD4 | `GET /admin/agenda?data=...` | as reservas do dia com status | agenda do barbeiro |
| AD5 | `PATCH /admin/agendamentos/{id}/status` → `CONCLUIDO` | `200` | atendimento fechado |
| AD6 | o mesmo com `NAO_COMPARECEU` em outra reserva | `200`; a cobrança continua `PAGA` | falta retém o valor |
| AD7 | cancelar pela barbearia **sem** motivo | `422 MOTIVO_OBRIGATORIO` | só a barbearia cancela, e com motivo |
| AD8 | `POST /admin/servicos` e depois `GET /admin/auditoria` | `201` e uma linha de auditoria com quem fez | toda ação do painel é registrada |
| AD9 | mudar o almoço na jornada e repetir o V3 | os horários mudam sem deploy | regra como dado |
| AD10 | deixar uma reserva expirar, outro cliente pega o horário, e só então pagar a primeira no sandbox | `GET /admin/pagamentos?status=PAGA_APOS_EXPIRACAO` lista a pendência | pagamento tardio sem horário vira pendência |

## 7. As telas (frontend)

> ⏳ **Fatia 7.** O frontend é gerado com IA a partir do contrato e revisado pelo Kevin ([como](como-foi-construido.md)).

| # | Tela | O que fazer | O que deve acontecer | |
|---|---|---|---|:-:|
| T1 | Início | abrir `http://localhost:3000` | preto, branco e prata, logo e botão "Agendar" | ⏳ 7 |
| T2 | Agendar | escolher serviço, dia e horário | checkout com contagem de 15 min | ⏳ 7 |
| T3 | Checkout | tentar pagar sem marcar o aceite | botão desabilitado | ⏳ 7 |
| T4 | Checkout | marcar o aceite e escolher Pix / Cartão | QR Code / checkout do gateway | ⏳ 7 |
| T5 | Meus horários | abrir depois de pagar | "Confirmado", **sem** botões de cancelar ou remarcar | ⏳ 7 |
| T6 | Painel | entrar como admin / como cliente | agenda do dia / acesso negado | ⏳ 7 |
| T7 | Celular | modo mobile do navegador | legível e navegável por teclado | ⏳ 7 |

## 8. Jornada completa

> ⏳ Roda quando as Fatias 2 a 7 estiverem na `develop`.

1. Subir tudo e conferir o health (parte 0).
2. Como **visitante**, agendar um Corte Degradê (R$ 55,00), aceitar a política e pagar com **Pix** no sandbox: a tela muda sozinha para "Confirmado".
3. Como **cliente** com conta, agendar o horário seguinte e pagar com **cartão** de teste.
4. Numa terceira reserva, não pagar: em 15 min o horário volta.
5. Como **admin**, concluir um atendimento e marcar falta em outro.
6. Conferir o log da API: nenhum CPF, telefone ou CEP.

**Pronto quando:** os 6 passos passam numa base limpa (`docker compose down -v`), sem mexer no banco à mão.

---

## 9. RAG: perguntar à documentação

Ferramenta de desenvolvimento, separada da API ([como funciona](ia/rag-vetorial.md)). Precisa só de Docker e JDK 25; o modelo de embeddings (118 MB) é baixado na primeira execução e roda local, sem chave de API.

### 9.1 ✅ Subir o banco vetorial e indexar

```bash
docker compose --profile rag up -d rag-db        # PostgreSQL 18 + pgvector, porta 5434
cd ferramentas/rag
./mvnw -q spring-boot:run -Dspring-boot.run.arguments=indexar
```

Tem que terminar com (o número cresce junto com a documentação):

```text
indexados 390 trechos de 37 arquivos
```

### 9.2 ✅ Perguntar em linguagem natural

```bash
./mvnw -q spring-boot:run "-Dspring-boot.run.arguments=buscar 'o cliente pode cancelar depois de pagar?'"
```

```text
Pergunta: o cliente pode cancelar depois de pagar?

1. [0,559] docs/adr/0007-gateway-de-pagamento-sandbox.md:70 · Decisão
2. [0,544] docs/contrato/contrato-api.md:225 · 4.7 Pagamento 100% antecipado
   ... "Após o pagamento antecipado de 100%, não há cancelamento, remarcação ou estorno em caso de desistência ou falta."
3. [0,534] docs/contexto.md:89 · 3. Regras de negócio
```

**Como ler:** o número entre colchetes é a semelhança (quanto maior, mais parecido em significado); depois vêm o arquivo, a linha e o título da seção. A resposta está no 2º trecho: o RAG **recupera**, quem redige a resposta é você ou a IA lendo esses trechos.

### 9.3 ✅ Buscar um identificador

```bash
./mvnw -q spring-boot:run "-Dspring-boot.run.arguments=buscar D40"
```

```text
1. [exata] docs/planejamento/decisoes.md:74 · Fatia 1 (16/11/2026)
   ... | D40 | PostgreSQL local do `compose.yaml` na porta **5433** ...
```

**O que prova:** `[exata]` em vez de um número: identificador não vai pelo vetor, vai por busca de texto, e o trecho que **define** o `D40` vem primeiro.

### 9.4 ✅ Medir a qualidade

```bash
./mvnw -q spring-boot:run -Dspring-boot.run.arguments=avaliar
```

Última linha, na medição de 17/11/2026:

```text
acerto@3 · só vetor: 10 de 17 · híbrido: 15 de 17
```

**O que prova:** o vetor sozinho erra os 5 identificadores; a busca híbrida acerta. Acrescente perguntas suas em `ferramentas/rag/src/main/resources/avaliacao.tsv` (pergunta, TAB, pedaço do caminho esperado) e rode de novo.

Parar o banco do RAG: `docker compose --profile rag stop rag-db`.

---

## Deu erro?

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `autenticação do tipo senha falhou para o usuário "altashoras"` | a API conectou num PostgreSQL **instalado na máquina**, não no do compose | o compose usa a porta **5433** por isso; confira `docker compose ps` e se não há `POSTGRES_PORTA` trocada no ambiente |
| `Connection refused` na 5433 | o banco não subiu | `docker compose up -d` e `docker compose ps` (precisa estar `healthy`) |
| `Port 8080 was already in use` | outra API rodando | feche a outra ou suba com `-Dspring-boot.run.arguments=--server.port=8081` |
| a API não sobe: `JWT_SEGREDO ausente ou com menos de 32 bytes` | subiu sem o perfil `dev` e sem a variável | use `-Dspring-boot.run.profiles=dev` (tem um segredo só para uso local) ou defina `JWT_SEGREDO` |
| login responde `429` | 5 erros em 15 minutos para o mesmo e-mail e IP | espere o tempo do `Retry-After` ou reinicie a API (o contador fica em memória) |
| o admin não foi criado | já existe um admin no banco, ou faltou `ADMIN_EMAIL`/`ADMIN_SENHA` | `SELECT u.email FROM usuario u JOIN usuario_papel p ON p.usuario_id = u.id WHERE p.papel = 'ADMIN';` no `psql` |
| `./mvnw verify` falha com "Could not find a valid Docker environment" | Docker Desktop fechado | abra o Docker Desktop e espere ficar pronto |
| o enforcer reclama do Java | JDK diferente do 25 | aponte o `JAVA_HOME` para um JDK 25 |
| migração falha depois de você mexer no banco | banco local sujo | `docker compose down -v` e suba de novo |
| RAG: `Connection refused` na 5434 | o banco vetorial não subiu (ele só sobe com o perfil) | `docker compose --profile rag up -d rag-db` |
| RAG: `relation "vector_store" does not exist` | primeira execução foi `buscar` antes de `indexar` | rode `indexar` primeiro |
| RAG: demora na primeira vez | está baixando o modelo de 118 MB para `ferramentas/rag/.cache/` | esperar; da segunda vez em diante usa o cache |

Contrato da API (v1.2.0): `npx @redocly/cli@2.78.0 lint docs/contrato/openapi.yaml` → **0 erros** (✅ 16/11/2026).
