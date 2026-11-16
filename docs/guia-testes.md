# 🧪 Teste você mesmo, de ponta a ponta · Barbearia Altas Horas

| | |
|---|---|
| 🎯 **Para que serve** | subir o projeto do zero e testar **à mão**, sem depender de ninguém (nem de IA), cada regra que importa |
| 👥 **Para quem** | o Kevin, um recrutador, um revisor |
| 🧰 **Precisa de** | Docker Desktop · JDK 25 · Git Bash (ou qualquer terminal com `curl`) |
| 🔒 **Sem segredo** | dados **fictícios** e gateway em **sandbox**; nenhum dinheiro real |

> [!IMPORTANT]
> **✅ = dá para testar hoje**, com a saída copiada de uma execução real (16/11/2026, Fatias 1 e 2).
> **⏳ Fatia N = roteiro já escrito**, que vira ✅ quando a fatia entrar na `develop` (aí ganha o comando e a resposta reais).

## Mapa

| Parte | O que você testa | Hoje |
|---|---|:-:|
| [0. Subir](#0-subir-o-projeto) | banco, API e health | ✅ |
| [1. Testes automatizados](#1-testes-automatizados) | 69 testes com PostgreSQL real | ✅ |
| [2. O banco na mão](#2-o-banco-na-mão-as-regras-que-o-postgresql-garante) | double booking, aceite da política, método de pagamento | ✅ |
| [3. Erros padronizados](#3-erros-padronizados-rfc-9457) | `problem+json`, `traceId` | ✅ |
| [4. Cenário do visitante](#4-cenário-do-visitante-sem-conta) | ver serviços e horários (✅), agendar e pagar sem conta | ✅ V1 a V3 · ⏳ Fatia 4 |
| [5. Cenário do cliente](#5-cenário-do-cliente-com-conta) | cadastro, login, agendar, pagar com cartão | ⏳ Fatias 3 e 4 |
| [6. Cenário do admin](#6-cenário-do-admin-o-barbeiro) | agenda, concluir, marcar falta, pagamento tardio | ⏳ Fatias 3 e 5 |
| [7. As telas](#7-as-telas-frontend) | Next.js com checkout e aceite | ⏳ Fatia 7 |
| [8. Jornada completa](#8-jornada-completa) | tudo junto, numa base limpa | ⏳ Fatia 7 |
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

✅ Resultado real (Windows + Docker Desktop): **69 testes, 0 falhas, 0 pulados**, `BUILD SUCCESS`, cobertura de instruções **96%**. O relatório abre em `backend/target/site/jacoco/index.html`.

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
| `ModulithTest` | 2 | 8 módulos e fronteiras verificadas |
| `ArquiteturaTest` | 3 | ninguém lê o relógio do sistema fora do `Clock`; dinheiro nunca em `double`; o núcleo da regra não conhece framework |

Conferir que todo critério da spec tem teste: `sh scripts/conferir-spec.sh 2 --exigir` (29 de 29).

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
curl -i http://localhost:8080/rota-que-nao-existe
```

✅ Resposta real (o mesmo formato vale para todo erro da API):

```http
HTTP/1.1 404
Content-Type: application/problem+json

{"detail":"O recurso pedido não existe.","instance":"/rota-que-nao-existe","status":404,"title":"Não encontrado",
 "type":"https://github.com/k3vinrich4rd/barbearia-altas-horas/blob/main/docs/contrato/contrato-api.md#recurso_nao_encontrado",
 "code":"RECURSO_NAO_ENCONTRADO","traceId":"bf302b905aa348aabe50c5322f03f4b6","errors":[]}
```

**O que prova:** o front decide pelo `code` (estável), e o `traceId` da tela é o mesmo do log da API. Confira: procure o `traceId` da sua resposta no terminal onde a API está rodando.

```bash
curl -i -X DELETE http://localhost:8080/actuator/health     # 405 METODO_NAO_PERMITIDO, mesmo formato
```

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

### V4 em diante ⏳ Fatia 4

| # | O que fazer | O que deve acontecer | O que prova |
|---|---|---|---|
| V4 | `POST /agendamentos/guest` com serviço, horário, nome, WhatsApp e e-mail fictícios | `201`, `AGUARDANDO_PAGAMENTO`, `expiraEm` (15 min) e `tokenGuest` (mostrado uma vez) | horário travado sem pedir CPF |
| V5 | repetir o V3 | o horário reservado sumiu | reserva pendente ocupa o horário |
| V6 | `POST /pagamentos/cobrancas` **sem** `aceitePolitica` | `422 ACEITE_POLITICA_OBRIGATORIO` | ninguém paga sem aceitar |
| V7 | mesma chamada com `"metodo": "PIX"`, CPF fictício e `"aceitePolitica": true` | `201` com QR Code e copia e cola de **100%** do preço | pagamento integral; CPF só aqui |
| V8 | confirmar a cobrança no painel do **sandbox** do gateway | o webhook chega e `GET /agendamentos/guest/{id}` mostra `CONFIRMADO` | só o gateway confirma |
| V9 | fazer outra reserva e **não** pagar por 15 min | vira `EXPIRADO` e o horário volta no V3 | ninguém segura horário de graça |

## 5. Cenário do cliente (com conta)

> ⏳ **Fatia 3** (conta) e **Fatia 4** (agendamento e pagamento).

| # | O que fazer | O que deve acontecer | O que prova | |
|---|---|---|---|:-:|
| C1 | `POST /auth/cadastro` com dados fictícios | `201`; CPF **não** é pedido | cadastro mínimo (LGPD) | ⏳ 3 |
| C2 | repetir com o mesmo e-mail em MAIÚSCULAS | `409 EMAIL_JA_CADASTRADO` | e-mail único | ⏳ 3 |
| C3 | `POST /auth/login` | `200` com token de acesso | autenticação | ⏳ 3 |
| C4 | `POST /agendamentos` com o token | `201`, `AGUARDANDO_PAGAMENTO` (cliente logado **também** paga antes) | regra R6 | ⏳ 4 |
| C5 | `POST /pagamentos/cobrancas` com `"metodo": "CARTAO_CREDITO"` e aceite | `201` com `cartao.urlCheckout`; pagar com o cartão de teste do sandbox | a API nunca vê o número do cartão | ⏳ 4 |
| C6 | `GET /me/agendamentos` | a reserva `CONFIRMADO` | "meus horários" | ⏳ 4 |
| C7 | `POST /agendamentos/{id}/cancelamento` | `404` | **não existe** cancelamento pelo cliente | ⏳ 4 |
| C8 | outro cliente lê `GET /agendamentos/{id}` do primeiro | `404` (não `403`) | não confirma que o id existe | ⏳ 4 |
| C9 | `GET /admin/agenda` com token de cliente | `403 ACESSO_NEGADO` | painel só para o admin | ⏳ 3 |
| C10 | `GET /me/dados` e depois `DELETE /me` | `200` com os dados; `204`; o login seguinte falha | direitos do titular | ⏳ 6 |

## 6. Cenário do admin (o barbeiro)

> ⏳ **Fatia 3** (login do admin) e **Fatia 5** (painel). O admin é criado na subida a partir de `ADMIN_EMAIL` e `ADMIN_SENHA` do **seu** `.env`: você escolhe os valores de teste; nenhuma senha fica no repositório.

| # | O que fazer | O que deve acontecer | O que prova | |
|---|---|---|---|:-:|
| AD1 | `POST /auth/login` com o admin do seu `.env` | `200` com os papéis `ADMIN` e `BARBEIRO` | admin por variável de ambiente | ⏳ 3 |
| AD2 | errar a senha várias vezes | `429 LIMITE_EXCEDIDO` com `Retry-After` | força bruta bloqueada | ⏳ 3 |
| AD3 | `GET /admin/agenda?data=...` | as reservas do dia com status | agenda do barbeiro | ⏳ 5 |
| AD4 | `PATCH /admin/agendamentos/{id}/status` → `CONCLUIDO` | `200` | atendimento fechado | ⏳ 5 |
| AD5 | o mesmo com `NAO_COMPARECEU` em outra reserva | `200`; a cobrança continua `PAGA` | falta retém o valor | ⏳ 5 |
| AD6 | cancelar pela barbearia **sem** motivo | `422 MOTIVO_OBRIGATORIO` | só a barbearia cancela, e com motivo | ⏳ 5 |
| AD7 | `POST /admin/servicos` (novo serviço) e depois `GET /admin/auditoria` | `201` e uma linha de auditoria com quem fez | toda ação do painel é registrada | ⏳ 5 |
| AD8 | mudar o almoço na jornada e repetir o V3 | os horários mudam sem deploy | regra como dado | ⏳ 5 |
| AD9 | deixar uma reserva expirar, outro cliente pega o horário, e só então pagar a primeira no sandbox | `GET /admin/pagamentos?status=PAGA_APOS_EXPIRACAO` lista a pendência | pagamento tardio sem horário vira pendência | ⏳ 5 |

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

## Deu erro?

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `autenticação do tipo senha falhou para o usuário "altashoras"` | a API conectou num PostgreSQL **instalado na máquina**, não no do compose | o compose usa a porta **5433** por isso; confira `docker compose ps` e se não há `POSTGRES_PORTA` trocada no ambiente |
| `Connection refused` na 5433 | o banco não subiu | `docker compose up -d` e `docker compose ps` (precisa estar `healthy`) |
| `Port 8080 was already in use` | outra API rodando | feche a outra ou suba com `-Dspring-boot.run.arguments=--server.port=8081` |
| `./mvnw verify` falha com "Could not find a valid Docker environment" | Docker Desktop fechado | abra o Docker Desktop e espere ficar pronto |
| o enforcer reclama do Java | JDK diferente do 25 | aponte o `JAVA_HOME` para um JDK 25 |
| migração falha depois de você mexer no banco | banco local sujo | `docker compose down -v` e suba de novo |

Contrato da API (v1.1.2): `npx @redocly/cli@2.78.0 lint docs/contrato/openapi.yaml` → **0 erros** (✅ 16/11/2026).
