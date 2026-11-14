# 🧪 Guia de testes de ponta a ponta · Barbearia Altas Horas

> [!IMPORTANT]
> **Planejado.** Este roteiro descreve o comportamento **esperado** do beta. Ainda não há código: cada passo vira real quando a fatia correspondente entrar na `develop`, e aí o guia ganha o comando testado e a resposta real copiada da execução.

**Para quem é:** quem quer conferir o sistema do zero (o Kevin, um revisor ou um recrutador). Cada passo tem **o que fazer**, **o que deve acontecer** e **por que importa**.

## Sumário
1. [Como rodar](#1-como-rodar)
2. [Quem é quem nos testes](#2-quem-é-quem-nos-testes)
3. [Backend: roteiro da API](#3-backend-roteiro-da-api)
4. [Frontend: roteiro das telas](#4-frontend-roteiro-das-telas)
5. [Juntos: jornada completa](#5-juntos-jornada-completa)
6. [Testes automatizados](#6-testes-automatizados)
7. [Checklist final](#7-checklist-final)

---

## 1. Como rodar

### Pré-requisitos
- Docker Desktop (ou Docker Engine + Compose)
- Java 25 e Node.js LTS, só se for rodar fora do Docker
- Um cliente HTTP: `curl`, Insomnia ou o Swagger da própria API

### Variáveis de ambiente
Copie `.env.example` para `.env` e preencha. **Nenhum valor real vai para o repositório.**

| Variável | Para quê | Obrigatória em `prod` |
|---|---|---|
| `ADMIN_EMAIL` | e-mail do primeiro admin | sim |
| `ADMIN_SENHA` | senha do primeiro admin (gravada só como hash) | sim |
| `JWT_SEGREDO` | assinatura dos tokens | sim |
| `DB_URL`, `DB_USUARIO`, `DB_SENHA` | PostgreSQL | sim |
| `CORS_ORIGENS` | origens do front (só `https://` em `prod`) | sim |
| `GATEWAY_TOKEN_SANDBOX` | cobrança Pix no sandbox | sim, no fluxo guest |
| `GATEWAY_SEGREDO_WEBHOOK` | validar a assinatura do webhook | sim, no fluxo guest |

### Subir tudo com 1 comando
```bash
docker compose up --build
```
| O que sobe | Endereço local (planejado) |
|---|---|
| Front (Next.js) | `http://localhost:3000` |
| API | `http://localhost:8080` |
| Swagger (só em `dev`) | `http://localhost:8080/swagger-ui.html` |
| Health | `http://localhost:8080/actuator/health` |

**Deve acontecer:** o health responde `{"status":"UP"}` e o Flyway aplica as migrações sem erro no log.

<details>
<summary><b>Sem Docker (pela IDE)</b></summary>

<br/>

```bash
# backend (perfil dev, com PostgreSQL do compose)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# frontend, em outra aba
cd frontend && npm install && npm run dev
```

</details>

---

## 2. Quem é quem nos testes

Dados **fictícios**, criados pelo seed do perfil `dev` (nunca em `prod`):

| Papel | Quem | Como entra |
|---|---|---|
| Cliente | Cliente de teste A | cadastro pelo front ou `POST /auth/cadastro` |
| Cliente | Cliente de teste B | idem, para testar acesso a recurso alheio |
| Guest | Visitante sem conta | só nome, WhatsApp e e-mail; CPF fictício válido no pagamento |
| Barbeiro | Barbeiro de teste | seed do `dev` |
| Admin | definido por `ADMIN_EMAIL` / `ADMIN_SENHA` do seu `.env` | `POST /auth/login` |

---

## 3. Backend: roteiro da API

Cada passo indica o **status esperado**. Os comandos `curl` e as respostas reais entram quando a fatia ficar pronta.

| # | Passo | Chamada | Esperado | Por que importa |
|---|---|---|---|---|
| B1 | Health | `GET /actuator/health` | `200` | a API subiu e o banco conectou |
| B2 | Listar serviços | `GET /servicos` | `200` com nome, duração e preço | catálogo público |
| B3 | Consultar CEP | `GET /cep/{cep}` | `200` com bairro, cidade e UF | integração com ViaCEP |
| B4 | CEP com as APIs fora | simular ViaCEP e BrasilAPI indisponíveis | cadastro continua funcionando, endereço fica para completar | degradação graciosa |
| B5 | Cadastro do cliente A | `POST /auth/cadastro` | `201` | conta criada, CPF não é pedido |
| B6 | Cadastro repetido | mesmo e-mail | `409` Problem Details | e-mail único |
| B7 | Login | `POST /auth/login` | `200` com token de acesso e refresh em cookie | autenticação |
| B8 | Disponibilidade | `GET /disponibilidade?barbeiro=…&dia=…` | `200` com horários livres | base da agenda |
| B9 | Agendar logado | `POST /agendamentos` | `201`, status `CONFIRMADO` | regra do cliente com conta |
| B10 | Mesmo horário de novo | outro cliente, mesmo barbeiro e horário | `409` | anti double booking |
| B11 | Duas reservas ao mesmo tempo | duas requisições simultâneas | só uma `201`, a outra `409` | constraint no banco |
| B12 | Guest | `POST /agendamentos/guest` com CPF | `201`, status `AGUARDANDO_PAGAMENTO` e dados do Pix | sinal de 50% |
| B13 | Webhook pago | `POST /pagamentos/webhook` (sandbox) | `200`, agendamento vira `CONFIRMADO` | confirmação automática |
| B14 | Webhook repetido | mesmo id de evento | `200` sem efeito novo | idempotência |
| B15 | Webhook com assinatura inválida | assinatura alterada | `401` | só o gateway confirma |
| B16 | Guest não paga | esperar 15 min (ou adiantar o relógio no teste) | status `EXPIRADO`, horário volta a ficar livre | ninguém segura horário de graça |
| B17 | Sem token | `POST /agendamentos` sem login | `401` | rota protegida |
| B18 | Recurso alheio | cliente B lê agendamento do cliente A | `404` | não confirma que o id existe |
| B19 | Cliente no painel | cliente A chama `GET /admin/agenda` | `403` | só `ADMIN` |
| B20 | Login do admin | `POST /auth/login` com `ADMIN_EMAIL`/`ADMIN_SENHA` | `200` com papel `ADMIN` | admin por variável de ambiente |
| B21 | Admin erra a senha várias vezes | repetir login errado | `429` após o limite | força bruta bloqueada |
| B22 | Admin gerencia serviço | `POST /admin/servicos` | `201` | painel funciona |
| B23 | Admin fecha atendimento | `PATCH /admin/agendamentos/{id}/status` | `200`, status `CONCLUIDO` | ciclo completo |
| B24 | Exportar e excluir dados | `GET /me` e `DELETE /me` | `200` e `204` | direitos do titular (LGPD) |
| B25 | Erro em formato padrão | qualquer `4xx` acima | `application/problem+json` com `type`, `title`, `status`, `detail`, `instance` | RFC 9457 |
| B26 | Nada pessoal no log | revisar o log depois do roteiro | nenhum CPF, telefone ou CEP | LGPD |

---

## 4. Frontend: roteiro das telas

| # | Tela | O que fazer | Esperado |
|---|---|---|---|
| F1 | Início | abrir `http://localhost:3000` | identidade preto, branco e prata, logo da barbearia e botão "Agendar" |
| F2 | Cadastro | preencher com CEP | bairro e cidade preenchem sozinhos; com CEP inválido, aparece mensagem clara |
| F3 | Login | entrar com o cliente A | vai para "Meus horários"; o token não aparece no `localStorage` (cookie `HttpOnly`) |
| F4 | Agendar | escolher serviço, barbeiro e horário | confirmação com status "Confirmado" |
| F5 | Horário ocupado | tentar o mesmo horário em outra aba | mensagem amigável, sem tela de erro técnico |
| F6 | Guest | agendar sem conta | pede CPF só na etapa de pagamento e mostra o Pix (sandbox) com contagem de 15 min |
| F7 | Consentimento | ver a tela de cadastro | caixa de marketing opcional e desmarcada |
| F8 | Painel admin | entrar com o admin | agenda do dia, serviços, barbeiros e sinais |
| F9 | Admin bloqueado | entrar no painel como cliente | acesso negado, sem dados do painel |
| F10 | Celular e acessibilidade | abrir no modo mobile do navegador | layout legível, contraste alto e navegação por teclado |

---

## 5. Juntos: jornada completa

1. Suba tudo com `docker compose up --build` e confira o health (B1).
2. No front, cadastre o **cliente A** com CEP (F2) e agende um horário (F4). No banco ou no painel, o status é `CONFIRMADO`.
3. Em outra janela anônima, faça uma reserva **guest** no horário seguinte (F6). O horário some da disponibilidade.
4. Dispare o webhook de pagamento do sandbox (B13). A tela do guest muda para "Confirmado".
5. Reenvie o mesmo webhook (B14). Nada muda.
6. Faça outra reserva guest e não pague. Depois de 15 minutos, o horário volta a aparecer livre (B16).
7. Entre no **painel admin** (F8), conclua o atendimento do cliente A e marque falta em outro horário (B23).
8. Como cliente A, exporte e exclua os dados (B24). O login seguinte falha.
9. Revise o log da API: nenhum dado pessoal (B26).

**Pronto quando:** os 9 passos passam em sequência, numa base limpa, sem intervenção manual no banco.

---

## 6. Testes automatizados

```bash
cd backend && ./mvnw verify      # unitários, integração (Testcontainers) e arquitetura
cd frontend && npm test          # componentes
```

| Tipo | Ferramenta | Cobre |
|---|---|---|
| Unitário | JUnit 5, Mockito, AssertJ | regras do agendamento, cálculo do sinal, sanitização de CEP e telefone |
| Integração | Testcontainers (PostgreSQL real) | constraint anti-sobreposição, Flyway, repositórios |
| Concorrência | threads simultâneas no teste | B10 e B11 |
| Segurança | MockMvc / Spring Security Test | 401, 403 e 404 de cada rota, incluindo `/admin/**` |
| Contrato | testes dos erros RFC 9457 | formato do Problem Details |
| Arquitetura | ArchUnit | fronteiras entre módulos |
| Frontend | testes de componentes | formulários, validação e estados de carregamento |

Os números (quantidade de testes, cobertura) entram aqui só depois de uma execução real, com data e comando.

---

## 7. Checklist final

- [ ] Health `UP` e migrações aplicadas
- [ ] Roteiro do backend (B1 a B26) sem divergência
- [ ] Roteiro do frontend (F1 a F10) sem divergência
- [ ] Jornada completa (seção 5) passou numa base limpa
- [ ] `./mvnw verify` e `npm test` verdes
- [ ] Nenhum segredo no repositório (`gitleaks` limpo) e nenhum dado pessoal no log
