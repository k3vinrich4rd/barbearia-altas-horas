# 📐 Spec · Fatia 15 · Férias e folgas no painel, expediente público e agenda sem limite de data

| | |
|---|---|
| **Status** | 📝 **aguarda aprovação do Kevin** (rascunho de 26/11/2026). Nenhuma linha de código de produção antes do ok |
| **Origem** | pedido do Kevin em 26/11/2026: (1) o cliente marca em **qualquer data futura**; (2) o dono cadastra no painel **férias e folgas** (início e fim com data e hora, motivo opcional, editar e remover), os horários somem para o cliente e, se já houver agendamentos no período, o painel avisa e lista, **sem cancelar sozinho**; (3) a agenda do cliente mostra **"fechado"** em vez de "indisponível", com o expediente e o almoço do dia |
| **Branch** | `feat/fatia-15-ferias-e-folgas` (implementação, depois da aprovação) |
| **Regras** | R2 (intervalo meio aberto), R5 (só "depois de agora", D197), R14 (pagamento tardio), R15 (reserva ativa), R18 (cliente não cancela), R23 (alheio = `404`), R30 (nada pessoal em log), H27 (só o dono mexe em folgas) |
| **ADRs** | [0015](../adr/0015-ferias-folgas-e-agenda-sem-limite.md) (esta fatia, proposto), [0003](../adr/0003-anti-double-booking-no-banco.md), [0005](../adr/0005-erros-rfc-9457.md), [0013](../adr/0013-varios-barbeiros-dono-e-convite.md) |
| **Depende de** | [Fatia 5](fatia-5-painel-do-barbeiro.md) (folgas, trava do barbeiro, auditoria) · [Fatia 14](fatia-14-experiencia-premium.md) (agenda sem antecedência e sem horizonte, D197; calendário do mês) |
| **Contrato** | `1.11.0 → 1.12.0`, **só acréscimos** (seção 2) |
| **Migração** | a próxima livre (a `develop` termina na `V14__prorrogacao_e_agenda_sem_limites.sql`): `folga.tipo`, `folga.atualizado_por`, `folga.atualizado_em` |
| **Decisões** | D202 em [`decisoes.md`](../planejamento/decisoes.md) |
| **Custo** | **zero**: nenhuma conta, serviço ou biblioteca nova |
| **Não cobre** | aviso automático ao cliente afetado (o dono avisa pelo WhatsApp) · folga pedida pelo barbeiro terceiro · folga recorrente (toda segunda, por exemplo; a jornada semanal já resolve) · apagar a coluna `horizonte_dias` (D199, depois de uma release estável) |

## 1. Objetivo

O dono marca as férias ou uma folga do barbeiro em poucos toques, vê antes de salvar quem já tinha horário no período e decide o que fazer com cada um. O cliente nunca vê horário dentro de férias ou folga, sabe quando o dia está fechado (e por quê) e pode marcar para qualquer data futura.

## 2. Contrato (1.12.0)

`openapi.yaml` e `contrato-api.md` já atualizados nesta PR de documentação (`redocly lint` 0 erros e 0 avisos). Só acréscimos; nenhum código de erro novo.

| Método | Rota | Quem | Sucesso | Erros |
|---|---|---|---|---|
| `GET` | `/admin/barbeiros/{id}/folgas` | `ADMIN` | `200` `Folga[]` (agora com `tipo` e `atualizadaEm`) | `401`, `403`, `404` |
| `POST` | `/admin/barbeiros/{id}/folgas` | `ADMIN` | `201` `FolgaSalva` (com `reservasMantidas[]`) | `400`, `401`, `403`, `404`, `422 FOLGA_COM_RESERVAS` com `conflitos[]` |
| `PUT` (**nova**) | `/admin/barbeiros/{id}/folgas/{folgaId}` | `ADMIN` | `200` `FolgaSalva` | `400`, `401`, `403`, `404`, `422 FOLGA_COM_RESERVAS` com `conflitos[]` |
| `DELETE` | `/admin/barbeiros/{id}/folgas/{folgaId}` | `ADMIN` | `204` | `401`, `403`, `404` |
| `GET` (**nova**) | `/admin/barbeiros/{id}/folgas/conflitos?inicio=&fim=` | `ADMIN` | `200` `ConflitoDeFolga[]` (vazio sem conflito) | `400`, `401`, `403`, `404` |
| `GET` | `/disponibilidade` | público | `200` para qualquer data de hoje em diante (D197); **novo:** `barbeiros[].expediente` | `404`, `422 ANTECEDENCIA_INSUFICIENTE`, `422 SERVICO_INATIVO` |

