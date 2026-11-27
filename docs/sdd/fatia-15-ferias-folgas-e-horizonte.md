# 📐 Spec · Fatia 15 · Férias e folgas no painel, expediente público, mais tempo para pagar e agenda sem limite de data

| | |
|---|---|
| **Status** | 📝 **aguarda aprovação do Kevin** (rascunho de 26/11/2026). Nenhuma linha de código de produção antes do ok |
| **Origem** | pedido do Kevin em 26/11/2026: (1) o cliente marca em **qualquer data futura**; (2) o dono cadastra no painel **férias e folgas** (início e fim com data e hora, motivo opcional, editar e remover), os horários somem para o cliente e, se já houver agendamentos no período, o painel avisa e lista, **sem cancelar sozinho**; (3) a agenda do cliente mostra **"fechado"** em vez de "indisponível", com o expediente e o almoço do dia; (4) **27/11/2026:** mais tempo para pagar passa a **2 minutos por pedido, até 10 vezes** (WCAG 2.2.1) |
| **Branch** | `feat/fatia-15-ferias-e-folgas` (implementação, depois da aprovação) |
| **Regras** | R2 (intervalo meio aberto), R5 (só "depois de agora", D197), R14 (pagamento tardio), R15 (reserva ativa), R18 (cliente não cancela), R7 (prazo do pagamento), R23 (alheio = `404`), R30 (nada pessoal em log), H27 (só o dono mexe em folgas) |
| **ADRs** | [0015](../adr/0015-ferias-folgas-e-agenda-sem-limite.md) (esta fatia, proposto), [0003](../adr/0003-anti-double-booking-no-banco.md), [0005](../adr/0005-erros-rfc-9457.md), [0013](../adr/0013-varios-barbeiros-dono-e-convite.md) |
| **Depende de** | [Fatia 5](fatia-5-painel-do-barbeiro.md) (folgas, trava do barbeiro, auditoria) · [Fatia 14](fatia-14-experiencia-premium.md) (agenda sem antecedência e sem horizonte, D197; calendário do mês) |
| **Contrato** | `1.11.0 → 1.12.0`, **só acréscimos** (seção 2) |
| **Migração** | a próxima livre (a `develop` termina na `V14__prorrogacao_e_agenda_sem_limites.sql`): `folga.tipo`, `folga.atualizado_por`, `folga.atualizado_em` |
| **Decisões** | D202 a D205 em [`decisoes.md`](../planejamento/decisoes.md) (a D205 revoga a D190 e altera a D188) |
| **Custo** | **zero**: nenhuma conta, serviço ou biblioteca nova |
| **Não cobre** | aviso automático ao cliente afetado (o dono avisa pelo WhatsApp) · folga pedida pelo barbeiro terceiro · folga recorrente (toda segunda, por exemplo; a jornada semanal já resolve) · apagar a coluna `horizonte_dias` (D199, depois de uma release estável) |

## 1. Objetivo

O dono marca as férias ou uma folga do barbeiro em poucos toques, vê antes de salvar quem já tinha horário no período e decide o que fazer com cada um. O cliente nunca vê horário dentro de férias ou folga, sabe quando o dia está fechado e pode marcar para qualquer data futura. Quem paga pelo celular pode pedir mais 2 minutos no último minuto, até 10 vezes.

## 2. Contrato (1.12.0)

`openapi.yaml` e `contrato-api.md` já atualizados nesta PR de documentação (`redocly lint` 0 erros e 0 avisos). Só acréscimos; um código de erro novo (`422 PRORROGACAO_ESGOTADA`).

| Método | Rota | Quem | Sucesso | Erros |
|---|---|---|---|---|
| `GET` | `/admin/barbeiros/{id}/folgas` | `ADMIN` | `200` `Folga[]` (agora com `tipo` e `atualizadaEm`) | `401`, `403`, `404` |
| `POST` | `/admin/barbeiros/{id}/folgas` | `ADMIN` | `201` `FolgaSalva` (com `reservasMantidas[]`) | `400`, `401`, `403`, `404`, `422 FOLGA_COM_RESERVAS` com `conflitos[]` |
| `PUT` (**nova**) | `/admin/barbeiros/{id}/folgas/{folgaId}` | `ADMIN` | `200` `FolgaSalva` | `400`, `401`, `403`, `404`, `422 FOLGA_COM_RESERVAS` com `conflitos[]` |
| `DELETE` | `/admin/barbeiros/{id}/folgas/{folgaId}` | `ADMIN` | `204` | `401`, `403`, `404` |
| `GET` (**nova**) | `/admin/barbeiros/{id}/folgas/conflitos?inicio=&fim=` | `ADMIN` | `200` `ConflitoDeFolga[]` (vazio sem conflito) | `400`, `401`, `403`, `404` |
| `POST` | `/agendamentos/{id}/prorrogacao` e `/agendamentos/guest/{id}/prorrogacao` (**mudam**) | dono da reserva / visitante com `X-Guest-Token` | `201` (+120 s) · `200` (mesma `X-Idempotency-Key` do último pedido) | `404`, `422 TRANSICAO_INVALIDA`, `422 RESERVA_EXPIRADA`, **`422 PRORROGACAO_ESGOTADA`** (novo), `422 PRORROGACAO_FORA_DA_JANELA` |
| `GET` | `/disponibilidade` | público | `200` para qualquer data de hoje em diante (D197); **novo:** `barbeiros[].expediente` | `404`, `422 ANTECEDENCIA_INSUFICIENTE`, `422 SERVICO_INATIVO` |

