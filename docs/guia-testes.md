# 🧪 Rodar e testar · Barbearia Altas Horas

> [!IMPORTANT]
> **Alvo da Fatia 1 em diante.** Cada passo marcado ⏳ vira ✅ com o comando e a resposta **reais** quando a fatia entrar na `develop`.

| | |
|---|---|
| 🎯 **Para que serve** | subir o projeto do zero e conferir, em ~15 minutos, as regras que mais importam |
| 👥 **Para quem** | recrutador, revisor ou quem for usar o projeto como template |
| 🧰 **Precisa de** | Docker Desktop · um navegador · `curl` (ou o Swagger da própria API) |
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
cp .env.example .env      # preencha só com valores de teste (os nomes estão comentados no arquivo)
docker compose up --build
```

| O que sobe | Endereço |
|---|---|
| Front (Next.js) | `http://localhost:3000` |
| API | `http://localhost:8080` |
| Swagger (perfil `dev`) | `http://localhost:8080/swagger-ui.html` |
| Health | `http://localhost:8080/actuator/health` → `{"status":"UP"}` |

O perfil `dev` cria dados fictícios: o barbeiro **Carlos Eduardo Cândido**, os 5 serviços reais com preço e a jornada de terça a sábado (07h–12h e 13h–20h).

<details>
<summary><b>Sem Docker para a API e o front (pela IDE)</b></summary>

<br/>

```bash
docker compose up -d postgres                                   # só o banco
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm install && npm run dev                       # em outra aba
```

</details>

## 2. Backend: a API sozinha

| # | O que fazer | O que deve acontecer | O que prova | |
|---|---|---|---|:-:|
| 1 | `GET /actuator/health` | `200 UP` | a API subiu e o Flyway aplicou as migrações | ⏳ |
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
cd frontend && npm test         # componentes
```

| Tipo | Ferramenta | Cobre |
|---|---|---|
| Unitário | JUnit 5 · Mockito · AssertJ | regras da agenda, valor da cobrança, máquina de estados |
| Integração | Spring Boot Test · Testcontainers (PostgreSQL) | constraint anti-sobreposição, concorrência, webhook, expiração |
| Arquitetura | ArchUnit · Spring Modulith | fronteiras entre módulos, uso do `Clock` |
| Contrato | Redocly CLI | o `openapi.yaml` sem erro de lint |

Contrato atual: `npx @redocly/cli@2.78.0 lint docs/contrato/openapi.yaml` → **0 erros** (✅ executado em 16/11/2026).