Campos novos:

- `FolgaRequest.tipo`: `FERIAS` ou `FOLGA`, opcional, padrão `FOLGA`.
- `FolgaRequest.manterReservas`: booleano, opcional, padrão `false`, só na escrita; nunca é guardado.
- `Folga.tipo` (sempre presente) e `Folga.atualizadaEm` (nulo até a primeira edição).
- `FolgaSalva` = `Folga` + `reservasMantidas: ConflitoDeFolga[]`.
- `ConflitoDeFolga`: `reservaId`, `inicio`, `fim`, `servico { id, nome }`, `clienteNome`, `origem` (`CLIENTE`, `GUEST`, `MANUAL`), `status` (`CONFIRMADO` ou `AGUARDANDO_PAGAMENTO`).
- `Problema.conflitos[]`: extensão RFC 9457 opcional, só no `FOLGA_COM_RESERVAS`.
- `Disponibilidade.barbeiros[].expediente` (`ExpedienteDoDia`): `aberto` (booleano), `motivoFechado` (`FORA_DO_EXPEDIENTE`, `FOLGA`, `FERIAS` ou `null` quando aberto), `inicio` e `fim` do expediente do dia (ou `null` fechado) e `almoco` `{ inicio, fim }` (ou `null`). Datas com offset, como `horarios`. Opcional no schema, sempre enviado desde a 1.12.0.

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
| `rg -i horizonte backend/src/main/java` | nenhum resultado | o código não lê horizonte |
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
| E6 | Reserva mantida ainda `AGUARDANDO_PAGAMENTO` pode ser paga? | **sim**, dentro do prazo vira `CONFIRMADO` normalmente | o dono escolheu mantê-la (pergunta 1 da seção 8) |
| E7 | Reserva já `EXPIRADO` paga depois, dentro de uma folga? | **não** reconfirma: `confirmarPagamento` trava o barbeiro com `FOR SHARE`, confere a folga e responde `HORARIO_OCUPADO`; a cobrança vira `PAGA_APOS_EXPIRACAO` (pendência do painel) | fecha uma lacuna que já existe hoje (`Reservas.java:148-171`): a constraint não enxerga folgas |
| E8 | Quem gerencia folgas? | **só `ADMIN`**. `BARBEIRO` recebe `403` pela rota `/admin/barbeiros/**`; ele vê as próprias folgas na agenda | H27 e ADR 0013; contrato de hoje |
| E9 | `{folgaId}` de outro barbeiro? | `404` | R23; o `DELETE` de hoje já filtra por `barbeiro_id` |
| E10 | Concorrência | `POST` e `PUT` travam a linha do barbeiro com `FOR UPDATE` **antes** de ler os conflitos; a lista de `reservasMantidas` é lida dentro da trava | folga e reserva nunca passam juntas (E21 da Fatia 5) |
| E11 | Auditoria | mesma transação: `FOLGA_CRIADA`, **`FOLGA_ALTERADA`** (nova), `FOLGA_REMOVIDA`; detalhe com `barbeiroId`, `tipo` e, se houver, quantidade e ids das reservas mantidas | igual às outras ações do painel; motivo e nome do cliente nunca entram (R30) |
| E12 | Prévia trava algo? | **não**: só lê, sem trava e sem gravar | o resultado pode mudar até o `POST`; quem decide é a gravação, que relê dentro da trava |
| E13 | Versão no `PUT`? | **não** nesta fatia | há um único dono; a última gravação vale |
| E14 | Período válido | `fim` depois de `inicio` e não todo no passado; senão `400 VALIDACAO_INVALIDA`. Vale para `POST`, `PUT` e prévia | as mesmas regras de hoje |
| E16 | Onde fica o `expediente`? | **por barbeiro**, em `barbeiros[]`, não na raiz | jornada e folgas são de cada barbeiro; um pode estar de férias e o outro atendendo |
| E17 | Como se calcula | `inicio` = início do primeiro intervalo da jornada no dia da semana; `fim` = fim do último; `almoco` = vão entre os dois primeiros intervalos (`null` com um só). Fechado: os três `null` | os mesmos dados e o mesmo critério do `ALMOCO` da grade do painel (`VisaoDaAgenda.java:173`) |
| E18 | Quando o dia fecha | sem intervalo na jornada: `FORA_DO_EXPEDIENTE`; folgas ou férias cobrindo **todo** o expediente do dia: `FOLGA` ou `FERIAS` (as duas: `FERIAS`). Folga parcial não fecha o dia | folga parcial pública revelaria compromisso pessoal; só os horários somem |
| E19 | O público vê o tipo? | **sim**, só o tipo (`FOLGA` ou `FERIAS`) e só no dia consultado; o texto do `motivo` **nunca** | ajuda o cliente a escolher outra data; não traz o período nem o motivo (ADR 0015, seção 7). Alternativa `FECHADO` fica como pergunta 4 |
| E15 | Erros com dado pessoal | o nome do cliente vai no `422` e na prévia, só para o `ADMIN`, com `Cache-Control: no-store`; o tratador de erros **não** loga `conflitos` | é o mesmo dado do `AgendamentoAdmin`; R30 |

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
| A14 | reserva `EXPIRADO` às 22/12 15:00 e depois uma folga que cobre o horário | pagamento tardio da reserva | a reserva **continua** `EXPIRADO`; a cobrança vira `PAGA_APOS_EXPIRACAO` e aparece nas pendências (E7) | `PagamentoTardioEmFolgaIT` |
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
| A25 | contrato | `redocly lint` e `ContratoExemplosDeErroTest` | 0 erros; o exemplo do `422 FOLGA_COM_RESERVAS` com `conflitos` bate com o enum; versão `1.12.0` | CI |
| A26 | jornada de terça a sábado | `GET /disponibilidade` para dom 06/12 e seg 07/12 | `200`; `horarios: []`; `expediente` = `{ aberto: false, motivoFechado: FORA_DO_EXPEDIENTE, inicio: null, fim: null, almoco: null }` nos dois dias | `ExpedienteDisponibilidadeIT` |
| A27 | terça 08/12 sem folga | `GET /disponibilidade?data=2026-12-08` | `expediente.aberto: true`, `motivoFechado: null`, `inicio` 07:00, `fim` 20:00 e `almoco` `{ inicio: 12:00, fim: 13:00 }`, todos com `-03:00` | `ExpedienteDisponibilidadeIT` |
| A28 | jornada de terça com um intervalo só (07:00–20:00) | `GET /disponibilidade` de uma terça | `almoco: null`; `inicio` 07:00, `fim` 20:00 | `ExpedienteDisponibilidadeIT` |
| A29 | férias do A1 (21/12 a 03/01) | `GET /disponibilidade?data=2026-12-22` | `horarios: []`; `expediente` = `{ aberto: false, motivoFechado: FERIAS, inicio: null, fim: null, almoco: null }`; o `motivo` das férias não aparece em nenhum campo | `ExpedienteDisponibilidadeIT` |
| A30 | folga `FOLGA` de 05/12 00:00 a 06/12 00:00 (sábado inteiro) | `GET /disponibilidade?data=2026-12-05` | `aberto: false`, `motivoFechado: FOLGA` | `ExpedienteDisponibilidadeIT` |
| A31 | folga só de 05/12 14:00 a 16:00 | `GET /disponibilidade?data=2026-12-05` | `aberto: true`, `motivoFechado: null`, `inicio`, `fim` e `almoco` normais; só somem os inícios que invadiriam 14:00–16:00 (folga parcial não é pública) | `ExpedienteDisponibilidadeIT` |
| A32 | uma `FOLGA` e umas `FERIAS` cobrindo o mesmo sábado inteiro | `GET /disponibilidade` desse sábado | `motivoFechado: FERIAS` | `ExpedienteDisponibilidadeIT` |
| A33 | dois barbeiros; só o Eduardo de férias em 22/12 | `GET /disponibilidade?data=2026-12-22` sem `barbeiroId` | o Eduardo vem `aberto: false, FERIAS`; o outro, `aberto: true` com os horários dele | `ExpedienteDisponibilidadeIT` |