Campos novos:

- `FolgaRequest.tipo`: `FERIAS` ou `FOLGA`, opcional, padrão `FOLGA`.
- `FolgaRequest.manterReservas`: booleano, opcional, padrão `false`, só na escrita; nunca é guardado.
- `Folga.tipo` e `Folga.atualizadaEm` (nulo até a primeira edição). *1.12.1 (D206): `tipo` e `FolgaSalva.reservasMantidas` são opcionais na resposta até este código estar em produção; ausentes = `FOLGA` e lista vazia.*
- `FolgaSalva` = `Folga` + `reservasMantidas: ConflitoDeFolga[]`.
- `ConflitoDeFolga`: `reservaId`, `inicio`, `fim`, `servico { id, nome }`, `clienteNome`, `origem` (`CLIENTE`, `GUEST`, `MANUAL`), `status` (`CONFIRMADO` ou `AGUARDANDO_PAGAMENTO`).
- `Problema.conflitos[]`: extensão RFC 9457 opcional, só no `FOLGA_COM_RESERVAS`.
- `Disponibilidade.barbeiros[].expediente` (`ExpedienteDoDia`): `aberto` (booleano), `motivoFechado` (`FORA_DO_EXPEDIENTE`, `FECHADO` ou `null` quando aberto; o tipo da folga **não** é público), `inicio` e `fim` do expediente do dia (ou `null` fechado) e `almoco` `{ inicio, fim }` (ou `null`). Datas com offset, como `horarios`. Opcional no schema, sempre enviado desde a 1.12.0.

- `Agendamento.prorrogacao`: ganha `usadas`, `maximo` (10) e `restantes`; `segundos` passa a 120; `usada` fica `deprecated` (`true` quando `usadas > 0`). Cabeçalho opcional `X-Idempotency-Key` nas duas rotas. Código novo `422 PRORROGACAO_ESGOTADA`.

Exemplo de um barbeiro na disponibilidade de uma terça aberta (trecho):

```json
{ "barbeiroId": "b0000000-0000-4000-8000-000000000001", "nomeExibicao": "Eduardo Candido",
  "horarios": ["2026-12-01T07:00:00-03:00", "2026-12-01T07:30:00-03:00"],
  "expediente": { "aberto": true, "motivoFechado": null,
    "inicio": "2026-12-01T07:00:00-03:00", "fim": "2026-12-01T20:00:00-03:00",
    "almoco": { "inicio": "2026-12-01T12:00:00-03:00", "fim": "2026-12-01T13:00:00-03:00" } } }
```

Exemplo do `422` (dados fictícios):

```json
{
  "type": "https://github.com/k3vinrich4rd/barbearia-altas-horas/blob/main/docs/contrato/contrato-api.md#folga_com_reservas",
  "title": "Existem reservas no período",
  "status": 422,
  "detail": "Há reservas no período da folga.",
  "instance": "/admin/barbeiros/b0000000-0000-4000-8000-000000000001/folgas",
  "code": "FOLGA_COM_RESERVAS",
  "traceId": "5d0c3f1a9b2e4c7d8e6f1a2b3c4d5e6f",
  "errors": [],
  "conflitos": [
    {
      "reservaId": "0baf3fc1-4b92-458e-baed-c07646d94692",
      "inicio": "2026-12-22T15:00:00-03:00",
      "fim": "2026-12-22T15:30:00-03:00",
      "servico": { "id": "6f1c2a8e-0000-4000-8000-000000000001", "nome": "Corte" },
      "clienteNome": "Cliente Exemplo",
      "origem": "CLIENTE",
      "status": "CONFIRMADO"
    }
  ]
}
```

## 3. Agenda sem limite de data: o que foi conferido (26/11/2026, `develop` em `e91e85d`)

