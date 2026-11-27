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
| [1. Testes automatizados](#1-testes-automatizados) | 581 testes com PostgreSQL real | ✅ |
| [2. O banco na mão](#2-o-banco-na-mão-as-regras-que-o-postgresql-garante) | double booking, aceite da política, método de pagamento | ✅ |
| [3. Erros padronizados](#3-erros-padronizados-rfc-9457) | `problem+json`, `traceId` | ✅ |
| [4. Cenário do visitante](#4-cenário-do-visitante-sem-conta) | ver serviços e horários, reservar sem conta, pagar (simulado) | ✅ V1 a V9 · ⏳ Fatia 5 |
| [5. Cenário do cliente](#5-cenário-do-cliente-com-conta) | cadastro, login, sessão, reservar, listar e pagar (simulado) | ✅ C1 a C12 · ⏳ Fatia 6 |
| [6. Cenário do admin](#6-cenário-do-admin-o-barbeiro) | entrar e trocar a senha inicial, agenda, fechar, cancelar com motivo, devolver, auditoria, serviços, jornada, folga e configuração | ✅ AD1 a AD14 (AD14: pagamento tardio, coberto por teste automatizado) · ⏳ AL1 (alcance por região, Fatia 6C: saída real a preencher no fluxo manual) |
| [5d. Mais 1 minuto no Pix e agenda sem limites](#5d-mais-1-minuto-no-pix-e-agenda-sem-limites-fatia-14a-) | botão "Acrescentar 1 minuto" no último minuto, uma vez, e agenda sem antecedência nem horizonte | ✅ Fatia 14A |
| [5e. Senha, voz da barbearia, agenda nova e fluidez](#5e-senha-voz-da-barbearia-agenda-nova-e-fluidez-fatia-14b-14c-e-14d-) | olhinho na senha, mensagens novas, Trajetória, calendário do mês e grade do dia no painel, e as 8 melhorias de fluidez | ✅ Fatias 14B, 14C e 14D |
| [5b. Deploy grátis](#5b-deploy-grátis-a-imagem-docker-e-o-backup-fatia-8-) | imagem Docker no perfil prod, IP real, CORS, hibernação, backup e restauração | ✅ A1 a A22 (ativação: só com o OK do Kevin) |
| [6D. WhatsApp, reserva manual e prazo de 3 min](#d1--fatia-6d-whatsapp-reserva-manual-e-prazo-de-3-min) | WhatsApp do barbeiro, reserva manual pelo painel e expiração em 3 min | ✅ D1 |
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

Deixe esse terminal aberto (a API fica rodando) e abra **outro** para os testes. O perfil `dev` não precisa de `.env` e cria dados fictícios: o barbeiro **Eduardo Candido**, os 5 serviços reais e a jornada de terça a sábado (07h–12h e 13h–20h).

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

✅ Resultado real mais recente (Windows + Docker Desktop, 24/11/2026, Fatias 14B, 14C e 14D): **588 testes, 0 falhas, 0 erros, 0 pulados**, cobertura de instruções 95% (JaCoCo), `BUILD SUCCESS` (ver [5e](#5e-senha-voz-da-barbearia-agenda-nova-e-fluidez-fatia-14b-14c-e-14d-)).

Resultado anterior (Linux + Docker, 20/11/2026): **387 testes, 0 falhas, 0 pulados** (322 em classes `*IT` e 65 de unidade; os 385 anteriores, todos mantidos, + 2 do `ContratoExemplosDeErroTest`, que confere se cada exemplo de erro do `openapi.yaml` usa um `code` do `CodigoErro` com o mesmo status e título, e se o enum do contrato é igual ao do Java), `BUILD SUCCESS`.

Resultado anterior (Windows + Docker Desktop): **384 testes, 0 falhas, 0 pulados** (18/11/2026, com a 6D: 356 anteriores (349 mais 7 da Fatia 8), todos mantidos, + 28 novos; os que dependiam do prazo de 15 minutos mudaram só de valor, ver D129), `BUILD SUCCESS` em cerca de 2 min. A cobertura de instruções medida pelo JaCoCo foi de **96,6%**; o relatório (`backend/target/site/jacoco/index.html`) é regerado a cada `verify`. O relatório abre em `backend/target/site/jacoco/index.html`.

| Classe | Testes | O que prova |
|---|:-:|---|
| `ContextoSobeIT` | 1 | a API sobe no PostgreSQL real e o health responde UP |
| `ErroPadraoTest` | 3 | 404 e 405 em `problem+json` com `code` e `traceId`; `X-Trace-Id` fora do formato é descartado |
| `V1MigracaoIT` | 6 | V1 aplicada, `btree_gist`, constraint e índices; regras reais na configuração; o banco recusa `AGUARDANDO_PAGAMENTO` sem prazo, `CONFIRMADO` sem aceite e método fora de PIX/cartão |
| `SobreposicaoConstraintIT` | 6 | mesmo horário e sobreposição parcial dão `23P01`; encostadas `[)` passam; `CANCELADO` e `EXPIRADO` não ocupam |
| `ConcorrenciaReservaIT` | 3 | 12 conexões disputando o mesmo horário: **exatamente 1 vence** (repetido 3 vezes) |
| `SeedDevIT` | 5 | perfil `dev` com o Carlos, os 5 serviços, a jornada com almoço e 22 horários pela API |
| `CalculadoraDeHorariosTest` | 12 | a regra dos horários, pura: almoço, serviço de 60 min, início depois de agora (sem antecedência, D197), folga, intervalo meio aberto |
| `DisponibilidadeIT` | 19 | a rota inteira no banco real: o que ocupa, pendente vencida, sem horizonte (D197), erros 400/404/422, formato e **sem N+1** |
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
 "barbeiros":[{"barbeiroId":"b0000000-0000-4000-8000-000000000001","nomeExibicao":"Eduardo Candido",
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

**O que prova:** o horário fica travado por **3 minutos** (R7; eram 15 até a 6D) esperando o pagamento de 100%. O `tokenGuest` aparece **só nesta resposta**; o banco guarda apenas o SHA-256 dele. A 4ª reserva sem conta do mesmo IP em uma hora recebe `429 LIMITE_EXCEDIDO` com `Retry-After`.

### V5 ✅ Acompanhar a reserva pelo token

| Chamada | Saída real |
|---|---|
| `GET /agendamentos/guest/{id}` com `X-Guest-Token: <token do V4>` | `200` `{"status":"AGUARDANDO_PAGAMENTO","inicio":"2026-11-24T10:30:00-03:00", ...}` |
| o mesmo com `X-Guest-Token: g1_errado` | `404 RECURSO_NAO_ENCONTRADO` |
| sem o cabeçalho | `404` |

**O que prova:** token errado responde igual a "não existe": a API não confirma que a reserva existe para quem não tem o token.

### V6 ✅ O horário sumiu da agenda

Repita o V3 para a mesma data. Saída real, entre 9h e 11h30: `['09:00', '09:30', '11:00', '11:30']`. As 10h (reservada pelo cliente do C8) e as 10h30 (do V4) saíram. Se ninguém pagar em 3 minutos, o job de expiração libera os dois de novo.

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

**O que prova:** a confirmação passa pelo **mesmo** fluxo do webhook (evento gravado, gateway reconsultado, reserva confirmada). Prova também a porta de entrada real: `POST /pagamentos/webhook` sem o cabeçalho `asaas-access-token` responde `401 WEBHOOK_NAO_AUTENTICADO`. No modo real (`asaas`) a rota de simulação deixa de existir. No front, o `urlCheckout` do cartão abre `/pagamento-simulado/{cobrancaId}` (D149, A63): a tela só confirma a simulação e não pede número de cartão.

### V9b ✅ Cartão simulado: a tela `/pagamento-simulado/{cobrancaId}` (A63, D149)

Com a API (perfil `dev`), o PostgreSQL do compose e o front (`npm run dev`) no ar, pelo BFF do front (`localhost:3000/api`, com `Origin: http://localhost:3000`):

1. `POST /api/agendamentos/guest` cria a reserva do visitante (o BFF grava o cookie `ah_visitante`).
2. `POST /api/pagamentos/cobrancas` com `"metodo": "CARTAO_CREDITO"`, aceite e CPF de teste devolve `cartao.urlCheckout`.
3. Abra o `urlCheckout` (`/pagamento-simulado/{cobrancaId}`) e toque em **Ver a tela de confirmação (simula o pagamento aprovado)**.
4. A tela chama `POST /api/pagamentos/simulacao/{cobrancaId}/confirmar` e vai para `/agendar/confirmacao/{reservaId}`.

Fora do navegador o cookie `Secure` não volta por `http`, então o roteiro manda o mesmo token no cabeçalho `X-Guest-Token`, que o BFF também aceita. No navegador quem leva o token é o cookie, como no E2E.

```
Saida real (20/11/2026, 10h58, perfil dev, PostgreSQL do compose na 5433, front em next dev na 3000):
1. POST /api/agendamentos/guest                         -> 201  reserva a7d7e195... status AGUARDANDO_PAGAMENTO inicio 2026-11-21T13:00:00-03:00; Set-Cookie ah_visitante HttpOnly Secure: True
2. POST /api/pagamentos/cobrancas (CARTAO_CREDITO)      -> 201  cobranca 54da1e84... status PENDENTE valorCentavos 2000 urlCheckout http://localhost:3000/pagamento-simulado/{cobrancaId}
3. GET  /pagamento-simulado/{cobrancaId} (front)        -> 200  h1 "Pagamento com cartão"; campo de cartão no HTML: False
4. POST /api/pagamentos/simulacao/{id}/confirmar SEM cookie -> 401  code NAO_AUTENTICADO
5. POST /api/pagamentos/simulacao/{id}/confirmar (dono) -> 200  status PAGA reservaId a7d7e195...
6. GET  /agendar/confirmacao/{reservaId} (front)        -> 200
7. GET  /api/agendamentos/guest/{id} (token)            -> 200  status CONFIRMADO
8. repetir a confirmação (idempotente)                  -> 200  status PAGA
9. POST .../simulacao/{id inexistente}/confirmar        -> 404  code RECURSO_NAO_ENCONTRADO
```

**O que prova:** o `urlCheckout` do `GatewaySimulado` abre uma tela que existe (`200`), sem nenhum campo de cartão. Só o dono da reserva confirma: sem o cookie, a resposta é `401 NAO_AUTENTICADO`, e a tela mostra o erro sem redirecionar. Com o cookie, a cobrança vira `PAGA` e a reserva `CONFIRMADO`. Repetir é inofensivo, e uma cobrança que não existe dá `404`. Testes automáticos: `frontend/src/telas/pagamento-simulado.test.tsx` (3 casos A63: 200, 404 e 401 sem cookie) e `frontend/e2e/e2e-cartao-simulado.spec.ts` (Playwright contra a API real).

### V10 ✅ Não pagar em 3 minutos

Faça outra reserva e **não** pague: em 3 minutos ela vira `EXPIRADO` e o horário volta no V3. A expiração já foi provada na Fatia 4A (job a cada minuto) e a saída real do prazo de 3 minutos está no [D1](#d1--fatia-6d-whatsapp-reserva-manual-e-prazo-de-3-min).

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

## 5b. Deploy grátis: a imagem Docker e o backup (Fatia 8) ✅

Nada aqui cria conta nem serviço: tudo roda na sua máquina, com o Docker Desktop aberto e **variáveis falsas**. O passo a passo para ligar de verdade (só com o OK do Kevin) está em [`ativar-deploy.md`](planejamento/ativar-deploy.md); os critérios, na [spec da Fatia 8](sdd/fatia-8-deploy.md).

```bash
sh scripts/testar-imagem.sh      # docker build + PostgreSQL 18 + API no perfil prod (A1 a A15); ~2 min na 1ª vez
sh scripts/testar-backup.sh      # pg_dump + gpg + restauração num banco novo (A20)
cd backend && ./mvnw -Dtest='IpRealNoPerfilProdIT,ArquivosDeImplantacaoTest' test   # A16 a A19, A21 e A22
```

Saída real de 18/11/2026 (Windows 10, Docker 28.3.2; a imagem final tem ~435 MB):

```text
  ✅ A1  docker build terminou
  ✅ A2  imagem final só tem JRE (sem javac nem Maven)
  ✅ A3  processo roda sem root
  ✅ A4  imagem sem segredo e sem .env (só o app.jar)
  ✅ A5  health UP em 22s, na porta da variável PORT=10000 (cold start da imagem)
  ✅ A6  health público, UP e sem detalhes (sem components): {"groups":["liveness","readiness"],"status":"UP"}
  ✅ A7  Flyway aplicou as 6 migrações na subida (e nenhuma do seed dev)
  ✅ A8  btree_gist e a constraint anti double booking existem
  ✅ A9a perfil ativo: prod        ✅ A9b banco sem seed fictício
  ✅ A10 a retenção de dados rodou na subida
  ✅ A11 CORS: origem do front aceita, outra origem recebe 403
  ✅ A12 limite por IP usa o X-Forwarded-For (31ª consulta do IP .77 = 429; o IP .88 = 200)
  ✅ A13a desligamento limpo no SIGTERM (código 143)
  ✅ A13b religou do zero e voltou a UP em 18s (cold start depois da hibernação)
      memória em uso depois da subida: 279.7MiB (limite do container: 512 MiB)
  ✅ A14 container com --memory=512m segue vivo e UP
  ✅ A15 sem DB_URL a API não sobe (código 1)
Imagem OK: todos os critérios de A1 a A15 passaram.

  ✅ A20a pg_dump -Fc gerou 48602 bytes
  ✅ A20b arquivo criptografado sem o cabeçalho PGDMP legível
  ✅ A20c senha errada não descriptografa
  ✅ A20d restaurado num banco novo: dado, btree_gist e a constraint anti double booking voltaram
Backup e restauração OK (A20).
```

**Depois do deploy real** (só o Kevin, depois do guia): `GET https://SEU-SERVICO.onrender.com/actuator/health` deve dar `UP`; para conferir o IP real no ar, faça 31 consultas seguidas a `/alcance` e veja o `429` (a 31ª). Se um cabeçalho `X-Forwarded-For` inventado por você escapar do limite, é o risco D-2 da spec.

## 5c. Deploy robusto: cache AOT, health, queda do banco e ping (Fatia 8B) ✅

Tudo roda na sua máquina, com o Docker Desktop aberto e **variáveis falsas** (nenhuma conta nem serviço). Critérios na [spec da Fatia 8B](sdd/fatia-8b-deploy-robusto.md); decisões D179 a D187.

```bash
sh scripts/testar-imagem.sh                      # build + PostgreSQL 18 + API: A1 a A15 (Fatia 8) e 8B-A5 a 8B-A18
MEDIR_ANTES=1 CPU_LIMITADA=1 sh scripts/testar-imagem.sh   # com a medição de cold start (~15 min) e a imagem da Fatia 8 para comparar
sh scripts/horas-render.sh --conferir            # A19 e A20: conta de horas do ping (≤ 300 h/mês)
sh scripts/verificar-no-ar.sh --local            # A26: prova o próprio script contra a imagem e um front falso
sh scripts/verificar-no-ar.sh                    # no ar de verdade (só GET e OPTIONS, sem credencial)
sh scripts/testar-backup.sh                      # A20 da Fatia 8 (dump sintético)
cd backend && ./mvnw verify                      # 555 testes; os da 8B: HealthProbesIT, PoolProdIT, BancoForaIT, ArquivosDeImplantacao8bTest
sh scripts/conferir-spec.sh 8b --exigir --exceto 3-12,14,15,18-21,23,26-31   # só os critérios de teste Java (ver abaixo)
```

### Saída real (24/11/2026, depois do merge com a develop de V1 a V13; Windows 10, Docker Desktop 28.3.2 com 2 CPUs e 3 GiB)

```text
  ✅ 8B-A5   jar extraído (-Djarmode=tools) em /app/app e cache AOT gerado no build (126 MiB), sem banco e sem segredo
  ✅ 8B-A6   o cache AOT foi aceito e usado: 18650 de 22017 classes vieram do arquivo de cache
  ✅ 8B-A7a  ENV da imagem: perfil prod e nenhuma menção a treino
  ✅ 8B-A7b  o perfil treino nunca é ativado em execução (só no docker build)
  ✅ 8B-A13-liveness   /actuator/health/liveness: 200 e {"status":"UP"} (sem components, sem detalhes)
  ✅ 8B-A13-readiness  /actuator/health/readiness: 200 e {"status":"UP"} (sem components, sem detalhes)
      memória depois de 20 requisições: 313.3MiB (limite do contêiner: 512 MiB; meta: até 400 MiB)
  ✅ 8B-A9   contêiner com --memory=512m segue vivo e UP, com 313.3MiB em uso (≤ 400 MiB)
  ✅ 8B-A14  PostgreSQL parado: liveness 200; health completo 503 {"groups":["liveness","readiness"],"status":"DOWN"} (sem URL nem usuário do banco)
  ✅ 8B-A15a banco pendurado: GET /servicos = 503 em 11,2s, problem+json BANCO_INDISPONIVEL, sem dado do banco
  ✅ 8B-A15b o mesmo processo seguiu (sem reinício) e o health completo voltou a UP em 1s
  ✅ 8B-A18  SIGTERM com requisição em curso: ela terminou com HTTP 200 e o processo saiu com 143 em 5s
  ✅ 8B-A8   cache AOT truncado (1 MB): a API subiu do mesmo jeito e ficou UP em 9s
```

### Cold start medido (A10, A11, A12): mediana de 3 subidas

Rodada completa depois do merge com a develop (13 migrações), mesma máquina, do `docker run` até o health completo `UP` (inclui o Flyway e o contêiner limitado a 512 MiB):

| Variante | Subidas | Mediana |
|---|---|---|
| **Fatia 8** (imagem antiga, sem cache, compilador completo; reconstruída com o código atual) | 13,3 s · 11,7 s · 12,1 s | **12,1 s** |
| 8B sem cache, compilador completo (`AOT_OPTS=` vazio, `TieredStopAtLevel=4`) | 9,9 s · 10,6 s · 10,7 s | 10,6 s |
| 8B **com cache**, compilador completo | 7,0 s · 7,5 s · 7,6 s | 7,5 s |
| 8B sem cache, `TieredStopAtLevel=1` | 7,9 s · 7,9 s · 7,9 s | 7,9 s |
| **8B como a imagem sobe** (cache + `TieredStopAtLevel=1`) | 5,4 s · 5,5 s · 5,6 s | **5,5 s** |

- Ganho só do cache: **28%** (meta de 25%: passou); cache + `TieredStopAtLevel=1`: 47% sobre a 8B sem nada e **55% sobre a imagem da Fatia 8** (12,1 s para 5,5 s).
- **A variação entre rodadas é grande quando a máquina está ocupada** (2 CPUs do Docker Desktop): em rodadas anteriores, antes do merge, o cache sozinho deu 9%, 20%, 23% e 35%, e o conjunto cache + C1 deu de 44% a 54% em todas. Por isso a imagem leva as duas coisas (D182). A rodada acima foi a mais limpa (as três subidas de cada variante ficaram a 0,3 s uma da outra).
- **A11, com `--cpus=0,25`** (estimativa do Render, que tem ~0,1 CPU): 27,7 s com cache e C1 contra 39,4 s sem cache (os dois com C1); numa rodada anterior, 32,2 s contra 51,0 s sem cache e sem C1. Não entra no pass/fail.
- **A12:** a imagem foi de 444 MB (Fatia 8, reconstruída) para 607 MB (**+163 MB**; o cache AOT tem **126 MiB**). A spec previa 120 MB: o limite do script passou a 200 MB (D183).
- **A9:** 313 MiB de memória depois de 20 requisições (era ~232 MiB antes do merge: a develop trouxe mais beans). Meta de 400 MiB: passou.
- Estes números são do Docker Desktop, não do Render: o primeiro deploy real e o painel do Render são a prova final.

### O que se confere à mão (roteiros do Kevin, depois do primeiro deploy com `checksPass`)

| Critério | Roteiro | Registre aqui |
|---|---|---|
| A3 | commit na `main` com check vermelho (num PR de teste) → Render → **Events** mostra "checks failed" e não publica | ⏳ |
| A4 | Vercel → Settings → Git → Production Branch = `main` | ⏳ |
| A20, A21 | aplicar [`ping-perfil.diff.md`](planejamento/ping-perfil.diff.md) no perfil; `gh workflow view` e `workflow_dispatch` com a API dormindo: verde em até 8 min | ⏳ |
| A23 | `gh workflow run "Manter banco ligado"`: verde em menos de 2 min | ⏳ |
| A27 | ensaio de rollback (Rollback no Render, que oferece só os 2 deploys anteriores; ou Manual Deploy de commit anterior) com `scripts/verificar-no-ar.sh` verde nos dois estados | ⏳ |
| A28 | ping para uma URL inválida uma vez: chega e-mail do GitHub em até 10 min | ⏳ |
| A29 a A31 | `DB_URL_BACKUP` e `BACKUP_SENHA` cadastrados, `gh workflow run "Backup do banco"`, baixar o artefato e `ARQUIVO=... sh scripts/testar-backup.sh --real` | ⏳ |

O modo `--real` foi ensaiado aqui com um dump **sintético** (as migrações do repositório num PostgreSQL 18, mais uma `flyway_schema_history` simulada, criptografado com uma senha falsa): senha errada não abre, restaura `btree_gist`, a constraint `reserva_sem_sobreposicao` e as 7 migrações. O artefato real só o Kevin tem.

### `conferir-spec.sh 8b`

O script só reconhece um critério se a coluna "Teste" da spec citar **uma classe Java** com o `A<n>:` no `@DisplayName`. Os critérios provados por script (`scripts/testar-imagem.sh`, `horas-render.sh`, `verificar-no-ar.sh`) e por roteiro manual não têm classe, então precisam do `--exceto`. Com `--exceto 3-12,14,15,18-21,23,26-31` os 10 critérios de teste Java passam (A1, A2, A13, A16, A17, A22, A24, A25, A32, A33). Os demais estão na saída real acima.

## 5d. Mais 1 minuto no Pix e agenda sem limites (Fatia 14A) ✅

**O que muda para quem usa:** (1) no **último minuto** do contador do Pix aparece o botão **"Acrescentar 1 minuto"**; ele soma 60 s ao prazo que está correndo, **uma vez por reserva**, vale para o cliente e para o visitante e não gera Pix novo. (2) A agenda **não tem mais antecedência mínima nem limite de dias à frente** (D197): a única regra é o início ser depois de agora, no fuso de São Paulo.

### Testes automatizados da 14A

| Classe | Testes | O que prova |
|---|---:|---|
| `ProrrogacaoDoPagamentoIT` | 9 | A1 (0:30 vira 1:30, `version + 1`), A2 (10:01:59 é cedo), A3 (repetir é `200` e não soma), A5 e A9 (sem carência nem segunda vez), A10 (só aguardando pagamento), A11 (alheio, inexistente, manual e token errado são `404`; sem token é `401`), A17 (a janela vem pronta na leitura) e o corpo com campo (`400`) |
| `ProrrogacaoConcorrenciaIT` | 4 | A4 (duas ao mesmo tempo: uma `201` e uma `200`), A14 (webhook dentro do prazo novo), A15 (prorrogação contra webhook, em 4 rodadas) e A16 (o job de expiração **pula** a linha travada: `SKIP LOCKED`) |
| `ProrrogacaoDoVisitanteIT` | 1 | A12: só com o `X-Guest-Token`, sem bearer |
| `ProrrogacaoCobrancaIT` | 1 | A6: a cobrança `PENDENTE` ganha o prazo novo, o Pix é o mesmo e o gateway não é chamado |
| `ProrrogacaoSemDadoPessoalIT` | 1 | A20: evento no outbox e linha de log só com o id (R30) |
| `ProrrogacaoModulosTest` | 3 | A21: a agenda não depende do pagamento; código novo no enum e no contrato; itens `deprecated` |
| `AgendaSemLimitesIT` | 3 | A7 (daqui a 5 minutos e 400 dias à frente), A8 (só o que já passou é recusado, com o relógio em 10:00:00 exato) e A19 (reserva manual) |
| `V14MigracaoIT` | 2 | A13: as duas colunas somem, as outras mantêm os valores (partindo da V13), `prorrogada_em` existe e o CHECK recusa reserva manual prorrogada |
| `ConfiguracaoSemLimitesIT` | 1 | A18: a resposta não traz os dois campos; o `PUT` de um front antigo dá `200` e eles são ignorados |
| front: `pagamento-prorrogacao.test.tsx` | 4 | A22 a A25: o botão só no último minuto, um só `POST`, foco na mensagem, 0:30 vira 1:30, `422` some com o botão |
| front: `agendar-sem-limites.test.tsx` | 3 | A26: `min` e nenhum `max` no "Outro dia", nenhum texto de antecedência ou "dias à frente", o código sem a variável de horizonte, "Procurar mais 30 dias" |
| E2E: `prorrogacao-pix.spec.ts` | 1 | A27: o fluxo inteiro com a API real (espera o último minuto de verdade, uns 2 min) |

Os testes antigos que conferiam a antecedência de 2 h e o horizonte de 30 dias **mudaram de valor** (a regra foi revogada): `CalculadoraDeHorariosTest`, `DisponibilidadeIT` (A20, A21), `ReservaClienteIT` (A4), `ReservaManualIT` (A17, A18), `ConfiguracaoAdminIT` (A64, A65, A67, A68), `AuditoriaCadastrosIT`, `V1MigracaoIT` e, no front, `agendar-horarios`, `reserva-conflitos` e `painel-configuracao`.

**Números reais (24/11/2026, Windows 10, Docker Desktop, Java 25, Node 22.16, PostgreSQL 18):**

- `cd backend && ./mvnw verify`: **581 testes, 0 falhas, 0 erros, 0 pulados**, `BUILD SUCCESS` em 6 min 9 s. Cobertura do JaCoCo: **95,7% das instruções, 80,8% dos ramos, 94,6% das linhas**. `ModulithTest`, `ArquiteturaTest` e `ContratoExemplosDeErroTest` verdes (556 da develop + os 25 novos; nenhum teste saiu, só mudaram de valor os que citavam a regra revogada).
- `npm ci` (0 vulnerabilidades), `npm run gerar:tipos` e `gerar:rotas`, `tsc --noEmit` e `eslint` sem erro, `vitest`: **75 arquivos, 181 testes verdes** (os 7 novos falhavam contra o front antigo e passam agora), `npm run build` ok.
- `npx playwright test` contra a API real no perfil `dev` (PostgreSQL 18 isolado, removido no fim; build do front com `NEXT_PUBLIC_PAGAMENTO_SIMULADO=true`): **23 de 23 verdes em 6,6 min**, e o `prorrogacao-pix.spec.ts` (A27) em 2,1 min (a espera do último minuto é real).
- `npx @redocly/cli@2.78.0 lint docs/contrato/openapi.yaml`: **0 erros e 0 avisos** (6 exceções explícitas, a nova é a do caminho `/agendamentos/{id}/prorrogacao` × `/agendamentos/guest/{id}`).
- `sh scripts/conferir-spec.sh 14 --exigir --exceto 28-62`: **27 de 27 critérios com teste** (`--ate 27` no `frontend-ci.yml`).

### Roteiro: o "Acrescentar 1 minuto" de ponta a ponta ✅

Saída real contra a API no perfil `dev` (PostgreSQL 18 isolado), 24/11/2026. Reserva de visitante para as 18:00 **de hoje**, feita às 17:41 (antes da 14A isso era `422` por falta de 2 h de antecedência):

```text
17:41:31  200  GET /disponibilidade de hoje  3 horários; primeiro 18:00, último 19:00 (agora são 17:41:31)
17:41:31  200  GET /disponibilidade de 05/01/2028 (400 dias à frente)  22 horários
17:41:31  422  GET /disponibilidade de ontem  code=ANTECEDENCIA_INSUFICIENTE detail="Este horário já passou."
17:41:32  201  POST /agendamentos/guest (hoje às 18:00, agora 17:41:32)  status=AGUARDANDO_PAGAMENTO expiraEm=17:44:32 prorrogacao={"usada":false,"segundos":60,"abreEm":"17:43:32","fechaEm":"17:44:32"}
17:41:32  422  POST /agendamentos/guest/{id}/prorrogacao logo depois de reservar  code=PRORROGACAO_FORA_DA_JANELA detail="Só dá para acrescentar tempo no último minuto."
17:41:32  404  POST com token errado  code=RECURSO_NAO_ENCONTRADO
17:41:32  400  POST com corpo { segundos: 600 }  code=CAMPO_DESCONHECIDO
17:41:32  201  POST /pagamentos/cobrancas (Pix)  status=PENDENTE pix=SIMULADO-5ef46d58-… expiraEm=17:44:32
17:41:32  ..  esperando o último minuto (abre às 17:43:32)
17:43:33  201  POST /agendamentos/guest/{id}/prorrogacao no último minuto  status=AGUARDANDO_PAGAMENTO expiraEm=17:45:32 prorrogacao={"usada":true,"segundos":60,"abreEm":"17:44:32","fechaEm":"17:45:32"}
17:43:33  200  POST de novo (repetição)  status=AGUARDANDO_PAGAMENTO expiraEm=17:45:32 prorrogacao={"usada":true,"segundos":60,"abreEm":"17:44:32","fechaEm":"17:45:32"}
17:43:33  200  GET /agendamentos/guest/{id}  status=AGUARDANDO_PAGAMENTO expiraEm=17:45:32 prorrogacao={"usada":true,"segundos":60,"abreEm":"17:44:32","fechaEm":"17:45:32"}
17:43:33  ..  esperando o fim do prazo novo (17:45:32)
17:45:33  422  POST depois do fim do prazo  code=RESERVA_EXPIRADA
17:45:33  200  POST /auth/login (admin do roteiro)  troca de senha obrigatória: true
```

**O que prova:** o último minuto é decidido pelo servidor (`abreEm` e `fechaEm` vêm prontos); pedir cedo é `422 PRORROGACAO_FORA_DA_JANELA`; no último minuto soma 60 s (17:44:32 vira 17:45:32) e repetir devolve `200` com o mesmo corpo, **sem** somar; a cobrança Pix continua a mesma; depois do fim do prazo novo não há segunda chance (`422 RESERVA_EXPIRADA`). O corpo com campo é `400 CAMPO_DESCONHECIDO` e o token errado é o mesmo `404` de "não existe".

### Roteiro: configuração e reserva manual sem antecedência ✅

```text
17:45:38  204  PUT /me/senha (troca obrigatória do admin do roteiro)
17:45:38  200  POST /auth/login com a senha nova  papeis=["ADMIN","BARBEIRO"]
17:45:38  200  GET /admin/configuracao  {"fuso":"America/Sao_Paulo","intervaloEntreAtendimentosMin":0,"granularidadeMin":30,"expiracaoPagamentoMin":3,"version":1}
17:45:38  200  PUT com os dois campos antigos (front antigo)  {"fuso":"America/Sao_Paulo","intervaloEntreAtendimentosMin":0,"granularidadeMin":30,"expiracaoPagamentoMin":3,"version":2}
17:45:38  200  PUT só com os campos que sobraram  {"fuso":"America/Sao_Paulo","intervaloEntreAtendimentosMin":0,"granularidadeMin":30,"expiracaoPagamentoMin":3,"version":3}
17:45:38  200  GET /disponibilidade de hoje depois do PUT antigo  3 horários: 18:00, 18:30, 19:00
17:45:38  201  POST /admin/agendamentos (reserva manual daqui a minutos)  status=CONFIRMADO origem=MANUAL prorrogacao=null
17:45:38  422  POST /admin/agendamentos de um horário que já passou (07:00)  code=ANTECEDENCIA_INSUFICIENTE
17:45:38  200  GET /admin/agenda de hoje  CONFIRMADO/MANUAL/prorrogacao=null
```

**O que prova:** a resposta de `/admin/configuracao` não tem mais `antecedenciaMinimaMin` nem `horizonteDias`; o `PUT` de um front antigo (com os dois) dá `200` e os dois são ignorados (a disponibilidade de hoje continua mostrando os horários de daqui a minutos); a reserva manual daqui a minutos vale e o horário que já passou é `422 ANTECEDENCIA_INSUFICIENTE`. A `prorrogacao` de uma reserva manual é `null`.

**Para o Kevin conferir à mão (celular):** com a API e o front no ar, reserve um horário, gere o Pix e espere o contador chegar a 1:00: o botão **Acrescentar 1 minuto** aparece, o contador vai de 0:30 para 1:30 e a mensagem "Pronto, mais 1 minuto no contador." recebe o foco. Com o leitor de tela ligado, o tempo novo é anunciado ("Faltam 2 minutos para pagar."). Com "Sem animação" ligado nada se move (o botão não tem animação). **Depois do deploy, apague `NEXT_PUBLIC_HORIZONTE_DIAS` no Vercel** (o código não a lê mais).

## 5e. Senha, voz da barbearia, agenda nova e fluidez (Fatia 14B, 14C e 14D) ✅

Spec: [fatia-14-experiencia-premium.md](sdd/fatia-14-experiencia-premium.md), critérios A28 a A62; decisões D199 e D200.

### Testes automatizados

| Parte | Critérios | Onde |
|---|---|---|
| 14B · olhinho na senha | A28 a A32, A33 (axe) | `campo-senha.test.tsx`, `e2e/senha-visivel.spec.ts` |
| 14B · voz e Trajetória | A34 a A39 | `mensagens-voz.test.ts`, `trajetoria-texto.test.tsx` |
| 14C · API da agenda nova | A40 a A46 | `AgendaDoMesIT`, `AgendaIsolamentoIT`, `GradeDoDiaIT`, `AgendaSemNMaisUmIT` (PostgreSQL real) |
| 14C · telas | A47 a A52, A53 (axe e teclado) | `painel-agenda-nova.test.tsx`, `painel-agenda-mes.test.tsx`, `painel-agenda-dia.test.tsx`, `e2e/painel-agenda-nova.spec.ts` |
| 14D · fluidez | A54 a A62 (A58 no E2E) | `agendar-periodos`, `agendar-proximo-livre`, `agendar-resumo-dados`, `skeleton`, `confirmacao-ics`, `confirmacao-whatsapp`, `agendar-de-novo`, `lighthouse-14`, `e2e/movimento-reduzido.spec.ts` |

Rodada real de 24/11/2026: backend `./mvnw verify` **588 testes, 0 falhas** (cobertura 95%); front **206 testes Vitest**, `tsc`, `lint` e `build` limpos; `redocly lint` válido; `scripts/conferir-spec.sh 14 --exigir` **62 de 62**.

### Na mão (perfil `dev`)

1. **Olhinho:** abra `/entrar`, digite uma senha e toque **Mostrar a senha**: o campo mostra o texto e o botão vira **Esconder a senha**. Envie com a senha visível: ela volta a ficar escondida antes do envio. Repita em `/cadastro`, `/conta/trocar-senha` (dois campos) e no convite (dois campos).
2. **Voz:** erre a senha em `/entrar`: "E-mail ou senha não conferem. Confira e tente de novo; o botão Mostrar a senha ajuda."
3. **Agenda nova:** entre como admin e abra `/painel`. Em cima, **Hoje**, **Data** e **Barbeiro**; o calendário do mês mostra a contagem de cada dia, "Folga" e "Fechado". Toque um dia: a URL vira `/painel?data=AAAA-MM-DD` e a grade mostra **Livre** (com **Marcar aqui**, que abre a reserva manual já marcada), a reserva, **Almoço** e **Folga**. No calendário, as setas movem o foco, `Home`/`End` vão ao início e ao fim da semana e `PageDown` troca o mês.
4. **Isolamento:** com a conta de um barbeiro convidado, `GET /admin/agenda/mes?mes=2026-12&barbeiroId=<id do Eduardo>` responde `404`; sem o `barbeiroId`, só aparece a agenda dele.
5. **Fluidez:** em `/agendar`, os horários vêm em **Manhã**, **Tarde** e **Noite**; **Próximo livre** marca dia e horário em um toque; em `/agendar/dados` o resumo fixo tem **Alterar**. Na confirmação, **Adicionar à minha agenda** baixa o `.ics`, **Mandar no WhatsApp** abre o `wa.me` sem destinatário e **Agendar de novo** volta para `/agendar` com o serviço e o barbeiro marcados. Com "Sem animação" ligado, nada se mexe.

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

### AD13 ✅ Configuração: regras que mudam sem deploy (sem antecedência nem horizonte desde a 14A)

```bash
curl -s localhost:8080/admin/configuracao -H "Authorization: Bearer $ADMIN"
curl -s -i -X PUT localhost:8080/admin/configuracao -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d '{"intervaloEntreAtendimentosMin":0,"granularidadeMin":30,"expiracaoPagamentoMin":3,"version":0}'
```

Saída real em [5d](#5d-mais-1-minuto-no-pix-e-agenda-sem-limites-fatia-14a-) (24/11/2026). Os campos `antecedenciaMinimaMin` e `horizonteDias` foram **revogados** (D197): a resposta não os traz e o `PUT` antigo que ainda os envia dá `200` e os ignora. O que continua valendo e mudando na hora: intervalo entre atendimentos, grade de horários (5 a 60 min) e prazo de pagamento (3 a 60 min), com `version` (`409 CONFLITO_DE_VERSAO`), `fuso` só leitura (`400 CAMPO_DESCONHECIDO`), auditoria só com nomes de campos (`ConfiguracaoAdminIT` A64 a A68, `AuditoriaCadastrosIT`) e `403` para quem não é ADMIN (A70).

### AD14 ✅ Pagamento tardio (modo simulado)

| # | O que fazer | O que deve acontecer | O que prova |
|---|---|---|---|
| AD14 | deixar uma reserva expirar, outro cliente pega o horário, e só então confirmar o pagamento da primeira (no beta o gateway é simulado, D59: não há sandbox) | `GET /admin/pagamentos?status=PAGA_APOS_EXPIRACAO` lista a pendência | pagamento tardio sem horário vira pendência |

✅ **Coberto por teste automatizado:** o pagamento tardio é provado pelo A38 (horário livre: a reserva volta a `CONFIRMADO`) e pelo A39 (horário já de outro cliente: a reserva continua `EXPIRADO` e a cobrança vira `PAGA_APOS_EXPIRACAO`), ambos em `PagamentoTardioIT` (Fatia 4), e pelo A24 da Fatia 5 (`CancelamentoPagamentoTardioIT`) para a reserva cancelada.

### AL1 ✅ Alcance por região (Fatia 6C)

> ⚠️ **Histórico (Fatia 6C).** Desde a Fatia 11 (ADR 0014, V12) o CEP **não é mais guardado**: a coluna `reserva_guest.cep` não existe, a região só é gravada com a caixinha de consentimento (`autorizaRegiao` + `versaoTextoRegiao`) e o alcance usa k = 5 com subida de nível. A consulta SQL e a saída abaixo valem só como registro do comportamento antigo; o roteiro atual é o da Fatia 11.

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

### D1 ✅ Fatia 6D: WhatsApp, reserva manual e prazo de 3 min

Fluxo manual de 18/11/2026, no perfil `dev`, contra um banco só desta rodada (`altashoras_6d`, criado dentro do PostgreSQL do compose, porta 5433: `docker exec altas-horas-postgres psql -U altashoras -d postgres -c "CREATE DATABASE altashoras_6d"`). A API subiu em outra porta para não brigar com nada que já estivesse rodando:

```bash
cd backend
POSTGRES_BANCO=altashoras_6d SERVER_PORT=8085 ADMIN_EMAIL="admin.local@exemplo.test" ADMIN_SENHA="troque-esta-senha-123" \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

O admin entrou, trocou a senha inicial (AD2 e AD3) e usou a senha nova nos passos abaixo. Todos os nomes e números são fictícios.

**1. O prazo de 3 minutos (V4 e V10).** Visitante reserva a quarta 25/11 às 10h (relógio real da máquina):

```text
hora antes:  2026-11-18T11:38:37-03:00
201 status = AGUARDANDO_PAGAMENTO | expiraEm = 2026-11-18T11:41:37-03:00
10:00 livre agora?  False
após 2min30: 10:00 livre? False
após 3min05 (11:41:42): 10:00 livre? True
status da reserva depois do job: EXPIRADO
```

**O que prova:** `expiraEm` é exatamente 3 minutos depois da criação; o horário continua ocupado até lá, volta para a disponibilidade logo depois e o job a minuto a minuto deixa a reserva `EXPIRADO`. O pagamento que chega depois disso, com o horário já de outro cliente, vira `PAGA_APOS_EXPIRACAO` e aparece em `GET /admin/pagamentos` (testes `A39` da 4B e `A6` da 6D).

**2. O WhatsApp do barbeiro.** Antes e depois do cadastro pelo admin; a rota pública não precisa de token:

```text
GET /barbeiros antes: [{"id": "b0000000-0000-4000-8000-000000000001", "nomeExibicao": "Eduardo Candido", "whatsapp": null}]
PUT admin (whatsapp "(11) 91234-5678"): 200 whatsapp = 11912345678 version = 1
GET /barbeiros (sem token): [{"id": "b0000000-0000-4000-8000-000000000001", "nomeExibicao": "Eduardo Candido", "whatsapp": "11912345678"}]
link: https://wa.me/11912345678
PUT com número inválido ("123"): 400 VALIDACAO_INVALIDA [{'message': 'deve ter de 10 a 13 dígitos', 'field': 'whatsapp'}]
```

**O que prova:** o seed dev fica sem número (desde a D152, o perfil dev já nasce com o WhatsApp comercial público gravado pela V8); o admin cadastra com máscara e o banco guarda só os dígitos; a rota pública devolve o número para o front montar o `wa.me`; formato errado dá `400` no campo certo.

**3. A reserva manual.** Admin trava a quinta 26/11 às 10h (horário livre, `10:00` na disponibilidade):

```text
POST /admin/agendamentos {servicoId, barbeiroId, inicio, nomeCliente, observacao}
201 {"status": "CONFIRMADO", "origem": "MANUAL", "inicio": "2026-11-26T10:00:00-03:00", "expiraEm": null, "cobrancaStatus": null,
     "precoCentavos": 4500, "observacao": "combinado por WhatsApp", "cliente": {"nome": "Zeferino Ficticio", "whatsapp": null}, "version": 0}
10:00 livre depois? False
mesmo horário de novo: 409 HORARIO_INDISPONIVEL
12:00 (almoço): 422 FORA_DO_EXPEDIENTE
com campo "whatsapp" no corpo: 400 CAMPO_DESCONHECIDO
agenda do dia: [{"inicio": "2026-11-26T10:00:00-03:00", "status": "CONFIRMADO", "origem": "MANUAL", "cobrancaStatus": null,
                 "cliente": {"nome": "Zeferino Ficticio", "whatsapp": null}, "observacao": "combinado por WhatsApp"}]
cancelar (ADMIN, com motivo): 200 CANCELADO cobrancaStatus = None
pendências de devolução: 0
10:00 de volta? True
```

**O que prova:** a reserva nasce confirmada, sem prazo, sem cobrança e sem WhatsApp do cliente; ocupa o horário na disponibilidade; a segunda tentativa dá `409`; o almoço continua valendo; campo fora do contrato é recusado; aparece na agenda com a origem `MANUAL`; cancelar não cria devolução e libera o horário.

**4. Nada pessoal no log.** A API ficou de pé durante todo o roteiro (107 linhas de log). `grep -c` no log inteiro, todos com resultado **0**: o nome do cliente da reserva manual (`Zeferino`), a observação (`combinado por WhatsApp`), o nome, o e-mail e o WhatsApp do visitante, o número do barbeiro (`91234`, `11912345678`), o WhatsApp que mandei no corpo recusado (`11999990000`), qualquer token (`eyJ`) e as duas senhas do admin.

Como conferir o banco: `SELECT origem, status, cliente_id, expira_em, nome_cliente FROM reserva WHERE origem = 'MANUAL';` mostra a linha sem `cliente_id` e sem `expira_em`, e `SELECT count(*) FROM cobranca WHERE reserva_id = '<id>';` dá `0`. Para ver a retenção sem esperar 90 dias, mude o `fim` da reserva para 91 dias atrás e rode a API de novo (o job de retenção roda na subida): `nome_cliente` e `observacao` ficam nulos.

## 7. As telas (frontend)

> ✅ **Fatia 7A** (base, visitante e cliente) e **7B** (painel ADMIN e BARBEIRO) prontas, aguardando revisão. O front é gerado com IA a partir do contrato e do design system do Kevin, e revisado por ele ([como](como-foi-construido.md)).

### 7.1 O que roda sozinho (com saída real)

```bash
cd frontend
npm ci
npm run lint && npx tsc --noEmit
npm test                                   # Vitest + Testing Library + MSW, um teste por critério (A1, A2...)
NEXT_PUBLIC_PAGAMENTO_SIMULADO=true npm run build
npx playwright test                        # E2E na API REAL (perfil dev + PostgreSQL) e axe nos dois temas
sh ../scripts/conferir-spec.sh 7 --exigir     # 62 de 62 (7A e 7B)
```

Saída real da 7B, de 18/11/2026 (Node 22.16, sem `legacy-peer-deps`): **`npm ci` 0 vulnerabilidades · `lint` e `tsc --noEmit` sem erros · `npm run test:cobertura`: 48 arquivos, 72 testes verdes · `npm run build` ok · `npx playwright test`: 10 de 10 verdes (visitante, cliente, painel com a troca da senha inicial, reserva manual, axe da 7A e do painel nos dois temas e em 375 × 812, teclado e A60) contra a API real e um PostgreSQL 18 só do E2E · `./mvnw verify`: 385 testes, 0 falhas, BUILD SUCCESS · `conferir-spec.sh` das fatias 1 a 6, 6d e `7 --exigir`: 10, 29, 44, 45, 71, 75, 28 e 62 critérios, todos com teste · `testar-conferir-spec.sh` e `verificar:tokens` ok · `redocly lint` do contrato 1.7.0: 0 erros (16 avisos).**

Saída real de cada comando, de 18/11/2026: **`npm ci` 0 vulnerabilidades · `lint` e `tsc --noEmit` sem erros · `npm test`: **37 arquivos, 54 testes verdes** (cobertura de linhas 84,37%) · `npm run build` ok · `npx playwright test`: **7 de 7 verdes em 1,3 min** (A54, A55 ×2, A57, A58 ×2 e A60) · `conferir-spec.sh 7 --exigir --exceto 44-53,59`: **49 de 49** · fatias 1 a 6 e 6D: 10, 29, 44, 45, 71, 75 e 28 de 100% · `./mvnw verify`: 384 testes, 0 falhas**

Saída real de 20/11/2026 (Linux, Node 22, API real no perfil `dev` + PostgreSQL 18), depois do PR #63: **`npm test`: 50 arquivos, 80 testes verdes** (entram os 5 de `home-historia.test.tsx`: linha do tempo, "Ouvir a história" com e sem `speechSynthesis`, galeria e o `<details>` das dúvidas) · **`npx playwright test`: 12 de 12 verdes em 2,1 min** (entra `e2e/home-historia.spec.ts`: linha do tempo pelo teclado, dúvidas com Enter, galeria, "Tema claro" e "Sem animação") · **`./mvnw verify`: 387 testes, 0 falhas**.

O E2E precisa do Docker aberto, do PostgreSQL (`docker compose up -d postgres`) e da API no ar (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` com os limites do E24 elevados por variável de ambiente). Para o E2E não bater nos limites de reserva (3 por hora) e de cadastro (5 por hora), suba a API com `APP_LIMITES_GUEST_MAX`, `APP_LIMITES_CADASTRO_MAX`, `APP_LIMITES_LOGIN_MAX` e `APP_LIMITES_LOGIN_IP_MAX` altos. `docker compose down -v` zera o banco.

### 7.2 O que se confere à mão (fluxo manual)

Abra `http://localhost:3000` (API em `http://localhost:8080`, perfil `dev`).

| # | Tela | O que fazer | O que deve acontecer | Saída real |
|---|---|---|---|---|
| T1 | Início | abrir `/` | preto e branco, logo, "Seu horário com o Eduardo, garantido.", serviços da API e botão "Agendar horário"; o bloco "De onde vêm nossos clientes" só aparece se a API tiver regiões | Saída real: a preencher no fluxo manual |
| T2 | Agendar | escolher serviço, dia e horário | só horários livres, no fuso da barbearia, agrupados por barbeiro | Saída real: a preencher no fluxo manual |
| T3 | Checkout | abrir o pagamento e não marcar o aceite | "Pagar" desabilitado; o contador mostra o tempo do `expiraEm` da API | Saída real: a preencher no fluxo manual |
| T4 | Checkout | aceitar, informar o CPF de teste e escolher Pix, depois cartão | QR e copia e cola; para cartão, só o botão do checkout do gateway, nenhum campo de cartão | Saída real: a preencher no fluxo manual |
| T5 | Meus agendamentos | abrir depois de pagar | "Confirmado", **sem** botões de cancelar ou remarcar | Saída real: a preencher no fluxo manual |
| T6 | Painel | entrar como `CLIENTE` e abrir `/painel` | acesso negado (o painel de verdade é da 7B) | Saída real: a preencher no fluxo manual |
| T7 | Celular | modo mobile do navegador (375 × 812) | legível e navegável só pelo teclado | Saída real: a preencher no fluxo manual |
| T8 | Servidor acordando | pare a API, abra `/` e religue | "O servidor está acordando", nova tentativa a cada 3 s, a página se completa quando a API volta | Saída real: a preencher no fluxo manual |
| T9 | Senha inicial | entrar com o admin criado por `ADMIN_EMAIL` e `ADMIN_SENHA` | a troca de senha é obrigatória antes de qualquer tela (a tela do painel entra na 7B) | Saída real: a preencher no fluxo manual |
| T10 | Código da reserva | reservar sem conta, pagar e copiar o código e o link da confirmação; abrir `/reserva` com eles | a reserva aparece "Confirmada"; o endereço perde o `#id=…&codigo=…` depois de lido | Saída real: a preencher no fluxo manual |

### 7.3 O painel (7B)

Entre em `http://localhost:3000/entrar` com o admin criado por `ADMIN_EMAIL` e `ADMIN_SENHA` (a API no perfil `dev`). Para o BARBEIRO, ligue uma conta com papel BARBEIRO a um barbeiro em Cadastros (campo "Conta de login").

| # | Tela | O que fazer | O que deve acontecer | Saída real |
|---|---|---|---|---|
| P1 | Senha inicial | entrar como admin pela primeira vez | a troca de senha é obrigatória antes de qualquer tela; depois do novo login abre `/painel` | Saída real: a preencher no fluxo manual |
| P2 | Menu | entrar como BARBEIRO e depois como ADMIN | o BARBEIRO vê Agenda, Reserva manual e Alcance; o ADMIN vê também Pendências, Cadastros e Auditoria; a URL escondida mostra "Acesso negado" | Saída real: a preencher no fluxo manual |
| P3 | Agenda | abrir `/painel` no dia de uma reserva paga | item confirmado com "PAGO" e "Pagamento: Paga"; dia e semana; filtro de barbeiro só para o ADMIN; o almoço aparece listrado com um barbeiro escolhido (ou na agenda do BARBEIRO) | Saída real: a preencher no fluxo manual |
| P4 | Fechar | marcar "Concluído" depois do horário e "Não compareceu" (com a confirmação) | o item muda de forma e de texto; antes do início a tela diz "só dá para fechar o atendimento depois do horário de início" | Saída real: a preencher no fluxo manual |
| P5 | Cancelar | como ADMIN, "Cancelar pela barbearia" com motivo | o item vira "Cancelado" com o motivo; a devolução aparece em Pendências | Saída real: a preencher no fluxo manual |
| P6 | Pendências | "Registrar devolução" e "Marcar como revisado" | a linha sai da fila; repetir mostra "já registrada" | Saída real: a preencher no fluxo manual |
| P7 | Reserva manual | travar um horário combinado por WhatsApp (com e sem nome) | a agenda do dia mostra "MANUAL · WHATSAPP", sem pagamento; o horário some de `/agendar` | Saída real: a preencher no fluxo manual |
| P8 | Cadastros | editar serviço (preço `55,90`), barbeiro (WhatsApp com máscara), jornada, folga e regras | preço vira centavos; nome repetido aparece no campo; jornada sobreposta marca o dia; fuso só leitura; prazo de 3 a 60 min | Saída real: a preencher no fluxo manual |
| P9 | Auditoria e alcance | abrir as duas telas | auditoria sem IP; alcance com "menos de 5" e sem percentual nas linhas pequenas | Saída real: a preencher no fluxo manual |

### 7.5 Site mais fluido (Fatia 12) ✅

**Passos à mão (visitante, no celular ou numa janela estreita):**

1. Abra a home: a ordem é herói (com a faixa "Escolha · Pague · Confirmado"), **Serviços** (6 cartões, o último é **Descoloração / Platinado (química), 60 min, R$ 80,00**), **Barbeiro**, **Galeria**, **Trajetória**, "De onde vêm nossos clientes" e **Dúvidas**. ✅
2. Em **Galeria** toque numa foto: abre inteira, com legenda; `←` e `→` trocam, `Esc` fecha e o foco volta à miniatura. ✅
3. Abra `/#historia`: a janela vai para a **Trajetória** e o endereço vira `/#trajetoria`. ✅
4. Role até o rodapé: a barra **Agendar horário** continua visível (só abaixo de 768 px) e não cobre o último link. Em `/agendar`, `/entrar`, `/cadastro` e `/painel` ela não existe. ✅
5. Toque em **Agendar este serviço** no platinado: `/agendar` abre com ele marcado, o próximo dia com horário já escolhido e, com um só barbeiro, sem seletor de barbeiro. Escolha um chip de horário e toque em **Continuar** (4 toques desde a home). ✅
6. O texto do pagamento diz "prazo curto", sem número: o prazo só aparece como cronômetro na tela de pagamento (vem de `expiraEm`). ✅
7. Sem clientes com região consentida, o bloco "De onde vêm nossos clientes" mostra "Em breve", sem número nem botão. ✅

**O que roda sozinho (saída real de 23/11/2026, Windows, Node 22, Java 25, PostgreSQL 18 isolado):**

- `cd backend && ./mvnw verify`: **398 testes, 0 falhas** (entram os 4 de `V10ServicoDescoloracaoPlatinadoIT`, A21; `V8CatalogoInicialIT` e `SeedDevIT` passam a esperar 6 serviços).
- `cd frontend && npm run lint && npx tsc --noEmit && npm run verificar:tokens`: limpos.
- `npm run test:cobertura`: **58 arquivos, 108 testes verdes**; cobertura 87,5% das linhas de instrução (Statements) e 83,5% dos ramos.
- `npm run build`: compila (Next 16).
- `npx playwright test` (API real no perfil `dev`, banco isolado): **20 de 20 verdes** num banco recém-criado (num banco já usado, A63 e A55 oscilaram por disputa dos mesmos horários), com axe sem nenhuma violação (A14 e A25), 4 toques (A23) e o fluxo da home ao pagamento (A22, mais o A57 de ponta a ponta).
- `bash scripts/conferir-spec.sh 12 --exigir`: **27 de 27 critérios com teste**.
- **Lighthouse móvel** (`npx lighthouse@12`, CPU 4x e Slow 4G simulados, contra `next build` + `next start` local com a API real; 3 execuções cada):

| Tela | Desempenho | Acessibilidade | LCP | CLS | TBT |
|---|---|---|---|---|---|
| `/` | 95, 97, 97 | 100 | 2,54 a 2,63 s | 0,000 a 0,003 | 55 a 143 ms |
| `/agendar` | 94, 96, 97 | 100 | 2,46 a 2,57 s | 0,002 | 106 a 197 ms |

  O **LCP** fica entre 2,46 e 2,63 s, **até 130 ms acima** do orçamento de 2,5 s em parte das execuções (o título repinta quando a fonte Playfair troca, `display: swap`, D-1 do design system). Com `display: optional` o mesmo teste deu 2,44 a 2,51 s; não foi aplicado porque muda o desenho. Antes de reservar espaço para a lista de serviços e fixar o resumo, o `/agendar` tinha CLS 0,141; hoje 0,002.
- Peso de imagens da home no celular antes de rolar (A26): 4 imagens, **53,8 KB** no total (fachada, logo, retrato e foto do barbeiro) e **nenhuma das 15 fotos novas** antes de rolar; as miniaturas da galeria só carregam perto da tela, em WebP de 256 px (6 a 16 KB cada). **Atualização (27/11/2026):** a galeria passou a ter só a foto do salão, sem pessoas; as fotos com clientes saíram do site e da versão atual do repositório, e a fachada do herói virou `fachada-sem-tv.jpg` (sem as TVs e sem reflexo).
### 7.4 Convite do barbeiro (Fatia 10)

O dono (ADMIN) gera o convite; o barbeiro digita o **próprio e-mail** e cria a senha; o convidado só mexe na própria agenda. As telas ainda **aguardam desenho** (layout neutro). Para repetir à mão: API no perfil `dev`, front em `http://localhost:3000`, o admin do `ADMIN_EMAIL`. O teste de ponta a ponta de verdade é `frontend/e2e/convite.spec.ts` (A36), que faz tudo isso contra a API real.

| # | Tela | O que fazer | O que deve acontecer | Saída real |
|---|---|---|---|---|
| C1 | Cadastros → Barbeiros | como dono, criar um barbeiro e tocar em "Gerar convite" (não pede e-mail) | diálogo com o link `/convite#…`, o QR, o prazo de 48 h, "Copiar link" e "Enviar pelo WhatsApp"; ao fechar o código some e a linha mostra "Convite pendente até …" com "Gerar outro" e "Revogar" | Saída real: a preencher no fluxo manual |
| C2 | Abrir o link | colar o link numa janela anônima | "Criar minha senha"; o código sai da barra de endereço; recarregar a página mostra o mesmo formulário (abrir não consome o convite) | Saída real: a preencher no fluxo manual |
| C3 | Criar a senha | digitar o próprio e-mail, a senha (10 a 72 caracteres) e a confirmação | vai para o login com "Senha criada. Entre com seu e-mail e a nova senha."; ao entrar, o menu mostra "Você entrou como Barbeiro" e só a própria agenda | Saída real: a preencher no fluxo manual |
| C4 | Link repetido | abrir o mesmo link de novo e tentar criar outra conta | uma frase só ("Este link não vale mais ou não pode ser usado aqui") e o caminho "Já tenho conta no site: entrar" | Saída real: a preencher no fluxo manual |
| C5 | Conta que já existe | entrar como cliente, abrir um convite novo e digitar o e-mail da conta | "Aceitar convite" (sem senha); depois, entrar de novo mostra o painel de barbeiro e a senha antiga continua valendo | Saída real: a preencher no fluxo manual |
| C6 | Dono na agenda de outro | como dono, filtrar a agenda pelo barbeiro convidado, fazer uma reserva manual e fechar e cancelar uma reserva dele | funciona, com o dono como autor na auditoria; o convidado, nos mesmos atos na agenda de outro, recebe "não encontrado" | Saída real: a preencher no fluxo manual |
| C7 | Desligar | como dono, editar o barbeiro, desmarcar "Ativo" e confirmar | a confirmação explica que mantém o histórico; a linha vira "Desligado"; a sessão do barbeiro cai na hora; reativar sozinho não devolve o acesso (só um convite novo) | Saída real: a preencher no fluxo manual |

Para o teste de visitantes, esta seção **não** entra: o público só testa o cliente (13 passos); o convite e o desligamento são da equipe.

### 7.6 Notificação ao barbeiro (Fatia 13) ✅

Quando o **pagamento confirma** uma reserva (de cliente com cadastro ou de visitante), o barbeiro daquela reserva ganha um aviso em **Painel → Novidades** e, se tiver o aparelho ativado, uma notificação no celular: *"Novo horário: Corte Degradê, sex 27/11 às 10:00"* (a spec escreveu "qui" no exemplo, mas 27/11/2026 é sexta). O aviso **não leva o nome do cliente**. Reserva que expira, cancelada ou manual não avisa. As telas **aguardam desenho** (layout neutro, D148). Só a equipe testa: o público não entra aqui.

| # | Tela | O que fazer | O que deve acontecer | Saída real |
|---|---|---|---|---|
| N1 | Painel → Notificações | como barbeiro, abrir a tela num celular Android ou no computador e tocar em **Ativar notificações** | o navegador pede a permissão **só depois do toque**; a tela passa a "Ativo" com "Enviar um teste" e "Desativar" | a preencher no teste manual (depende do aparelho) |
| N2 | Enviar um teste | tocar em **Enviar um teste** | chega "Notificações ativadas neste aparelho"; o 6º em uma hora responde `429` | a preencher no teste manual |
| N3 | iPhone | abrir pelo Safari, sem a Tela de Início | a tela mostra "No iPhone, toque em Compartilhar, depois em Adicionar à Tela de Início…"; com iOS menor que 16.4: "Atualize o iPhone…" | a preencher no iPhone real (não dá para provar em teste automático) |
| N4 | Cliente marca e paga | com a API no perfil `dev`, o cliente (ou visitante) marca com o Eduardo e confirma o pagamento simulado | o selo **Novidades** do Eduardo mostra o número; abrir a lista zera o selo e destaca os itens "Novo"; o barbeiro convidado não vê nada | `frontend/e2e/notificacao.spec.ts` (A43) |
| N5 | Voltar à aba | deixar o painel aberto, pagar uma reserva em outra aba e voltar | o selo atualiza ao voltar o foco (e na hora, se o push chegar), **sem polling** | `NovidadesDoPainel.test.tsx` (A42) |

**Ligar de verdade:** o par de chaves VAPID e o deploy estão em [ativar-deploy.md](planejamento/ativar-deploy.md#passo-2b--notificação-ao-barbeiro-chave-vapid-fatia-13). Sem as chaves a tela mostra "indisponíveis" e o aviso do painel funciona igual. O e-mail está pronto e desligado: [notificacao-por-email.md](planejamento/notificacao-por-email.md).

**Como ativar no Android e no iPhone (guia curto):** *Android (Chrome):* abra o painel, vá em Notificações, toque em Ativar e em Permitir; confira com "Enviar um teste". *iPhone (iOS 16.4 ou mais novo):* no Safari, toque em Compartilhar, depois em **Adicionar à Tela de Início**; abra o Altas Horas pelo **ícone novo** (não pelo Safari), entre no painel, Notificações, Ativar. Se negou por engano: Ajustes do aparelho, Notificações, Altas Horas.

**Números (24/11/2026, Windows, Node 22, Java 25, PostgreSQL 18 real):** `./mvnw verify`: **542 testes, 0 falhas** (depois do merge com a Fatia 11 e das correções da revisão de segurança) (eram 387 em 20/11; a Fatia 13 soma os A1 a A50 do backend, incluindo a criptografia conferida contra o exemplo da RFC 8291) · front: `eslint` e `tsc --noEmit` sem erro, `vitest`: **72 arquivos, 171 testes verdes**, `next build` ok (rotas `/manifest.webmanifest`, `/painel/novidades` e `/painel/notificacoes`) · `npx playwright test` contra a API real no perfil `dev` (banco isolado, removido no fim): **22 de 22 verdes em 3,3 min** (entra `notificacao.spec.ts`, A43) · `scripts/conferir-spec.sh 13 --exigir`: **50 de 50** · `redocly lint` 2.78.0 no contrato 1.10.0: 0 erros. O que **não** se prova em teste automático: a notificação chegando num iPhone e num Android de verdade (passos N1 a N3).
### 7.5 Alcance por nível e consentimento de região (Fatia 11)

| # | O que fazer | Esperado | Situação |
|---|---|---|---|
| 1 | Cadastro: abrir `/cadastro` | a caixinha de região **desmarcada**, o texto da API, o canal de contato e o link da política; nenhum campo de CEP | ⏳ roteiro manual |
| 2 | Marcar a caixinha, digitar o CEP 06010-000 e criar a conta | `PUT /me`/cadastro com `autorizaRegiao: true`; `GET /me` traz `regiaoAutorizada: true` e `endereco.cep` nulo | ✅ `RegiaoConsentidaIT` (A1 a A3), `PerfilSemCepIT` (A7) |
| 3 | Sem marcar a caixinha, mandar `cep` direto à API | `201`, nenhuma região gravada e nenhuma chamada ao ViaCEP | ✅ `RegiaoConsentidaIT` (A1) |
| 4 | "Meus dados" → "Retirar minha autorização de região" | confirmação em uma linha; a região some do perfil e das reservas | ✅ `RevogarRegiaoIT` (A4, A5), `MeusDados.test.tsx` (A49) |
| 5 | Visitante: confirmação da reserva → "Retirar minha autorização de região" | `DELETE /agendamentos/guest/{id}/regiao` → 204; token errado → 404 | ✅ `RevogarRegiaoGuestIT` (A6), `ConfirmacaoRegiao.test.tsx` (A48) |
| 6 | Painel (Dono): Alcance, trocar Estado, Região e Município; filtrar UF, período e barbeiro | linhas e filhos; "menos de 5" em texto; lugar pequeno sobe de nível | ✅ `AlcancePorNivelIT`, `SubidaDeNivelIT`, `PainelAlcance.test.tsx` (A51) |
| 7 | Painel (barbeiro convidado) | só os clientes dele, sem filtro de barbeiro, nunca bairro pequeno | ✅ `AlcancePorBarbeiroIT` (A37, A38), `PainelAlcance.test.tsx` (A52) |
| 8 | Home: clicar num município | bairros em "cerca de 15%", "menos de 5%" ou só o nome; nenhum número de pessoas | ✅ `VitrineEmFaixasIT`, `HomeAlcance.test.tsx` (A50) |
| 9 | Nenhuma subtração revela um número menor que k | 200 sementes, k = 3, 5 e 8; sem as defesas o teste falha | ✅ `AlcancePropriedadeTest` (A43, A44) |

Roteiro do painel com dado real: com o banco do `docker compose`, crie 6 contas em Osasco/Centro com a caixinha e uma reserva cada (ver o fluxo ponta a ponta), entre como Dono e abra "Alcance".

### 7.8 Férias e folgas no painel (Fatia 15, C13) ✅

Página própria **Painel → Férias e folgas** (`/painel/folgas`), só para o ADMIN (contrato 1.12.1, ADR 0015, E1 a E15 e E24). Em Cadastros → Barbeiros → "Jornada e folgas" fica só o atalho "Ver férias e folgas de …".

| # | O que fazer | O que deve acontecer | Onde está provado |
|---|---|---|---|
| FF-1 | abrir a página sem folgas | "Nenhuma folga de hoje em diante." e o botão **Marcar férias ou folga**; remover pede confirmação | `painel-folgas.test.tsx` |
| FF-2 | entrar como BARBEIRO | o item não aparece no menu e a URL mostra acesso negado | `painel-acesso.test.tsx` (A44) |
| FF-3 e FF-10 | escolher as datas | **antes de salvar**, o painel chama `GET …/folgas/conflitos` e mostra quem está marcado (horário, serviço, nome, origem, status) | `painel-folgas.test.tsx` e `e2e/painel-folgas.spec.ts` |
| FF-4 | tocar em **Salvar e manter os horários** | vai `manterReservas: true`; "Folga lançada. N clientes continuam marcados nesse período." com a lista e "Ver na agenda"; nenhuma reserva muda | `painel-folgas.test.tsx` e `e2e/painel-folgas.spec.ts` |
| FF-5 | **Cancelar um a um** → "Cancelar pela barbearia" com motivo | só aquela reserva é cancelada (com a versão lida na grade do dia) e a prévia passa a mostrar uma a menos | `painel-folgas.test.tsx` |
| FF-6 | "Dia inteiro" de 25/11 a 27/11, tipo Férias | vai `inicio` 25/11 00:00 e `fim` 28/11 00:00 (BRT), `tipo: FERIAS`; a tela diz "até sexta, 27/11 (o dia inteiro)" | `painel-folgas.test.tsx` |
| FF-7 | **Editar**, mudar o fim e salvar | `PUT` com `inicio`, `fim`, `motivo` e `tipo`; "Folga alterada." e a lista com o fim novo | `painel-folgas.test.tsx` |
| FF-8 | colar 201 caracteres no motivo | o campo para em 200 (contador "200/200") e lembra "Não escreva dado de cliente aqui." | `painel-folgas.test.tsx` |
| FF-9 | axe em 360×800 | 0 violações na lista e na prévia com clientes | `e2e/painel-folgas.spec.ts` |
| FF-11 | alguém marca entre a prévia e o salvar | `422 FOLGA_COM_RESERVAS`: "Há clientes marcados nesse período. Confira a lista abaixo: você pode manter essas reservas ou cancelar uma por uma antes de salvar a folga.", a lista do `conflitos[]` e as 3 saídas; nada gravado | `painel-folgas.test.tsx` e `mensagens-voz.test.ts` |
| FF-12 | uma folga do tipo FERIAS e o público em /agendar | só "Fechado", nunca "Férias" nem "Folga" | `agendar-expediente.test.tsx` (AF-10, no PR das telas públicas) |

## 8. Jornada completa

> ⏳ Roda quando as Fatias 2 a 7 estiverem na `develop`.

1. Subir tudo e conferir o health (parte 0).
2. Como **visitante**, agendar um Corte Degradê (R$ 55,00), aceitar a política e pagar com **Pix** no sandbox: a tela muda sozinha para "Confirmado".
3. Como **cliente** com conta, agendar o horário seguinte e pagar com **cartão** de teste.
4. Numa terceira reserva, não pagar: em 3 min o horário volta.
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