**Telas (UX especifica depois da aprovação):** na agenda do cliente, dia com `aberto: false` mostra "Fechado" (com "Férias" ou "Folga" quando vier o tipo) em vez de "Sem horário", e dia aberto mostra o expediente e o almoço. No painel, o formulário de folga pede início, fim, tipo e motivo; ao escolher o período, chama a prévia e mostra os conflitos; "Salvar e manter os agendamentos" envia `manterReservas: true`; depois de salvar, lista as reservas mantidas com o atalho para cada uma. Os critérios de tela entram na spec de UX.

## 6. Entregas

| Módulo | O que |
|---|---|
| banco | migração nova (próxima `V` livre): `folga.tipo varchar(10) NOT NULL DEFAULT 'FOLGA'` com CHECK (`FERIAS`, `FOLGA`), `atualizado_por uuid NULL REFERENCES usuario(id)`, `atualizado_em timestamptz NULL`. Só acrescenta |
| `agenda` | `ConsultaDisponibilidade` e `CalculadoraDeHorarios`: monta o `ExpedienteDoDia` por barbeiro com a jornada e as folgas que já carrega (sem consulta nova); `CadastrosDoPainel`: `criarFolga` com `tipo` e `manterReservas`; `alterarFolga` nova (mesma trava); `conflitosDaFolga` (leitura); `Reservas.confirmarPagamento` confere folga quando a reserva está `EXPIRADO` (E7) |
| `admin` | `BarbeirosAdminController`: `PUT /folgas/{folgaId}` e `GET /folgas/conflitos`; `CadastroDeFolgas` com auditoria `FOLGA_ALTERADA`; DTOs `FolgaRequest` (`tipo`, `manterReservas`), `FolgaAdmin` (`tipo`, `atualizadaEm`), `FolgaSalva`, `ConflitoDeFolga` |
| `comum.erro` | `ExcecaoDeNegocio` carrega a extensão `conflitos` no `FOLGA_COM_RESERVAS`; o log do tratador não imprime extensões |
| front | `schema.d.ts` e `rotas-permitidas.ts` já regenerados nesta PR; telas na spec de UX |
| docs | guia de testes com a saída real; `painel-admin.md` (ação `FOLGA_ALTERADA`); `contexto.md` (H27 continua) |