| Onde | O que está lá | Conclusão |
|---|---|---|
| `ConsultaDisponibilidade.java:69`, `ReservarHorario.java:316` | a única recusa por tempo é `422 ANTECEDENCIA_INSUFICIENTE` (início até agora) | sem data máxima |
| `openapi.yaml:120-122` (`/disponibilidade`), `openapi.yaml:835-836` (reserva manual), `contrato-api.md:167` e `:347` | "não há antecedência mínima nem limite de dias à frente" (D197) | contrato já certo |
| `rg -i horizonte backend/src/main/java` | só `CodigoErro.java` (`FORA_DO_HORIZONTE`) e `ConfiguracaoRequest.java` (javadoc e o campo `horizonteDias`), as duas sobras marcadas "deprecated desde a D197, sem efeito"; nenhum código lança o erro nem lê o campo (corrigido em 27/11, ressalva de QA da PR #118) | o código não aplica horizonte |
| `V1__agenda.sql:104,113`, `V14__prorrogacao_e_agenda_sem_limites.sql:7,20` | coluna `horizonte_dias` sem uso, `DEFAULT 30`, CHECK 1 a 365 | sobra de propósito (D199); remoção física depois |
| `openapi.yaml:1646-1653`, `contrato-api.md:443` | `FORA_DO_HORIZONTE` `deprecated`, a API não devolve | sai na próxima versão maior |
| `openapi.yaml:2211`, `ConfiguracaoRequest.java:23` | `horizonteDias` `deprecated`, `writeOnly`, ignorado no `PUT` | sai na próxima versão maior |
| `Agendar.tsx:28` e `:306-307` | `DIAS_DA_BUSCA = 30` (tamanho da busca "Ver o próximo dia com horário"); "Outro dia" só com `min` | limite da busca, não regra |
| `mensagens.ts:43` | texto do `FORA_DO_HORIZONTE` para o front antigo | sem efeito |
| produção, 26/11/2026 | `GET /disponibilidade` para 05/01/2028 e 04/06/2030: `200` com horários | confirmado no ar |

**Decisão:** nada a remover agora. Nenhuma trava nova: cada consulta de disponibilidade cobre um dia só, então não há janela a limitar.

## 4. Decisões de desenho

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E1 | Férias são um recurso novo? | **Não.** `tipo` na folga (`FERIAS`, `FOLGA`; padrão `FOLGA`) | as regras são idênticas; um recurso a mais duplicaria código e contrato |
| E2 | Editar com `PUT` ou `PATCH`? | **`PUT`** substitui `inicio`, `fim`, `motivo` e `tipo` | o formulário manda tudo; o painel já usa `PUT` |
| E3 | O que é conflito? | reserva **ativa** do barbeiro que se sobrepõe a `[inicio, fim)`: `CONFIRMADO`, ou `AGUARDANDO_PAGAMENTO` com prazo aberto | a mesma definição de hoje (R15) e da disponibilidade; encostar não é conflito (R2) |
| E4 | Com conflito, o que acontece? | padrão: `422 FOLGA_COM_RESERVAS` com `conflitos[]` e nada gravado. Com `manterReservas: true`: grava, mantém as reservas e devolve `reservasMantidas[]` | o Kevin pediu: avisar e listar, nunca cancelar sozinho |
| E5 | E as reservas mantidas? | **continuam** e acontecem. Só o `ADMIN` cancela, uma a uma, pelos caminhos que já existem | cancelar reserva paga gera devolução; decisão do dono |
| E6 | Reserva mantida ainda `AGUARDANDO_PAGAMENTO` pode ser paga? | **sim**, dentro do prazo vira `CONFIRMADO` normalmente | o dono escolheu mantê-la (item 1 da seção 8, padrão vigente D216) |
| E7 | Reserva já `EXPIRADO` paga depois, dentro de uma folga? | **não** reconfirma: `confirmarPagamento` trava o barbeiro com `FOR SHARE`, confere a folga e responde `HORARIO_OCUPADO`; a cobrança vira `PAGA_APOS_EXPIRACAO` (pendência do painel) | fecha uma lacuna que já existe hoje (`Reservas.java:148-171`): a constraint não enxerga folgas |
| E8 | Quem gerencia folgas? | **só `ADMIN`**. `BARBEIRO` recebe `403` pela rota `/admin/barbeiros/**`; ele vê as próprias folgas na agenda | H27 e ADR 0013; contrato de hoje |
| E9 | `{folgaId}` de outro barbeiro? | `404` | R23; o `DELETE` de hoje já filtra por `barbeiro_id` |
| E10 | Concorrência | `POST` e `PUT` travam a linha do barbeiro com `FOR UPDATE` **antes** de ler os conflitos; a lista de `reservasMantidas` é lida dentro da trava | folga e reserva nunca passam juntas (E21 da Fatia 5) |
| E11 | Auditoria | mesma transação: `FOLGA_CRIADA`, **`FOLGA_ALTERADA`** (nova), `FOLGA_REMOVIDA`; detalhe com `barbeiroId`, `tipo` e, se houver, quantidade e ids das reservas mantidas | igual às outras ações do painel; motivo e nome do cliente nunca entram (R30) |
| E12 | Prévia trava algo? | **não**: só lê, sem trava e sem gravar | o resultado pode mudar até o `POST`; quem decide é a gravação, que relê dentro da trava |
| E13 | Versão no `PUT`? | **não** nesta fatia | há um único dono; a última gravação vale |
| E14 | Período válido | `fim` depois de `inicio` e não todo no passado; senão `400 VALIDACAO_INVALIDA`. Vale para `POST`, `PUT` e prévia | as mesmas regras de hoje |
| E15 | Erros com dado pessoal | o nome do cliente vai no `422` e na prévia, só para o `ADMIN`, com `Cache-Control: no-store`; o tratador de erros **não** loga `conflitos` | é o mesmo dado do `AgendamentoAdmin`; R30 |
| E16 | Onde fica o `expediente`? | **por barbeiro**, em `barbeiros[]`, não na raiz | jornada e folgas são de cada barbeiro; um pode estar de férias e o outro atendendo |
| E17 | Como se calcula | `inicio` = início do primeiro intervalo da jornada no dia da semana; `fim` = fim do último; `almoco` = vão entre os dois primeiros intervalos (`null` com um só). Fechado: os três `null` | os mesmos dados e o mesmo critério do `ALMOCO` da grade do painel (`VisaoDaAgenda.java:173`) |
| E18 | Quando o dia fecha | sem intervalo na jornada: `FORA_DO_EXPEDIENTE`; folgas ou férias cobrindo **todo** o expediente do dia: `FECHADO`. Folga parcial não fecha o dia | folga parcial pública revelaria compromisso pessoal; só os horários somem |
| E19 | O público vê o tipo? | **não** (27/11/2026, confirmado por UX): o público vê só "Fechado" e o almoço; `FOLGA`/`FERIAS` e o `motivo` só no `/admin` | minimização (LGPD art. 6º, III); o tipo não muda nada na agenda pública (ADR 0015, seção 7) |
| E20 | Mais tempo para pagar: quanto e quantas vezes | **120 s por pedido, até 10** (`app.agenda.prorrogacao-pagamento: PT2M`, `app.agenda.prorrogacao-maximo: 10`); a janela continua `[expiraEm − 60 s, expiraEm)` | WCAG 2.2.1 "Extend" (aviso antes, ação simples, pelo menos 10 vezes); revoga a D190 e altera a D188 |
| E21 | Repetição do pedido | `X-Idempotency-Key` **opcional** (o mesmo nome do cabeçalho da cobrança): repetir o último pedido com a mesma chave devolve `200` com o mesmo corpo, sem somar nem contar; sem chave, a repetição cai fora da janela (`422 PRORROGACAO_FORA_DA_JANELA`) | obrigatório quebraria o front de hoje; a janela já impede somar duas vezes |
| E22 | Ordem das checagens | `404` → `422 TRANSICAO_INVALIDA` → `200` (mesma chave) → `422 RESERVA_EXPIRADA` → **`422 PRORROGACAO_ESGOTADA`** → `422 PRORROGACAO_FORA_DA_JANELA` → grava | a de hoje, com o código novo logo depois do `RESERVA_EXPIRADA` |
| E23 | Pix e cobrança | o copia e cola não muda; na criação, a validade enviada ao gateway = início do prazo + `expiracaoPagamentoMin` + `maximo` × `segundos` (hoje 23 min), **calculada das propriedades**; o horário continua preso pelo `expiraEm` da reserva; pagamento depois de expirar segue o fluxo de hoje (reconfirma ou `PAGA_APOS_EXPIRACAO`) | o Pix tem de continuar pagável durante todo o tempo que a pessoa pode pedir (D191) |
| E24 | Prévia de conflitos no painel | o painel chama `GET /folgas/conflitos` **assim que o dono escolhe as datas, antes de salvar**, e mostra quem está marcado. O `422 FOLGA_COM_RESERVAS` com a lista continua como **rede de segurança** para a corrida (alguém marca entre a prévia e o salvar) | a decisão fica com o dono, antes de qualquer gravação; a gravação relê dentro da trava e é quem decide |

## 5. Critérios de aceite

Barbeiro Eduardo (`b…01`), jornada de terça a sábado das 07:00 às 20:00, almoço das 12:00 às 13:00 (dois intervalos: 07:00–12:00 e 13:00–20:00; H1). Relógio: **ter 01/12/2026 10:00** (São Paulo).

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | nenhuma folga | `POST /folgas` com férias de 21/12 00:00 a 03/01 00:00, `tipo: FERIAS`, sem reserva no período | `201` `FolgaSalva` com `tipo: FERIAS`, `reservasMantidas: []` e `Location`; auditoria `FOLGA_CRIADA` com `tipo` | `FolgaAdminIT` |
| A2 | férias do A1 | `GET /disponibilidade?data=2026-12-22` | `200` sem horários para o Eduardo | `FeriasDisponibilidadeIT` |
| A3 | férias do A1 | cliente, visitante e reserva manual tentam 22/12 15:00 | `422 FORA_DO_EXPEDIENTE` nos três, nada gravado | `FeriasReservaIT` |
| A4 | corpo sem `tipo` (front de hoje) | `POST /folgas` | `201` com `tipo: FOLGA` | `FolgaAdminIT` |
| A5 | folga de 05/12 14:00 a 16:00 | `PUT /folgas/{folgaId}` para 05/12 13:00 a 17:00, `tipo: FOLGA`, motivo novo | `200`; `atualizadaEm` preenchido; a disponibilidade de 05/12 não tem nenhum início que invada 13:00–17:00; auditoria `FOLGA_ALTERADA` | `FolgaEdicaoIT` |
| A6 | folga do A5 | `PUT` reduzindo para 05/12 15:00 a 16:00 | `200`; os inícios que não invadem 15:00–16:00 voltam a aparecer na disponibilidade | `FolgaEdicaoIT` |
| A7 | folga do A5 | `DELETE /folgas/{folgaId}` | `204`; os horários voltam; auditoria `FOLGA_REMOVIDA` | `FolgaAdminIT` |
| A8 | reserva `CONFIRMADO` 22/12 15:00–15:30 e `AGUARDANDO_PAGAMENTO` no prazo 22/12 15:30–16:00 | `GET /folgas/conflitos?inicio=2026-12-21T00:00-03:00&fim=2026-12-23T00:00-03:00` | `200` com as 2 reservas em ordem de início, cada uma com `reservaId`, `inicio`, `fim`, `servico`, `clienteNome`, `origem` e `status`; sem e-mail, CPF, WhatsApp nem observação; nada gravado | `ConflitosDeFolgaIT` |
| A9 | as reservas do A8, mais uma `AGUARDANDO_PAGAMENTO` vencida e uma `CANCELADO` no período | `POST /folgas` cobrindo o período, sem `manterReservas` | `422 FOLGA_COM_RESERVAS` com `conflitos[]` listando **só** as 2 ativas; nada gravado; sem auditoria | `FolgaAdminIT` |
| A10 | as reservas do A8 | `PUT` de uma folga existente aumentando o período por cima delas, sem `manterReservas` | `422 FOLGA_COM_RESERVAS` com `conflitos[]`; a folga continua como era | `FolgaEdicaoIT` |
| A11 | as reservas do A8 | `POST /folgas` com `manterReservas: true` | `201` com `reservasMantidas` (as 2); as 2 reservas continuam com o mesmo status; a disponibilidade não mostra nenhum horário no período; a auditoria guarda a quantidade (2) e os ids, sem nome nem motivo | `ManterReservasIT` |
| A12 | o A11 feito | uma reserva nova no período (cliente, visitante, manual) | `422 FORA_DO_EXPEDIENTE` | `ManterReservasIT` |
| A13 | o A11 feito; a reserva `AGUARDANDO_PAGAMENTO` é paga dentro do prazo | webhook de pagamento | a reserva vira `CONFIRMADO` (E6) | `ManterReservasIT` |
| A14 | reserva `EXPIRADO` às 22/12 15:00, **ou ainda `AGUARDANDO_PAGAMENTO` com o prazo vencido** (o job de expiração não passou), e depois uma folga que cobre o horário | pagamento tardio da reserva | a reserva **não** volta a `CONFIRMADO` (continua no status em que estava); a cobrança vira `PAGA_APOS_EXPIRACAO` e aparece nas pendências (E7, D217). Controle: com a folga só encostando no horário (meio aberto, R2), reconfirma como no R14 | `PagamentoTardioEmFolgaIT` |
| A15 | outro barbeiro (`b…02`) com folga `F2` | `PUT` e `DELETE /admin/barbeiros/b…01/folgas/F2` | `404` nos dois, `F2` intacta | `FolgaBolaIT` |
| A16 | conta `BARBEIRO` vinculada ao Eduardo | `GET`, `POST`, `PUT`, `DELETE /folgas` e `GET /folgas/conflitos` do próprio Eduardo | `403 ACESSO_NEGADO` em todas (H27) | `FolgaBolaIT` |
| A17 | barbeiro inexistente | `GET /folgas/conflitos` | `404` | `ConflitosDeFolgaIT` |
| A18 | qualquer | `POST`, `PUT` ou prévia com `fim` antes ou igual a `inicio` | `400 VALIDACAO_INVALIDA`, nada gravado | `FolgaValidacaoIT` |
| A19 | qualquer | `POST`, `PUT` ou prévia com o período todo no passado (30/11 10:00 a 30/11 12:00) | `400 VALIDACAO_INVALIDA`, nada gravado | `FolgaValidacaoIT` |
| A20 | qualquer | `tipo: VIAGEM` ou `motivo` com 201 caracteres | `400` | `FolgaValidacaoIT` |
| A21 | horário livre às 22/12 15:00 | um `PUT` que passa a cobrir 15:00 e uma reserva às 15:00, ao mesmo tempo (50 repetições) | em toda repetição: **ou** `PUT` `200` e reserva `422 FORA_DO_EXPEDIENTE`, **ou** reserva `201` e `PUT` `422 FOLGA_COM_RESERVAS`; nunca as duas gravadas | `ConcorrenciaFolgaIT` |
| A22 | hoje 01/12/2026 | `GET /disponibilidade?data=2028-01-05` e reserva de cliente para 05/01/2028 10:00 | `200` com horários e `201`; nenhuma data futura é recusada (D197) | `AgendaSemLimiteIT` |
| A23 | hoje 01/12/2026 | `GET /disponibilidade?data=2026-11-30` | `422 ANTECEDENCIA_INSUFICIENTE` (não muda) | `AgendaSemLimiteIT` |
| A24 | folga com `motivo` "consulta médica" e reservas mantidas | criar, editar e remover | nem o motivo nem o nome de cliente aparecem no log nem na auditoria | `SemDadoPessoalNoLogIT` |
| A25 | contrato | `redocly lint` e `ContratoExemplosDeErroTest` | 0 erros; o exemplo do `422 FOLGA_COM_RESERVAS` com `conflitos` bate com o enum; versão `1.12.x` | `ContratoExemplosDeErroTest` |
| A26 | jornada de terça a sábado | `GET /disponibilidade` para dom 06/12 e seg 07/12 | `200`; `horarios: []`; `expediente` = `{ aberto: false, motivoFechado: FORA_DO_EXPEDIENTE, inicio: null, fim: null, almoco: null }` nos dois dias | `ExpedienteDisponibilidadeIT` |
| A27 | terça 08/12 sem folga | `GET /disponibilidade?data=2026-12-08` | `expediente.aberto: true`, `motivoFechado: null`, `inicio` 07:00, `fim` 20:00 e `almoco` `{ inicio: 12:00, fim: 13:00 }`, todos com `-03:00` | `ExpedienteDisponibilidadeIT` |
| A28 | jornada de terça com um intervalo só (07:00–20:00) | `GET /disponibilidade` de uma terça | `almoco: null`; `inicio` 07:00, `fim` 20:00 | `ExpedienteDisponibilidadeIT` |
| A29 | férias do A1 (21/12 a 03/01) | `GET /disponibilidade?data=2026-12-22` | `horarios: []`; `expediente` = `{ aberto: false, motivoFechado: FECHADO, inicio: null, fim: null, almoco: null }`; nem `FERIAS` nem o `motivo` aparecem em nenhum campo | `ExpedienteDisponibilidadeIT` |
| A30 | folga `FOLGA` de 05/12 00:00 a 06/12 00:00 (sábado inteiro) | `GET /disponibilidade?data=2026-12-05` | `aberto: false`, `motivoFechado: FECHADO` (igual às férias: o tipo não é público) | `ExpedienteDisponibilidadeIT` |
| A31 | folga só de 05/12 14:00 a 16:00 | `GET /disponibilidade?data=2026-12-05` | `aberto: true`, `motivoFechado: null`, `inicio`, `fim` e `almoco` normais; só somem os inícios que invadiriam 14:00–16:00 (folga parcial não é pública) | `ExpedienteDisponibilidadeIT` |
| A32 | uma `FOLGA` e umas `FERIAS` cobrindo o mesmo sábado inteiro; o mesmo sábado no painel | `GET /disponibilidade` desse sábado e `GET /admin/barbeiros/{id}/folgas` | público: `motivoFechado: FECHADO`; painel (ADMIN): as duas folgas com `tipo` e `motivo` | `ExpedienteDisponibilidadeIT` |
| A33 | dois barbeiros; só o Eduardo de férias em 22/12 | `GET /disponibilidade?data=2026-12-22` sem `barbeiroId` | o Eduardo vem `aberto: false, FECHADO`; o outro, `aberto: true` com os horários dele | `ExpedienteDisponibilidadeIT` |
| A34 | reserva de cliente criada às 10:00:00 (`expiraEm` 10:03:00), `usadas: 0` | `POST /agendamentos/{id}/prorrogacao` às 10:02:30 | `201`; `expiraEm` **10:05:00** (+120 s); `prorrogacao` = `{ usadas: 1, maximo: 10, restantes: 9, segundos: 120, usada: true }`; evento `PrazoDaReservaProrrogado` e `cobranca.expira_em` = 10:05:00 | `ProrrogacaoDoPagamentoIT` |
| A35 | o A34 feito | o mesmo pedido de novo, com a **mesma** `X-Idempotency-Key`, às 10:02:31 | `200` com o mesmo corpo; `expiraEm` continua 10:05:00 e `usadas` continua 1 | `ProrrogacaoDoPagamentoIT` |
| A36 | o A34 feito | um pedido **sem** chave (ou com chave nova) às 10:02:31 | `422 PRORROGACAO_FORA_DA_JANELA` (o prazo tem mais de 60 s); nada muda | `ProrrogacaoDoPagamentoIT` |
| A37 | `usadas: 9`, dentro do último minuto | o 10º pedido | `201`; `usadas: 10`, `restantes: 0`; o objeto `prorrogacao` continua presente | `ProrrogacaoDoPagamentoIT` |
| A38 | `usadas: 10`, dentro do último minuto | o **11º** pedido (cliente e visitante) | `422 PRORROGACAO_ESGOTADA`; `expiraEm` e `usadas` não mudam | `ProrrogacaoDoPagamentoIT` |
| A39 | `usadas: 10` e o prazo já vencido | um pedido | `422 RESERVA_EXPIRADA` (vem antes do `PRORROGACAO_ESGOTADA`) | `ProrrogacaoDoPagamentoIT` |
| A40 | dois pedidos sem chave ao mesmo tempo no último minuto (50 repetições) | `POST /prorrogacao` em paralelo | em toda repetição: um `201` e um `422 PRORROGACAO_FORA_DA_JANELA`; `usadas` sobe 1 e `expiraEm` ganha 120 s uma vez só | `ConcorrenciaProrrogacaoIT` |
| A41 | `expiracaoPagamentoMin` 3, `prorrogacao-pagamento` PT2M, `prorrogacao-maximo` 10 | `POST /pagamentos/cobrancas` (Pix) | o gateway recebe a validade de **23 min** depois do início do prazo da reserva; com as propriedades trocadas (ex.: PT1M e 5) a validade muda junto (8 min), sem número fixo no código; o copia e cola não muda depois de um pedido de mais tempo | `CobrancaValidadeIT` |
| A42 | reserva expirada depois de 3 pedidos | o Pix é pago depois do `expiraEm` | reconfirma se o horário estiver livre, ou a cobrança vira `PAGA_APOS_EXPIRACAO` (o fluxo de hoje, R14) | `PagamentoTardioIT` |
| A43 | contrato | `ContratoExemplosDeErroTest` | `PRORROGACAO_ESGOTADA` está no enum do Java e do contrato, com o exemplo (422, "Prorrogações esgotadas") e a linha na tabela de erros | `ContratoExemplosDeErroTest` |
| A44 | visitante sem token e conta só `BARBEIRO` | `GET`, `POST`, `PUT` e `DELETE /admin/barbeiros/{id}/folgas` e `GET .../folgas/conflitos` (as rotas da página Férias e folgas, C13) | sem token: `401 NAO_AUTENTICADO` em todas; `BARBEIRO`: `403 ACESSO_NEGADO` em todas; só o `ADMIN` passa (H27, E8, D216) | **pendente**: ainda não há teste com o rótulo A44. Hoje, `FolgaBolaIT` (A16) cobre o 403 do `BARBEIRO` nas cinco rotas e `AcessoAdminIT` (A70 da Fatia 5) cobre o 401 só em `GET`, `POST` e `DELETE /folgas`; falta o 401 em `PUT` e em `GET /folgas/conflitos` |

**Telas (UX especifica depois da aprovação):** na agenda do cliente, dia com `aberto: false` mostra só "Fechado" em vez de "Sem horário", e dia aberto mostra o expediente e o almoço. No pagamento, o botão "Acrescentar 2 minutos" aparece no último minuto enquanto `restantes > 0` e manda uma `X-Idempotency-Key` nova a cada toque (a mesma só na repetição automática). No painel, o formulário de folga pede início, fim, tipo e motivo; **assim que o dono escolhe as datas, antes de salvar**, chama a prévia `GET /folgas/conflitos` e mostra quem está marcado (E24); o `422 FOLGA_COM_RESERVAS` com a lista só aparece se alguém marcar entre a prévia e o salvar (rede de segurança contra corrida), e a tela mostra a lista que vem no problema; "Salvar e manter os agendamentos" envia `manterReservas: true`; depois de salvar, lista as reservas mantidas com o atalho para cada uma. Os critérios de tela estão na seção 5.1.


### 5.1 Critérios de tela (front)

Os critérios das specs de UX de 27/11 que os PRs #140 (mais tempo para pagar e agenda com expediente) e #141 (página Férias e folgas) cobrem. O rótulo é o mesmo do teste do front (`MT-`, `AF-` e `FF-`), e o `conferir-spec.sh 15` também confere estas linhas. Os textos citados são os do código; os que ainda esperam o ok do Kevin estão nas perguntas desses PRs.

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| MT-1 | reserva no último minuto, `usadas = 0` | o botão aparece | aparece "Acrescentar 2 minutos" e **não** aparece "Restam N pedidos." | `pagamento-prorrogacao.test.tsx` |
| MT-1b | `usadas = 1`, `restantes = 9`, no último minuto de novo | o botão volta | aparece, discreto, "Restam 9 pedidos." (número vindo da API) | `pagamento-prorrogacao.test.tsx` |
| MT-3 | 9 pedidos usados | pede o 10º | funciona; depois o botão e a linha de pedidos não voltam no último minuto seguinte | `pagamento-prorrogacao.test.tsx` |
| MT-4c | a tela recebe `422 PRORROGACAO_ESGOTADA` | mostra o aviso | "Você já usou todo o tempo extra. Conclua o pagamento agora ou escolha outro horário." (D214), como aviso, sem "Erro:" nem código de suporte; o botão some | `pagamento-prorrogacao.test.tsx` |
| MT-4d | um toque; depois a rede cai no meio do pedido | o front repete sozinho | cada toque manda uma `X-Idempotency-Key` nova (UUID); a repetição automática manda a **mesma** | `pagamento-prorrogacao.test.tsx` |
| MT-7 | `mensagens-voz.test.ts` | roda | os textos da Fatia 15 (pedidos restantes, tempo extra esgotado e dia fechado) estão travados palavra por palavra | `mensagens-voz.test.ts` |
| AF-10 | `expediente.aberto: false` no domingo | vê a tira de dias | o chip diz "Fechado", com `aria-disabled="true"`, não escolhe o dia e não mostra o motivo; dia aberto que ficou sem horário diz "sem horário", nunca "Fechado" (E18) | `agendar-expediente.test.tsx` |
| AF-11 | almoço das 12:00 às 13:00 | vê os horários do dia | "Fechado · 12h às 13h" entre Manhã e Tarde, sem a palavra "Almoço" e sem chip "12:00 indisponível" (D209) | `agendar-expediente.test.tsx` |
| AF-12 | `expediente` ausente (API antiga) | vê a tira de dias | a tela funciona como antes ("sem horário") e nada fica "Fechado" | `agendar-expediente.test.tsx` |
| AF-13 | 360×800 e 1280×800, API real | roda o axe na agenda | 0 violações (WCAG 2.2 AA), com a tira "Fechado" e a faixa do almoço | `agenda-expediente.spec.ts` |
| AF-15 | folga de 14:00 a 16:00 numa terça | vê os horários da terça | o dia **não** fica "Fechado"; os horários que ela tira aparecem como indisponíveis, sem motivo | `agendar-expediente.test.tsx` |
| FF-1 | ADMIN, sem folgas | abre "Férias e folgas" | "Nenhuma folga de hoje em diante." e o botão "Marcar férias ou folga"; "Remover" pede confirmação antes do `DELETE`; fim antes do início é barrado na tela | `painel-folgas.test.tsx` |
| FF-3 | período com 2 clientes | escolhe as datas | a prévia mostra os 2 (horário, serviço, nome, origem, status) antes de salvar | `painel-folgas.test.tsx`, `painel-folgas.spec.ts` |
| FF-4 | a prévia com clientes | toca "Salvar e manter os agendamentos" (D219; o front ainda mostra "Salvar e manter os horários") | manda `manterReservas: true`; o aviso diz quantos continuam marcados e lista as reservas mantidas com "Ver na agenda" | `painel-folgas.test.tsx`, `painel-folgas.spec.ts` |
| FF-5 | a prévia com clientes | "Cancelar um a um" | cancela pela barbearia só a reserva escolhida e a prévia passa a mostrar 1 | `painel-folgas.test.tsx` |
| FF-6 | "Dia inteiro" de 25/11 a 27/11, tipo Férias | salva | manda `inicio` 25/11 00:00, `fim` 28/11 00:00 (BRT) e `tipo` `FERIAS` | `painel-folgas.test.tsx` |
| FF-7 | uma folga na lista | "Editar", muda o fim e salva | manda `PUT` com `inicio`, `fim`, `motivo` e `tipo`; a lista mostra o fim novo | `painel-folgas.test.tsx` |
| FF-8 | o campo motivo | digita além do limite | para em 200 caracteres (contador 200/200) e a tela lembra de não escrever dado de cliente | `painel-folgas.test.tsx` |
| FF-9 | 360×800, API real | roda o axe na página | 0 violações | `painel-folgas.spec.ts` |
| FF-10 | ADMIN escolhe as datas | antes de tocar em salvar | o painel já chamou `GET .../folgas/conflitos` com `inicio` e `fim` | `painel-folgas.test.tsx`, `painel-folgas.spec.ts` |
| FF-11 | alguém marca entre a prévia e o salvar | ADMIN salva sem manter | `422 FOLGA_COM_RESERVAS`: a tela mostra o texto final (D214), a lista de `conflitos[]` e as 3 saídas; nada foi gravado | `painel-folgas.test.tsx`, `mensagens-voz.test.ts` |

Ficam de fora desta tabela, sem rótulo próprio no front:
- MT-4, MT-4b e MT-5 são regras da API (A38, A35 e A36).
- MT-6 (o BFF chama a rota do visitante) está dentro do A27 da Fatia 14, em `prorrogacao-pix.spec.ts`.
- FF-2 (o BARBEIRO não vê a página) está no A44 da Fatia 7, em `painel-acesso.test.tsx`; a parte da API é o A16 e o A44 desta spec.
- FF-12 (o público vê só "Fechado") ainda não tem teste próprio de tela.
- AF-1 a AF-9 e AF-14 são da agenda fluida, fora destes PRs.

## 6. Entregas

| Módulo | O que |
|---|---|
| banco | migração nova (próxima `V` livre): `folga.tipo varchar(10) NOT NULL DEFAULT 'FOLGA'` com CHECK (`FERIAS`, `FOLGA`), `atualizado_por uuid NULL REFERENCES usuario(id)`, `atualizado_em timestamptz NULL`. Só acrescenta |
| `agenda` (prorrogação) | `ProrrogarPagamento`: 120 s por pedido, contador `prorrogacoes`, `X-Idempotency-Key` do último pedido, `PRORROGACAO_ESGOTADA` depois do `RESERVA_EXPIRADA`; `application.yml`: `prorrogacao-pagamento: PT2M` e `prorrogacao-maximo: 10`; migração: `reserva.prorrogacoes` e `reserva.prorrogacao_chave` |
| `pagamento` | `CriarCobranca`: validade enviada ao gateway = início do prazo + `expiracaoPagamentoMin` + `maximo` × `segundos` (das propriedades) |
| `agenda` | `ConsultaDisponibilidade` e `CalculadoraDeHorarios`: monta o `ExpedienteDoDia` por barbeiro com a jornada e as folgas que já carrega (sem consulta nova); `CadastrosDoPainel`: `criarFolga` com `tipo` e `manterReservas`; `alterarFolga` nova (mesma trava); `conflitosDaFolga` (leitura); `Reservas.confirmarPagamento` confere folga quando a reserva está `EXPIRADO` (E7) |
| `admin` | `BarbeirosAdminController`: `PUT /folgas/{folgaId}` e `GET /folgas/conflitos`; `CadastroDeFolgas` com auditoria `FOLGA_ALTERADA`; DTOs `FolgaRequest` (`tipo`, `manterReservas`), `FolgaAdmin` (`tipo`, `atualizadaEm`), `FolgaSalva`, `ConflitoDeFolga` |
| `comum.erro` | `ExcecaoDeNegocio` carrega a extensão `conflitos` no `FOLGA_COM_RESERVAS`; o log do tratador não imprime extensões |
| front | `schema.d.ts` e `rotas-permitidas.ts` já regenerados nesta PR, e um texto **provisório** para `PRORROGACAO_ESGOTADA` em `mensagens.ts` (o CI exige um texto por código); textos finais e telas na spec de UX |
| `comum.erro` | `CodigoErro.PRORROGACAO_ESGOTADA` já entra nesta PR (o teste de contrato exige o enum do Java igual ao do contrato); quem lança é a implementação |
| docs | guia de testes com a saída real; `painel-admin.md` (ação `FOLGA_ALTERADA`); `contexto.md` (H27 continua) |

## 7. Riscos e cuidados

- **Esquecer reservas mantidas.** A resposta lista as mantidas e o calendário do mês mostra reservas e folga no mesmo dia.
- **Prévia desatualizada.** A prévia não trava. A gravação relê os conflitos dentro da trava e decide.
- **Rota literal ao lado de rota com id.** `/folgas/conflitos` e `/folgas/{folgaId}`: no Spring a rota literal ganha. Não existe `GET /folgas/{folgaId}`.
- **Dado pessoal no erro.** `clienteNome` só para `ADMIN`, sem log (R30).

## 8. Padrão vigente (D216) até resposta do dono

Estas perguntas deixaram de ser abertas em 27/11/2026. Cada uma vale como **padrão vigente (D216) até resposta do dono**, conforme a [D216](../planejamento/decisoes.md). Se o dono responder diferente, a mudança vira uma nova decisão.

1. Com `manterReservas`, a reserva ainda aguardando pagamento dentro das férias **pode ser paga e confirmada** no prazo (E6). Padrão vigente (D216) até resposta do dono.
2. **Só o ADMIN** cadastra, edita e remove folgas; o barbeiro terceiro não cadastra as próprias (H27, E8). Padrão vigente (D216) até resposta do dono.
3. **Sem aviso automático** ao cliente com horário dentro das férias: o dono avisa pelo WhatsApp. Padrão vigente (D216) até resposta do dono.
4. A tela de pagamento mostra **"Restam N pedidos."** ("Resta 1 pedido."), discreto, a partir do 1º pedido de mais tempo, com N = `prorrogacao.restantes`. Padrão vigente (D216) até resposta do dono.

## 9. Pronto quando

- [ ] todos os critérios com teste verde no `./mvnw verify`
- [ ] `scripts/conferir-spec.sh 15 --exigir` verde e a fatia 15 incluída no laço do CI
- [ ] [checklist da PR](checklist-pr.md) completo
- [ ] spec de UX das telas de férias e folgas aprovada

