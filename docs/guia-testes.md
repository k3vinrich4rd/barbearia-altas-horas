# 🧪 Teste você mesmo, de ponta a ponta · Barbearia Altas Horas

| | |
|---|---|
| 🎯 **Para que serve** | subir o projeto do zero e testar **à mão**, sem depender de ninguém (nem de IA), cada regra que importa |
| 👥 **Para quem** | o Kevin, um recrutador, um revisor |
| 🧰 **Precisa de** | Docker Desktop · JDK 25 · Git Bash (ou qualquer terminal com `curl`) |
| 🔒 **Sem segredo** | dados **fictícios** e gateway em **sandbox**; nenhum dinheiro real |

> [!IMPORTANT]
> **✅ = dá para testar hoje**, com a saída copiada de uma execução real (Fatias 1 a 5A, 16 e 17/11/2026).
> **⏳ Fatia N = roteiro já escrito**, que vira ✅ quando a fatia entrar na `develop` (aí ganha o comando e a resposta reais).

## Mapa

| Parte | O que você testa | Hoje |
|---|---|:-:|
| [0. Subir](#0-subir-o-projeto) | banco, API e health | ✅ |
| [1. Testes automatizados](#1-testes-automatizados) | 349 testes com PostgreSQL real | ✅ |
| [2. O banco na mão](#2-o-banco-na-mão-as-regras-que-o-postgresql-garante) | double booking, aceite da política, método de pagamento | ✅ |
| [3. Erros padronizados](#3-erros-padronizados-rfc-9457) | `problem+json`, `traceId` | ✅ |
| [4. Cenário do visitante](#4-cenário-do-visitante-sem-conta) | ver serviços e horários, reservar sem conta, pagar (simulado) | ✅ V1 a V9 · ⏳ Fatia 5 |
| [5. Cenário do cliente](#5-cenário-do-cliente-com-conta) | cadastro, login, sessão, reservar, listar e pagar (simulado) | ✅ C1 a C12 · ⏳ Fatia 6 |
| [6. Cenário do admin](#6-cenário-do-admin-o-barbeiro) | entrar e trocar a senha inicial, agenda, fechar, cancelar com motivo, devolver, auditoria, serviços, jornada, folga e configuração | ✅ AD1 a AD14 (AD14: pagamento tardio, coberto por teste automatizado) · ⏳ AL1 (alcance por região, Fatia 6C: saída real a preencher no fluxo manual) |
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

✅ Resultado real (Windows + Docker Desktop): **349 testes, 0 falhas, 0 pulados** (18/11/2026, com a 6C: 331 anteriores, todos intactos, + 18 do alcance por região), `BUILD SUCCESS` em cerca de 2 min. A cobertura de instruções medida pelo JaCoCo foi de **96,6%**; o relatório (`backend/target/site/jacoco/index.html`) é regerado a cada `verify`. O relatório abre em `backend/target/site/jacoco/index.html`.

| Classe | Testes | O que prova |
|---|:-:|---|
| `ContextoSobeIT` | 1 | a API sobe no PostgreSQL real e o health responde UP |
| `ErroPadraoTest` | 3 | 404 e 405 em `problem+json` com `code` e `traceId`; `X-Trace-Id` fora do formato é descartado |
| `V1MigracaoIT` | 6 | V1 aplicada, `btree_gist`, constraint e índices; regras reais na configuração; o banco recusa `AGUARDANDO_PAGAMENTO` sem prazo, `CONFIRMADO` sem aceite e método fora de PIX/cartão |
| `SobreposicaoConstraintIT` | 6 | mesmo horário e sobreposição parcial dão `23P01`; encostadas `[)` passam; `CANCELADO` e `EXPIRADO` não ocupam |
| `ConcorrenciaReservaIT` | 3 | 12 conexões disputando o mesmo horário: **exatamente 1 vence** (repetido 3 vezes) |
| `SeedDevIT` | 5 | perfil `dev` com o Carlos, os 5 serviços, a jornada com almoço e 22 horários pela API |
| `CalculadoraDeHorariosTest` | 12 | a regra dos horários, pura: almoço, serviço de 60 min, antecedência inclusiva, folga, intervalo meio aberto |
| `DisponibilidadeIT` | 19 | a rota inteira no banco real: o que ocupa, pendente vencida, horizonte, erros 400/404/422, formato e **sem N+1** |
| `CatalogoIT` · `PoliticaPagamentoIT` · `PrecoFormatadoTest` | 3 · 1 · 6 | só ativos, só os campos do contrato, cache, política vigente, `R$ 1.234,56` |
| `CadastroIT` · `LoginIT` · `MeIT` | 9 · 5 · 2 | conta criada com senha só em hash, campo a mais recusado, mesma resposta para e-mail inexistente, token vencido pelo relógio |
| `RefreshRotativoIT` · `LogoutIT` · `TrocaDeSenhaIT` | 5 · 1 · 2 | rotação, reuso derruba a família, 8 renovações simultâneas com no máximo uma vencedora, troca de senha encerra as sessões |
| `LimiteTentativasIT` · `LimitadorDeTentativasTest` | 5 · 3 | 429 com `Retry-After` por e-mail + IP e por IP; janela deslizante |
| `AdminInicialIT` · `MatrizAcessoIT` · `SegurancaWebIT` | 5 · 3 · 3 | admin criado por variável sem sobrescrever, troca obrigatória, 401/403/404, negar por padrão, CORS e CSRF onde há cookie |
| `SemDadoPessoalNoLogIT` · `ValueObjectsTest` | 1 · 1 | nenhum e-mail, telefone, CEP, senha ou token no log |
| `CepIT` · `CepFallbackIT` · `CepDegradacaoIT` | 4 · 5 · 2 | ViaCEP com os 8 dígitos, formato inválido sem chamada externa, fallback para a BrasilAPI (timeout, 503, 429, corpo que não é JSON), 503 com os dois fora e cadastro sobrevivendo (A1 a A7, A16) |
| `CepCacheIT` · `CepRespostaInvalidaIT` · `CepLimiteIT` | 5 · 4 · 2 | cache de 7 dias e de 1 hora com relógio falso, falha nunca em cache, teto de entradas, resposta do ViaCEP que não serve vira falha, 30 por minuto por IP (A8 a A14) |
| `CadastroComCepIT` · `SemDadoPessoalNoLogCepIT` · `CepConfigTest` | 4 · 1 · 2 | bairro, cidade e uf gravados no cadastro (A15, A17 a A19), nenhum CEP no log (A20), timeouts de 1 s e 2 s e URLs só locais (A22). Todos os provedores são falsos: **nenhuma chamada de rede real** |
| `AlcanceDaReservaIT` · `AlcancePainelIT` · `AlcancePublicoIT` | 5 · 4 · 4 | região copiada do CEP do visitante (provedores falsos) e do cadastro do cliente, CEP nunca impede a reserva, `cep` de formato errado dá 400; painel com k ≥ 5 ("Outros bairros", "Outras cidades"), escopo do BARBEIRO e erros 400/401/403/404; vitrine só com nomes, 12 meses, cache de 1 hora (`TickerFalso`) e 429 na 31ª chamada (A62 a A73) |
| `RetencaoJobIT` · `SemDadoPessoalNoLogIT` (6C) | +1 · +1 | o job zera `reserva_guest.cep` e mantém a região (A74); nenhum CEP, e-mail, WhatsApp nem IP no log do roteiro do alcance (A75) |
| `ModulithTest` | 9 | 8 módulos e fronteiras verificadas (a 6C acrescentou: a agenda usa a conta só por `Contas` e o cep só pelas portas) |
| `ArquiteturaTest` | 3 | ninguém lê o relógio do sistema fora do `Clock`; dinheiro nunca em `double`; o núcleo da regra não conhece framework |

Conferir que todo critério da spec tem teste: `sh scripts/conferir-spec.sh 3 --exigir` (44 de 44; fatias 1 e 2: 10 e 29). Fatia 6: `sh scripts/conferir-spec.sh 6 --exigir` dá **75 de 75** (A1 a A75; A61a, A61b e A61c não entram na contagem do script, mas têm teste).

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
 Acabamento (Pezinho) / Sobrancelha  |          60 |           2000
 Barba Completa / Desenhada          |          60 |           3000
 Corte Clássico / Social             |          60 |           4500
 Corte Degradê / Fade / Navalhado    |          60 |           5500
 Corte Moderno / Visagismo (Tesoura) |          60 |           6000
```

**O que prova:** preço em centavos (inteiro, nunca `float`) e todos os serviços ocupando 60 min (2 slots). (durações atualizadas em 18/11; saída a reconferir no próximo fluxo manual).

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

[{"id":"6f1c2a8e-0000-4000-8000-000000000005","nome":"Acabamento (Pezinho) / Sobrancelha","duracaoMin":60,"precoCentavos":2000,"precoFormatado":"R$ 20,00"},
 {"id":"6f1c2a8e-0000-4000-8000-000000000004","nome":"Barba Completa / Desenhada","duracaoMin":60,"precoCentavos":3000,"precoFormatado":"R$ 30,00"},
 {"id":"6f1c2a8e-0000-4000-8000-000000000001","nome":"Corte Clássico / Social","duracaoMin":60,"precoCentavos":4500,"precoFormatado":"R$ 45,00"},
 {"id":"6f1c2a8e-0000-4000-8000-000000000002","nome":"Corte Degradê / Fade / Navalhado","duracaoMin":60,"precoCentavos":5500,"precoFormatado":"R$ 55,00"},
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

**O que prova:** 22 horários; depois das 11:00 vem 13:00 (às 11:30 o serviço de 60 min invadiria o almoço) e o último é 19:00 (às 19:30 passaria das 20h). Como todos os serviços duram 60 min, o Corte Degradê (`...000002`) também tem 22. (durações atualizadas em 18/11; saída a reconferir no próximo fluxo manual).

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

**O que prova:** o CPF não é pedido; a resposta não tem senha; o CEP fica guardado. A saída acima é a de quando os provedores de CEP estão fora do ar (`completo: false`, R31); com o ViaCEP respondendo o mesmo cadastro devolve bairro `Sé`, cidade `São Paulo`, uf `SP` e `completo: true` (B3).

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
{"status":"AGUARDANDO_PAGAMENTO","origem":"CLIENTE","servico":{"nome":"Corte Degradê / Fade / Navalhado","duracaoMin":60},
 "inicio":"2026-11-24T10:00:00-03:00","fim":"2026-11-24T11:00:00-03:00","precoCentavos":5500,"expiraEm":"2026-11-17T12:03:11-03:00", ...}
```

**O que prova:** quem tem conta **também** paga 100% antes (R6); o preço vem do serviço e fica congelado na reserva. (durações atualizadas em 18/11; saída a reconferir no próximo fluxo manual).

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

### C13 ✅ Fatia 6B: dados, consentimento e exclusão da conta (fluxo manual no perfil `dev`)

Os critérios A23 a A61 já são testados pelo `./mvnw verify`. Este roteiro é o conferido à mão, com a API no perfil `dev`. Use dados fictícios e preencha `<ACESSO>` e `<REFRESH>` com o que o login devolver.

```bash
curl -s http://localhost:8080/politica-privacidade                       # 200: versao 1.0, textoConsentimento, politica
curl -s -X POST http://localhost:8080/auth/cadastro -H "Content-Type: application/json" -d '{"nome":"Cliente Teste D","email":"cliente.d@exemplo.test","whatsapp":"(11) 90000-0004","senha":"Senha-de-teste-123","aceitaMarketing":true}'
curl -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{"email":"cliente.d@exemplo.test","senha":"Senha-de-teste-123"}'
curl -s http://localhost:8080/me/consentimentos -H "Authorization: Bearer <ACESSO>"
curl -s -X PUT http://localhost:8080/me/consentimentos/marketing -H "Authorization: Bearer <ACESSO>" -H "Content-Type: application/json" -d '{"aceito":false,"versaoTexto":"1.0"}'
curl -i http://localhost:8080/me/dados -H "Authorization: Bearer <ACESSO>"   # 200, Cache-Control: no-store
curl -i -X DELETE http://localhost:8080/me -H "Authorization: Bearer <ACESSO>"   # 204
curl -i -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{"email":"cliente.d@exemplo.test","senha":"Senha-de-teste-123"}'   # 401
curl -i http://localhost:8080/me -H "Authorization: Bearer <ACESSO>"        # 401 (o token antigo deixou de valer)
```

Saída real (18/11/2026, perfil `dev`, dados fictícios, resumida):

```text
GET    /politica-privacidade             -> 200 {versao:"1.0", textoConsentimento, politica, atualizadaEm}
POST   /auth/cadastro (aceitaMarketing)  -> 201
GET    /me/consentimentos                -> 200 [{finalidade:"MARKETING_CEP", aceito:true,  versaoTexto:"1.0"}]
PUT    /me/consentimentos/marketing      -> 200 {aceito:false, versaoTexto:"1.0"}
GET    /me/consentimentos                -> 200 [{finalidade:"MARKETING_CEP", aceito:false, versaoTexto:"1.0"}]
GET    /me/dados  x5                     -> 200, Cache-Control: no-store, chaves: geradoEm, conta, consentimentos, agendamentos
                                            (sem senha, token, CPF nem IP)
GET    /me/dados  (6a chamada)           -> 429 LIMITE_EXCEDIDO, Retry-After: 3599
DELETE /me (reserva futura não paga)     -> 204; a reserva ficou CANCELADO no banco
POST   /auth/login (conta excluída)      -> 401 CREDENCIAIS_INVALIDAS
GET    /me (token antigo)                -> 401 NAO_AUTENTICADO
POST   /auth/cadastro (mesmo e-mail)     -> 201
DELETE /me (reserva PAGA, CONFIRMADO)    -> 409 EXCLUSAO_BLOQUEADA_POR_RESERVA; a reserva continua CONFIRMADO
DELETE /me (admin)                       -> 403 ACESSO_NEGADO
```

No log inteiro da API (duas subidas): `grep -c` por e-mails, nome, WhatsApp (com e sem máscara), `127.0.0.1`, `::1` e `exemplo.test`: **0** em todos.

**O que deve acontecer:** a exportação traz a conta, o histórico dos consentimentos (do mais antigo para o mais novo) e só as reservas do próprio cliente, sem senha, token, IP nem CPF; depois do `204` o login e o token antigo dão `401`. Com uma reserva futura **paga**, o `DELETE /me` dá `409 EXCLUSAO_BLOQUEADA_POR_RESERVA` e nada muda. No log: `grep` de e-mail, WhatsApp, CEP e IP com 0 ocorrências.

### Rotina do visitante depois dos 90 dias ✅ (retenção)

O job `RetencaoDeDados` roda todo dia às 3h (fuso da barbearia) e loga `Retenção de dados: limpeza=reserva_guest linhas=N`. Para conferir à mão sem esperar, no perfil `dev`: crie uma reserva de visitante, mude o `fim` da reserva no banco para mais de 90 dias atrás (`UPDATE reserva SET inicio = now() - interval '91 days', fim = now() - interval '91 days' + interval '30 minutes' WHERE id = '<ID>'`, ajustando `fim_ocupacao`) e suba a API com `APP_JOBS_RETENCAO_CRON="0 * * * * *"` para rodar no próximo minuto.

Saída real (18/11/2026, perfil `dev`): reserva de visitante criada, envelhecida por SQL e API reiniciada; a retenção rodou na subida.

```text
log:    Retenção de dados: limpeza=reserva_guest linhas=1   (auditoria_ip e evento_webhook: linhas=0)
antes:  nome do visitante, whatsapp 11900000063, anonimizado_em nulo, token_hash 7c378da3…
depois: nome = 'Visitante anonimizado', whatsapp = '00000000000', anonimizado_em preenchido, token_hash 1d492a0b…
        reserva continua EXPIRADO, 0 cobranças alteradas
GET /agendamentos/guest/{id} com o token antigo -> 404
```

O que deve acontecer: `reserva_guest` fica com `nome = 'Visitante anonimizado'`, `whatsapp = '00000000000'` e `anonimizado_em` preenchido; o link do visitante (`GET /agendamentos/guest/{id}` com o token antigo) dá `404`; a reserva e a cobrança não mudam; o log traz só o nome da limpeza e a contagem.
### B3 ✅ CEP com os provedores ligados (Fatia 6A)

Precisa de internet: o perfil `dev` fala com o ViaCEP de verdade. Sem chave e sem conta.

```bash
curl -i http://localhost:8080/cep/01001-000
curl -i http://localhost:8080/cep/01001000        # mesma resposta, agora com "fonte":"CACHE"
curl -i http://localhost:8080/cep/123              # 400 CEP_INVALIDO, sem chamada externa
curl -i http://localhost:8080/cep/99999999         # 404 CEP_NAO_ENCONTRADO
curl -i -X POST http://localhost:8080/auth/cadastro -H "Content-Type: application/json"   -d '{"nome":"Cliente Teste B","email":"cliente.b@exemplo.test","whatsapp":"(11) 90000-0002","senha":"Senha-de-teste-123","cep":"01001-000"}'
```

Saída real (17/11/2026, ViaCEP de verdade, resumida):

```text
GET /cep/01001000   -> 200 {"cep":"01001000","logradouro":"Praça da Sé","bairro":"Sé","cidade":"São Paulo","uf":"SP","fonte":"VIACEP"}
GET /cep/01001000   -> 200 {... "fonte":"CACHE"}
GET /cep/01001-000  -> 200 {"cep":"01001000", ... "fonte":"CACHE"}   (a máscara é normalizada)
GET /cep/99999999   -> 404 application/problem+json  code=CEP_NAO_ENCONTRADO  traceId=...
GET /cep/123        -> 400 application/problem+json  code=CEP_INVALIDO ("O CEP deve ter 8 dígitos.")
POST /auth/cadastro (cep 01001000) -> 201 "endereco":{"cep":"01001000","bairro":"Sé","cidade":"São Paulo","uf":"SP","completo":true}
```

Limite por IP (30 por minuto): numa rajada de 31 consultas seguidas, as 26 primeiras deram `404` (CEPs válidos que não existem) e as 5 últimas `429` com `Retry-After` (ex.: `52`). As consultas de antes da rajada contaram na mesma janela; o `123` inválido não gastou chamada externa.

**O que deve acontecer:** a primeira consulta vem com `"fonte":"VIACEP"` (Praça da Sé, Sé, São Paulo, SP) e a segunda com `"fonte":"CACHE"`; o cadastro devolve `endereco.completo: true`. O log da API tem a linha `cep resolvido: sim, fonte=...` e **nenhum** CEP.

### B4 ✅ CEP com os provedores desligados (Fatia 6A)

Para simular a queda sem desligar a internet, suba a API apontando os dois provedores para uma porta fechada:

```bash
APP_CEP_VIACEP_URL=http://localhost:1 APP_CEP_BRASILAPI_URL=http://localhost:1 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
curl -i http://localhost:8080/cep/01001000        # 503 CEP_INDISPONIVEL, em application/problem+json com traceId
curl -i -X POST http://localhost:8080/auth/cadastro -H "Content-Type: application/json"   -d '{"nome":"Cliente Teste C","email":"cliente.c@exemplo.test","whatsapp":"(11) 90000-0003","senha":"Senha-de-teste-123","cep":"01001-000"}'
```

Saída real (17/11/2026, provedores apontados para `http://localhost:1/`, resumida):

```text
GET /cep/01310100 -> 503 application/problem+json  code=CEP_INDISPONIVEL ("Preencha o endereço manualmente.")  traceId=...
POST /auth/cadastro (cep 01310100) -> 201 "endereco":{"cep":"01310100","bairro":null,"cidade":null,"uf":null,"completo":false}
```

Com os provedores ligados de novo, o cadastro com `01001000` deu `201` e `completo: true`. No log: `provedor de CEP falhou: provedor=VIACEP tipo=REDE` (e `BRASILAPI`), sem nenhum CEP, e-mail ou nome. `grep -c` por CEP e por e-mail de teste no log inteiro: 0.

**O que deve acontecer:** a consulta dá `503` e o cadastro dá `201` com `endereco.completo: false` e bairro, cidade e uf nulos (R31). Quando os provedores voltam, a falha **não** ficou em cache: a mesma consulta já responde `200`. O log tem `provedor de CEP falhou: provedor=... tipo=REDE` e nenhum CEP. Limite: a 31ª consulta do mesmo IP no mesmo minuto dá `429` com `Retry-After`.

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

### AD4 ✅ Ver a agenda do dia

Com o token do admin (AD3) e três reservas pagas na quarta 02/12 (visitante às 10h, cliente A às 11h, cliente B às 14h, como no V8/C12):

```bash
curl -s "localhost:8080/admin/agenda?data=2026-12-02" -H "Authorization: Bearer $ADMIN"
curl -s "localhost:8080/admin/agenda?data=2026-12-02&visao=SEMANA" -H "Authorization: Bearer $ADMIN"
curl -s -i "localhost:8080/admin/agenda" -H "Authorization: Bearer $ADMIN"
```

Saída real (17/11/2026, 16h31), resumida:

```text
200  3 itens: 10:00 CONFIRMADO version 2 cliente {"nome":"Visitante Teste","whatsapp":"11900000002"} cobrancaStatus PAGA
              11:00 CONFIRMADO ... "Cliente Teste A" ... PAGA · 14:00 CONFIRMADO ... "Cliente Teste B" ... PAGA
200  visao=SEMANA: 5 itens (as 2 de 01/12 mais as 3 de 02/12)
400  VALIDACAO_INVALIDA   (sem o parâmetro data)
```

**O que prova:** o dia vem na ordem do relógio, com o nome e o WhatsApp para o Eduardo avisar o cliente e o status da cobrança; **não** vem e-mail, token nem CPF. A visão `SEMANA` vai de segunda a domingo.

### AD5 ✅ Fechar o atendimento (a regra do relógio)

```bash
curl -s -i -X PATCH localhost:8080/admin/agendamentos/<id>/status -H "Authorization: Bearer $ADMIN"   -H "Content-Type: application/json" -d '{"status":"CONCLUIDO","version":2}'
```

Saída real (17/11/2026, 16h31; o relógio do `dev` é o real, e as reservas são de 02/12):

```text
422  TRANSICAO_INVALIDA  "O atendimento só pode ser fechado a partir do horário de início."
```

**O que prova:** não dá para marcar `CONCLUIDO` nem `NAO_COMPARECEU` antes de a hora chegar (critério A12; a reserva e o `version` ficam como estavam). Para ver o `200`, rode o mesmo comando em uma reserva cujo horário já passou.

### AD6 ✅ Falta retém o valor

O mesmo `PATCH` com `{"status":"NAO_COMPARECEU","version":2}` na reserva das 10h:

```text
422  TRANSICAO_INVALIDA  (mesma regra: a reserva ainda é do futuro)
```

Com a hora já passada, a resposta é `200` e a cobrança **continua `PAGA`** (o valor fica com a barbearia, R19); isso é provado pelo teste `FechamentoAtendimentoIT` (A11).

### AD7 ✅ Cancelar pela barbearia: sem motivo e com motivo

```bash
curl -s -i -X PATCH localhost:8080/admin/agendamentos/<id>/status -H "Authorization: Bearer $ADMIN"   -H "Content-Type: application/json" -d '{"status":"CANCELADO","version":2}'
curl -s -i -X PATCH localhost:8080/admin/agendamentos/<id>/status -H "Authorization: Bearer $ADMIN"   -H "Content-Type: application/json" -d '{"status":"CANCELADO","motivo":"Barbeiro com imprevisto de saude","version":2}'
```

Saída real (17/11/2026, 16h31), na reserva paga da cliente A (11h):

```text
422  MOTIVO_OBRIGATORIO   (sem motivo)
422  MOTIVO_OBRIGATORIO   (motivo só com espaços)
409  CONFLITO_DE_VERSAO   (version desatualizada)
200  {"status":"CANCELADO","version":3,"motivoCancelamento":"Barbeiro com imprevisto de saude", ...}
```

**O que prova:** só a barbearia cancela, e sempre com motivo; o cliente vê o motivo em `GET /me/agendamentos`. A resposta do `PATCH` ainda mostra `cobrancaStatus: PAGA`, porque a cobrança é tratada logo depois, por um evento (próximo passo).

### AD8 ✅ Pendência de devolução e devolver

```bash
curl -s "localhost:8080/admin/pagamentos" -H "Authorization: Bearer $ADMIN"
curl -s -i -X POST localhost:8080/admin/pagamentos/<cobrancaId>/devolucao -H "Authorization: Bearer $ADMIN"
curl -s -i -X POST localhost:8080/admin/pagamentos/<cobrancaId>/devolucao -H "Authorization: Bearer $ADMIN"
curl -s "localhost:8080/admin/pagamentos?status=DEVOLVIDA" -H "Authorization: Bearer $ADMIN"
```

Saída real (17/11/2026, 16h31):

```text
200  total=1  {"status":"DEVOLUCAO_PENDENTE","valorCentavos":5500,
              "reserva":{"inicio":"2026-12-02T11:00:00-03:00","servico":"Corte Degradê / Fade / Navalhado",
                         "statusReserva":"CANCELADO","motivoCancelamento":"Barbeiro com imprevisto de saude"},
              "contato":{"nome":"Cliente Teste A","whatsapp":"11900000002"}}
200  {"status":"DEVOLVIDA","devolvidaEm":"2026-11-17T16:31:08-03:00"}
422  TRANSICAO_INVALIDA   (devolver de novo)
200  total=0 pendências · ?status=DEVOLVIDA lista a de R$ 55,00
400  VALIDACAO_INVALIDA   (?status=XYZ)
```

**O que prova:** cancelar uma reserva paga cria sozinho a pendência `DEVOLUCAO_PENDENTE`, com o WhatsApp de quem pagou para o Eduardo fazer o Pix de volta por fora. Marcar como devolvida é uma vez só, e a pendência some. Não aparece Pix, cartão, CPF nem e-mail.

### AD9 ✅ Auditoria: quem fez o quê

```bash
curl -s "localhost:8080/admin/auditoria" -H "Authorization: Bearer $ADMIN"
curl -s -i "localhost:8080/admin/auditoria?tamanho=51" -H "Authorization: Bearer $ADMIN"
```

Saída real (17/11/2026, 16h31):

```text
200  total=2 pagina=0 tamanho=20
     DEVOLUCAO_REGISTRADA   cobranca     16:31:08  detalhe {"para":"DEVOLVIDA","reservaId":"2dc16414-…"}
     AGENDAMENTO_CANCELADO  agendamento  16:31:06  detalhe {"de":"CONFIRMADO","para":"CANCELADO"}
400  VALIDACAO_INVALIDA   (tamanho acima de 50)
```

**O que prova:** cada ação do painel vira uma linha (as recusadas, `422`/`409`, não deixam rastro), com o `atorId` e o horário, e o `detalhe` guarda só campos técnicos: **o motivo, o nome e o WhatsApp não estão lá**. O mesmo vale para o log: depois de todo o fluxo, `grep` por CPF, e-mail, WhatsApp, token e o texto do motivo no log da API deu **0** ocorrências.

### AD9b ✅ Quem não é admin

```bash
curl -s -i "localhost:8080/admin/agenda?data=2026-12-02" -H "Authorization: Bearer <accessToken de um cliente>"
curl -s -i "localhost:8080/admin/agenda?data=2026-12-02"
```

```text
403  ACESSO_NEGADO     (cliente comum: agenda, pagamentos, auditoria e PATCH)
401  NAO_AUTENTICADO   (sem token)
```

### AD10 ✅ Serviços: criar, repetir o nome, editar e desativar

```bash
curl -s -i -X POST localhost:8080/admin/servicos -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d '{"nome":"Hidratação Teste","duracaoMin":30,"precoCentavos":3500}'
curl -s -i -X POST localhost:8080/admin/servicos -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d '{"nome":"hidratação teste","duracaoMin":30,"precoCentavos":3500}'
curl -s -i -X PUT localhost:8080/admin/servicos/<id> -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d '{"nome":"Hidratação Teste","duracaoMin":30,"precoCentavos":4000,"ativo":false,"version":1}'
curl -s localhost:8080/servicos
```

Saída real (17/11/2026, 17h13), resumida:

```text
200  GET /admin/servicos: 5 serviços (todos ativos), com ativo e version
201  POST: R$ 35,00, ativo true, version 0, Location /admin/servicos/<id>; aparece em GET /servicos (6)
409  NOME_JA_EXISTE            (mesmo nome em minúsculas)
400  CAMPO_DESCONHECIDO        (campo "ativo" no corpo do POST)
200  PUT preço 4000: R$ 40,00, version 1
409  CONFLITO_DE_VERSAO        (PUT de novo com version 0)
200  PUT ativo=false: version 2; GET /servicos volta a 5 e o serviço some; GET /admin/servicos continua listando
```

**O que prova:** o Eduardo muda o cardápio sem deploy. Não existe DELETE: desativar tira o serviço do público e mantém o histórico. A `version` impede que duas telas abertas se sobrescrevam.

### AD11 ✅ Jornada: ver e recusar sobreposição

```bash
curl -s localhost:8080/admin/barbeiros -H "Authorization: Bearer $ADMIN"
curl -s -i -X PUT localhost:8080/admin/barbeiros/b0000000-0000-4000-8000-000000000001/jornada -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d '[{"diaSemana":2,"horaInicio":"08:00","horaFim":"12:00"},{"diaSemana":2,"horaInicio":"11:00","horaFim":"14:00"}]'
```

Saída real (17/11/2026, 17h13):

```text
200  1 barbeiro; jornada de terça a sábado, 07:00-12:00 e 13:00-20:00
422  JORNADA_SOBREPOSTA   (terça 08-12 e 11-14); a jornada antiga continua igual
```

**O que prova:** a jornada é dado, não código, e um intervalo sobreposto é recusado antes de gravar. A troca que dá `200` (e muda os horários) está provada em `JornadaAdminIT` (A52), para não deixar a jornada do seed alterada.

### AD12 ✅ Folga: some da disponibilidade, não passa por cima de reserva

```bash
curl -s -i -X POST localhost:8080/admin/barbeiros/b0000000-0000-4000-8000-000000000001/folgas -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d '{"inicio":"2026-12-10T14:00:00-03:00","fim":"2026-12-10T16:00:00-03:00","motivo":"Consulta"}'
curl -s "localhost:8080/disponibilidade?servicoId=6f1c2a8e-0000-4000-8000-000000000002&barbeiroId=b0000000-0000-4000-8000-000000000001&data=2026-12-10"
curl -s -i -X DELETE localhost:8080/admin/barbeiros/b0000000-0000-4000-8000-000000000001/folgas/<folgaId> -H "Authorization: Bearer $ADMIN"
```

Saída real (17/11/2026, 17h13), resumida:

```text
22 horários em 10/12 (07:00 a 19:00) antes da folga (durações atualizadas em 18/11; saída a reconferir no próximo fluxo manual)
201  folga 14:00-16:00 em 10/12 (Location da folga)
17 horários: somem 13:30, 14:00, 14:30, 15:00 e 15:30 (o serviço de 60 min não pode terminar dentro da folga); 16:00 continua
200  GET folgas: 1 folga
204  DELETE: os 22 horários voltam; DELETE de novo: 404
201  reserva do cliente em 09/12 às 15:00 (AGUARDANDO_PAGAMENTO)
422  FOLGA_COM_RESERVAS   (folga 14:00-16:00 em 09/12 por cima dela); nada gravado
```

**O que prova:** a folga tira os horários na hora, sem reiniciar nada, e o Eduardo não consegue apagar um horário que já tem cliente: antes ele cancela (com motivo) e devolve. Remover a folga devolve os horários.

### AD13 ✅ Configuração: mudar a antecedência com efeito imediato

```bash
curl -s localhost:8080/admin/configuracao -H "Authorization: Bearer $ADMIN"
curl -s -i -X PUT localhost:8080/admin/configuracao -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d '{"antecedenciaMinimaMin":180,"intervaloEntreAtendimentosMin":0,"granularidadeMin":30,"horizonteDias":30,"expiracaoPagamentoMin":15,"version":0}'
curl -s "localhost:8080/disponibilidade?servicoId=6f1c2a8e-0000-4000-8000-000000000002&barbeiroId=b0000000-0000-4000-8000-000000000001&data=<hoje>"
curl -s "localhost:8080/admin/auditoria?tamanho=10" -H "Authorization: Bearer $ADMIN"
```

Saída real (17/11/2026, 17h13), resumida:

```text
200  antecedência 120, intervalo 0, granularidade 30, horizonte 30, expiração 15, fuso America/Sao_Paulo, version 0
     hoje (17h13): 1 horário (19:30)
200  PUT antecedência 180: version 1; hoje: 0 horários, sem reiniciar a API
409  CONFLITO_DE_VERSAO   (PUT com version 0 de novo)
400  CAMPO_DESCONHECIDO   (fuso no corpo)
200  PUT de volta a 120: version 2; hoje: 1 horário (19:30)
200  auditoria: SERVICO_CRIADO, SERVICO_ALTERADO x2, FOLGA_CRIADA, FOLGA_REMOVIDA, CONFIGURACAO_ALTERADA x2
     detalhe só com nomes de campos e ids ({"campos":"antecedenciaMinimaMin"}), sem nome nem motivo
403  ACESSO_NEGADO   (cliente em /admin/servicos, /admin/barbeiros e /admin/configuracao)
401  NAO_AUTENTICADO (sem token)
```

**O que prova:** a regra de negócio é dado: mudou de 120 para 180 minutos e a disponibilidade de hoje mudou na mesma hora. Cada cadastro deixa uma linha na auditoria, e o papel `BARBEIRO` sem `ADMIN` recebe `403` (A70, `AcessoAdminIT`; o seed `dev` não tem conta só com esse papel). O roteiro completo está em `.equipe/testes/roteiro_5b.py` (saída em `roteiro_5b.out`, para a equipe). Depois de rodar, `grep` por e-mails `@exemplo.test`, WhatsApp e `g1_` no log da API deu **0**.

### AD14 ✅ Pagamento tardio (modo simulado)

| # | O que fazer | O que deve acontecer | O que prova |
|---|---|---|---|
| AD14 | deixar uma reserva expirar, outro cliente pega o horário, e só então confirmar o pagamento da primeira (no beta o gateway é simulado, D59: não há sandbox) | `GET /admin/pagamentos?status=PAGA_APOS_EXPIRACAO` lista a pendência | pagamento tardio sem horário vira pendência |

✅ **Coberto por teste automatizado:** o pagamento tardio é provado pelo A38 (horário livre: a reserva volta a `CONFIRMADO`) e pelo A39 (horário já de outro cliente: a reserva continua `EXPIRADO` e a cobrança vira `PAGA_APOS_EXPIRACAO`), ambos em `PagamentoTardioIT` (Fatia 4), e pelo A24 da Fatia 5 (`CancelamentoPagamentoTardioIT`) para a reserva cancelada.

### AL1 ✅ Alcance por região (Fatia 6C)

Os critérios A62 a A75 já têm teste automatizado (`AlcanceDaReservaIT`, `AlcancePainelIT`, `AlcancePublicoIT`, `RetencaoJobIT` e `SemDadoPessoalNoLogIT`). Este roteiro mostra o mesmo à mão, no perfil `dev`. Precisa do ADMIN com a senha já trocada (AD3) e do ViaCEP de verdade (internet) para o CEP ser resolvido.

```bash
# 1. visitante agenda com CEP (A62); sem CEP (A65); com CEP de formato errado (A64)
curl -s -X POST localhost:8080/agendamentos/guest -H "Content-Type: application/json" \
  -d '{"servicoId":"<SERVICO>","barbeiroId":"<BARBEIRO>","inicio":"2026-12-02T10:00:00-03:00","nome":"Visitante Teste","email":"visitante@exemplo.test","whatsapp":"(11) 90000-0002","cep":"01001-000"}'
curl -s -i -X POST localhost:8080/agendamentos/guest -H "Content-Type: application/json" \
  -d '{"servicoId":"<SERVICO>","barbeiroId":"<BARBEIRO>","inicio":"2026-12-02T10:30:00-03:00","nome":"Visitante Teste","email":"visitante@exemplo.test","whatsapp":"(11) 90000-0002","cep":"123"}'

# 2. a região copiada na reserva e o CEP guardado no contato do visitante
docker exec -it altas-horas-postgres psql -U altashoras -c \
  "SELECT r.regiao_bairro, r.regiao_cidade, r.regiao_uf, g.cep FROM reserva r JOIN reserva_guest g ON g.reserva_id = r.id ORDER BY r.criado_em DESC LIMIT 3"

# 3. painel (só CONFIRMADO e CONCLUIDO contam: confirme o pagamento no V9 antes) e vitrine pública
curl -s "localhost:8080/admin/alcance" -H "Authorization: Bearer $ADMIN"
curl -s "localhost:8080/admin/alcance?de=2026-11-10&ate=2026-11-01" -H "Authorization: Bearer $ADMIN"   # 400
curl -s "localhost:8080/alcance"                                                                         # sem token
```

Saída real (18/11/2026, perfil `dev`, ViaCEP de verdade, dados fictícios, resumida):

```text
POST /agendamentos/guest (cep 01001-000)   -> 201, resposta sem região nem CEP
POST /agendamentos/guest (cep 123)         -> 400 CEP_INVALIDO, nada criado
POST /agendamentos/guest (cep 99999999)    -> 201, sem região e sem CEP guardado
banco: reserva.regiao_* = Sé / São Paulo / SP e reserva_guest.cep = 01001000 (só a 1ª); a do 99999999 ficou nula
log:   "cep resolvido: sim, fonte=VIACEP" · "região resolvida: sim" / "região resolvida: não"

GET /admin/alcance (ADMIN, 01/11 a 31/12) -> 200 total 14, semRegiao 6,
      São Paulo 6 (42,9%) com bairro Sé 6 (42,9%), "Outras cidades" 2 (14,3%, sem bairros)
      (Campinas/Cambuí tinha só 2 atendimentos: k < 5, por isso agrupou)
GET /admin/alcance?de=2026-11-10&ate=2026-11-01 -> 400
GET /admin/alcance (sem token)            -> 401
GET /admin/alcance (BARBEIRO só dele)     -> 200 total 2, semRegiao 0, "Outras cidades" 2 (100,0%); com ?barbeiroId=<outro> a resposta é a mesma (continua só a dele)
GET /alcance (sem token) 1ª e 2ª chamada  -> 200 {atualizadoEm, totalCidades:1, totalBairros:1, regioes:[{São Paulo/SP, ["Sé"]}]}, sem números;
      a 2ª repete o mesmo atualizadoEm (veio do cache); Cache-Control: max-age=300, public
```

Como foi montado: os 2 primeiros atendimentos (01001-000 e 99999999) foram confirmados pelo pagamento simulado (V8/V9). A rota de visitante limita 3 reservas por IP por hora, então os outros 7 `CONCLUIDO` (5 em Sé/São Paulo e 2 em Cambuí/Campinas) foram inseridos por `INSERT ... SELECT` no banco de dev, copiando uma reserva real. O BARBEIRO sem papel de ADMIN foi criado por SQL (conta com o mesmo hash do admin, papel `BARBEIRO`) e vinculado por `POST /admin/barbeiros`; as 2 reservas de Campinas foram movidas para ele. O período padrão do painel são os últimos 30 dias, por isso os testes usaram `de` e `ate`.

No log inteiro da API (uma subida, 59 linhas): `grep -c` por `01001000`, `01001-000`, `99999999`, `123`, e-mails dos testes, nomes fictícios, WhatsApp, senha, token de visitante, `127.0.0.1` e `::1`: **0** em todos.

**O que deve acontecer:** a reserva com `cep` válido nasce `201` com a região (bairro, cidade e uf) e o CEP no contato do visitante, **sem** devolver nenhum dos dois na resposta; com os provedores fora ou um CEP que não existe, a reserva sai igual, sem região e sem CEP guardado; `cep` com formato errado dá `400 CEP_INVALIDO` e nada é criado. O painel só conta atendimentos `CONFIRMADO` e `CONCLUIDO`: bairro com menos de 5 vira "Outros bairros" e cidade com menos de 5 vira "Outras cidades" (k ≥ 5), então com poucos atendimentos de teste quase tudo aparece agrupado. O período invertido ou com mais de 366 dias dá `400`. A vitrine `GET /alcance` devolve só nomes (sem contagens) e repete a mesma resposta por 1 hora; a 31ª chamada no mesmo minuto do mesmo IP dá `429` com `Retry-After`. No log da API: `grep` por CEP, e-mail, WhatsApp e IP deve dar 0 (o log só diz "região resolvida: sim/não").

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