## 7. Riscos e cuidados

- **Esquecer reservas mantidas.** A resposta lista as mantidas e o calendário do mês mostra reservas e folga no mesmo dia.
- **Prévia desatualizada.** A prévia não trava. A gravação relê os conflitos dentro da trava e decide.
- **Rota literal ao lado de rota com id.** `/folgas/conflitos` e `/folgas/{folgaId}`: no Spring a rota literal ganha. Não existe `GET /folgas/{folgaId}`.
- **Dado pessoal no erro.** `clienteNome` só para `ADMIN`, sem log (R30).

## 8. Perguntas para o Kevin

1. Com `manterReservas`, a reserva ainda aguardando pagamento dentro das férias pode ser paga e confirmada (proposta) ou deve deixar de aceitar pagamento?
2. O barbeiro terceiro continua sem cadastrar as próprias folgas (H27, proposta) ou pode pedir para o dono aprovar?
3. O cliente com horário dentro das férias deve ser avisado automaticamente no futuro? Hoje a proposta é o dono avisar pelo WhatsApp.
4. Na agenda pública, pode aparecer "Férias" e "Folga" (proposta) ou prefere só "Fechado", sem dizer o motivo?

## 9. Pronto quando

- [ ] todos os critérios com teste verde no `./mvnw verify`
- [ ] `scripts/conferir-spec.sh 15 --exigir` verde e a fatia 15 incluída no laço do CI
- [ ] [checklist da PR](checklist-pr.md) completo
- [ ] spec de UX das telas de férias e folgas aprovada

