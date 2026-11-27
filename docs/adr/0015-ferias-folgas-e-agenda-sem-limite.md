# 0015. Férias e folgas no painel, expediente público, mais tempo para pagar (2 min até 10 vezes) e agenda sem limite de data

- Status: **proposto** (revisão de arquitetura, 26/11/2026; ampliado em 27/11/2026), aguarda o ok do Kevin
- Data: 2026-11-27
- Decisores: revisão de arquitetura (dona), Kevin Richard (produto)
- Ponto de partida: `develop` em `e91e85d` (contrato 1.11.0, migrações até `V14`)
- Relacionados: ADR 0003 (constraint e expiração), ADR 0005 (erros RFC 9457), ADR 0013 (Dono/Admin e isolamento), D197 e D199 (agenda sem antecedência e sem horizonte), H27 (só o dono mexe em jornada e folgas)
- Spec: [Fatia 15](../sdd/fatia-15-ferias-folgas-e-horizonte.md) · Decisões: D202 a D205 (a D205 revoga a D190 e altera a D188)

## Contexto

O Kevin pediu duas coisas para a Barbearia Altas Horas:

1. **O cliente marca em qualquer data futura.** A agenda não pode ter limite de dias à frente.
2. **Férias e folgas no painel.** O dono (Carlos Eduardo Candido, papel `ADMIN`, "Dono/Admin" pelo ADR 0013) cadastra períodos com início, fim, motivo opcional e tipo (férias ou folga), edita e remove. Nesses períodos os horários somem para o cliente. Quando já existem agendamentos no período, o sistema avisa e lista os conflitos, **sem cancelar nada sozinho**.
3. **"Fechado" em vez de "indisponível".** A tela do cliente precisa saber se o dia está fechado (fora do expediente, folga ou férias) ou só sem horário livre, e mostrar o expediente e o almoço.
4. **Mais tempo para pagar (27/11/2026).** O tempo extra passa a ser **2 minutos por pedido, até 10 vezes**, com aviso antes de o prazo acabar (WCAG 2.2.1, opção "Extend": aviso antes do fim, ação simples e pelo menos 10 vezes). Hoje é 1 minuto, uma vez só (D188, D190).

O que já existe (conferido no código e no contrato em `e91e85d`):

- **Horizonte: já não existe.** A D197 (24/11/2026) revogou a antecedência mínima e o horizonte. A única regra de tempo é "o início é depois de agora" (`ConsultaDisponibilidade.java:69` e `ReservarHorario.java:316`, `422 ANTECEDENCIA_INSUFICIENTE`). O contrato diz isso em `openapi.yaml:120-122` (`/disponibilidade`) e `openapi.yaml:835-836` (reserva manual). O código do backend não lê `horizonte_dias` em nenhum lugar. Em produção, `GET /disponibilidade` para 05/01/2028 e 04/06/2030 respondeu `200` com horários em 26/11/2026.
- **Sobras do horizonte, todas sem efeito:** a coluna `configuracao_agenda.horizonte_dias` continua no banco, sem uso, com `DEFAULT 30` e o CHECK de 1 a 365 (`V1__agenda.sql:104,113`, `V14__prorrogacao_e_agenda_sem_limites.sql:7,20`), de propósito, pela D199 (expandir e contrair); o código `FORA_DO_HORIZONTE` está `deprecated` (`openapi.yaml:1646-1653`, `contrato-api.md:443`); o campo `horizonteDias` está `deprecated`, `writeOnly` e é ignorado no `PUT /admin/configuracao` (`openapi.yaml:2211`, `ConfiguracaoRequest.java:23`); o texto genérico `FORA_DO_HORIZONTE` continua no front (`mensagens.ts:43`). No front, `DIAS_DA_BUSCA = 30` (`Agendar.tsx:28`) é só o tamanho da busca "Ver o próximo dia com horário", não uma regra: o seletor "Outro dia" tem só `min` (`Agendar.tsx:306-307`).
- **Folgas: já existem** (Fatia 5): `GET` e `POST /admin/barbeiros/{id}/folgas` e `DELETE /admin/barbeiros/{id}/folgas/{folgaId}`, só `ADMIN`. O `POST` recusa com `422 FOLGA_COM_RESERVAS` quando há reserva ativa no período (`CadastrosDoPainel.java:263-272`), trava a linha do barbeiro com `FOR UPDATE` antes de conferir (`CadastrosDoPainel.java:252`) e grava a auditoria na mesma transação (`CadastroDeFolgas.java:40-52`). A tabela `folga` tem `inicio`, `fim`, `motivo`, `criado_por` e `criado_em` (`V1__agenda.sql:171-182`); não tem tipo. A disponibilidade já desconta as folgas.
- **Disponibilidade hoje:** `GET /disponibilidade` devolve, por barbeiro, só `horarios` (`openapi.yaml`, schema `Disponibilidade`). Um dia sem horário pode ser fechado ou lotado, e a tela não sabe qual. A jornada é uma lista de `IntervaloJornada` (`diaSemana`, `horaInicio`, `horaFim`); o almoço é o vão entre dois intervalos do mesmo dia (H1: terça a sábado, 07h00 às 20h00, almoço das 12h00 às 13h00), o mesmo critério do `ALMOCO` da grade do painel (`VisaoDaAgenda.java:173`).
- **Prorrogação hoje (14A):** `POST /agendamentos/{id}/prorrogacao` e `/agendamentos/guest/{id}/prorrogacao` somam 60 s uma vez, na janela `[expiraEm − 60 s, expiraEm)` (D188, D189); o tempo vem de `app.agenda.prorrogacao-pagamento: PT60S` (`application.yml:50`, D190); a trilha é `reserva.prorrogada_em` (D192); a cobrança acompanha pelo evento `PrazoDaReservaProrrogado` (D191). Na criação, o gateway recebe a validade igual ao `expiraEm` da reserva naquele momento (`CriarCobranca.java:173-174`); o Asaas só usa a data (`dueDate`, `AsaasGateway.java:94`).
- **Lacuna encontrada:** o pagamento tardio de uma reserva já `EXPIRADO` volta a `CONFIRMADO` se a constraint de reservas deixar (`Reservas.java:148-171`, `StatusReserva.java:26`); só o `23P01` vira `HORARIO_OCUPADO` e a pendência `PAGA_APOS_EXPIRACAO` (`ProcessarPagamento.java:78-106`). A constraint só olha outras reservas, não enxerga folgas, e esse caminho não trava a linha do barbeiro. Então uma folga criada depois da expiração pode ganhar, sem ninguém ver, uma reserva confirmada dentro dela. Isso já pode acontecer hoje e fica mais provável com férias longas.

## Decisão

### 1. Agenda sem limite de data: confirmar a D197, sem mudar o contrato

- Não há data máxima para marcar, nem para o cliente, nem para o visitante, nem na reserva manual. A regra continua sendo só "o início é depois de agora".
- **Nenhuma trava nova contra abuso.** Cada `GET /disponibilidade` já consulta **um dia só** e cada `GET /admin/agenda/mes`, um mês. Não existe consulta por faixa de datas que precise de limite. O "Ver o próximo dia com horário" continua procurando 30 dias por vez no front (limite da busca, não regra).
- Nada sai do contrato agora. `FORA_DO_HORIZONTE` e `horizonteDias` continuam `deprecated` até a próxima versão maior, e a remoção física da coluna `horizonte_dias` continua pendente, como a D199 já previa (depois de uma release estável).

### 2. Tipo no período: `FERIAS` ou `FOLGA`

- A folga ganha o campo `tipo`, com os valores `FERIAS` e `FOLGA`. O padrão é `FOLGA`, então quem não manda o campo (o front de hoje) continua funcionando.
- O tipo é só um rótulo para o painel e o calendário. As duas coisas bloqueiam a agenda do mesmo jeito.
- O recurso continua sendo `/admin/barbeiros/{id}/folgas`. Não criamos um recurso `/ferias` separado, porque as regras são idênticas.
- Banco: a próxima migração livre (provavelmente `V15`) acrescenta `folga.tipo varchar(10) NOT NULL DEFAULT 'FOLGA'` com CHECK nos dois valores, e `atualizado_por` e `atualizado_em`, que podem ser nulos. A migração só acrescenta, e a versão anterior da API continua funcionando com ela.

### 3. Editar: `PUT /admin/barbeiros/{id}/folgas/{folgaId}`

- `PUT` substitui o período inteiro (`inicio`, `fim`, `motivo`, `tipo`). Escolhemos `PUT` em vez de `PATCH` porque o formulário do painel sempre manda todos os campos, e porque já é o padrão do painel (`PUT /admin/servicos/{id}`, `PUT /admin/barbeiros/{id}`).
- As validações são as mesmas da criação: `fim` depois de `inicio` e o período não pode estar todo no passado (`400 VALIDACAO_INVALIDA`); `motivo` até 200 caracteres.
- A folga de outro barbeiro, ou que não existe, é `404` (não confirma que o id existe, R23).
- Sem campo `version`: há um único dono, e a última gravação vale. Se aparecer um segundo administrador, o controle de versão entra numa versão menor.

### 4. Conflitos: avisar e listar, nunca cancelar sozinho

- **Conflito** é uma reserva **ativa** do barbeiro que se sobrepõe ao período: `CONFIRMADO`, ou `AGUARDANDO_PAGAMENTO` com o prazo ainda aberto (a mesma definição de hoje, R15). Os intervalos são meio abertos (R2): encostar não é conflito.
- **Prévia:** `GET /admin/barbeiros/{id}/folgas/conflitos?inicio=&fim=` lista os conflitos antes de salvar. Cada item traz: `reservaId`, `inicio`, `fim`, o serviço (`id` e `nome`), o nome de exibição do cliente (`Sem nome` na reserva manual sem nome ou já anonimizada), a `origem` (`CLIENTE`, `GUEST` ou `MANUAL`) e o `status`. Não traz e-mail, CPF, WhatsApp nem observação. A consulta só lê e não trava nada. A resposta é uma lista, vazia quando não há conflito. Usa as mesmas validações de período (`400`).
- **Padrão: recusar.** `POST` ou `PUT` com conflito continua `422 FOLGA_COM_RESERVAS`, e agora o corpo RFC 9457 traz a extensão `conflitos[]`, com os mesmos itens da prévia (ADR 0005). Nada é gravado.
- **Gravar mesmo assim: `manterReservas: true`.** Com essa marcação no corpo do `POST` ou do `PUT`, o período é gravado e a resposta (`201` ou `200`) traz `reservasMantidas[]`, com a mesma lista, para o painel avisar. As reservas **continuam como estão** e acontecem, a menos que o dono cancele cada uma pelos caminhos que já existem (o cancelamento continua só do `ADMIN`). Horários novos no período ficam bloqueados para todo mundo (cliente, visitante e reserva manual), porque a disponibilidade e o `ReservarHorario` já descontam as folgas.
- `manterReservas` só vale na escrita (`writeOnly`), o padrão é `false` e o campo nunca é guardado no banco.
- **Pagamento tardio dentro de folga (fecha a lacuna):** uma reserva que já estava `EXPIRADO` e é paga depois **não** é reconfirmada se o horário dela cair numa folga. O `confirmarPagamento` da agenda, quando a reserva está `EXPIRADO`, trava a linha do barbeiro com `FOR SHARE` (a mesma trava do `ReservarHorario`), confere a folga e responde `HORARIO_OCUPADO`. A cobrança vira `PAGA_APOS_EXPIRACAO`, como já acontece quando o horário foi ocupado por outra reserva, e aparece nas pendências do painel para devolução. Uma reserva ainda `AGUARDANDO_PAGAMENTO` e mantida com `manterReservas` é confirmada normalmente se for paga dentro do prazo, porque o dono escolheu mantê-la.

### 5. Quem pode: só o dono

- Criar, editar, remover, listar e ver a prévia de folgas é **só `ADMIN`** (o dono), como no contrato de hoje e na H27 do ADR 0013 ("o barbeiro edita as próprias folgas? decidido: não").
- O `BARBEIRO` recebe `403 ACESSO_NEGADO` pela rota `/admin/barbeiros/**`, como hoje (ADR 0013, matriz de acesso). Ele continua vendo as próprias folgas no `/admin/agenda` e no `/admin/agenda/mes` (o campo `folga` do dia), pela política `AcessoAgenda`.
- `{folgaId}` que não pertence ao `{id}` informado é `404`.

### 6. Concorrência e auditoria

- `POST` e `PUT` travam a linha do barbeiro com `FOR UPDATE` **antes** de conferir as reservas, como o `criarFolga` de hoje. O `ReservarHorario` trava a mesma linha com `FOR SHARE`. Folga e reserva nova nunca passam juntas, nem no `PUT` que aumenta o período.
- Com `manterReservas: true`, a lista de `reservasMantidas` é lida **dentro** da trava, então ela é exata no instante da gravação.
- O `DELETE` continua sem trava, porque remover só libera horário.
- Auditoria na **mesma transação**, como as outras ações do painel: `FOLGA_CRIADA`, `FOLGA_ALTERADA` (nova) e `FOLGA_REMOVIDA`. O detalhe guarda `barbeiroId`, `tipo` e, quando houver, `reservasMantidas` (só a **quantidade** e os ids das reservas). O motivo da folga e o nome dos clientes **nunca** entram na auditoria nem no log (R30).

### 7. Expediente público na disponibilidade

- Cada barbeiro da resposta de `GET /disponibilidade` ganha `expediente` (schema `ExpedienteDoDia`): `aberto`, `motivoFechado`, `inicio`, `fim` e `almoco` (`{ inicio, fim }` ou `null`). Fica **por barbeiro**, e não na raiz, porque a jornada e as folgas são de cada barbeiro: com dois barbeiros, um pode estar de férias e o outro, atendendo.
- Tudo é derivado do que já existe: `inicio` é o início do primeiro intervalo da jornada naquele dia da semana, `fim` é o fim do último, e `almoco` é o vão entre os dois primeiros intervalos (com um intervalo só, `null`).
- `aberto: false` quando:
  - a jornada não tem intervalo nesse dia da semana: `motivoFechado: FORA_DO_EXPEDIENTE` (domingo e segunda, hoje);
  - folgas ou férias cobrem o expediente **inteiro** do dia: `motivoFechado: FECHADO`, sem dizer qual dos dois.
- Folga que cobre só **parte** do dia não aparece: o dia continua `aberto` e só os horários somem. Assim o público não fica sabendo de uma consulta de duas horas no meio da tarde.
- Fechado: `inicio`, `fim` e `almoco` vão `null`, para não sugerir um horário que não vale.
- Datas com segundos e offset, no fuso da barbearia, como os `horarios` que já existem. Os nomes seguem o contrato: `expediente` já é o nome usado no calendário do painel (`ResumoDoMes`) e `almoco` já é o nome da situação `ALMOCO` da grade.
- **O que é público e o que não é (decidido em 27/11/2026, confirmado por UX).** O público vê só **"Fechado"** e o almoço. O **tipo** (`FOLGA` ou `FERIAS`) e o **texto** do `motivo` ficam só no `/admin` (`GET /admin/barbeiros/{id}/folgas`). Motivos:
  - minimização (LGPD art. 6º, III): o cliente só precisa saber que o dia está fechado para escolher outro;
  - "férias" de um barbeiro, principalmente de um terceiro, diz que a pessoa está fora por um período, e isso não é da conta do público;
  - o tipo não muda nada na agenda pública: os dois bloqueiam igual.
  - Alternativa descartada: mostrar `FOLGA` ou `FERIAS` ao público. Ajudaria a orientar o cliente, mas revela mais do que precisa.
- Campos opcionais no schema (quem lê a 1.11.0 não quebra), mas a API sempre os envia desde a 1.12.0.

### 8. Mais tempo para pagar: 2 minutos por pedido, até 10 vezes (revoga a D190, altera a D188)

- **Quanto e quantas vezes.** Cada pedido soma **120 s** ao prazo que está correndo (`expiraEm + 120 s`), até **10 pedidos** por reserva. Os dois valores são propriedades da aplicação: `app.agenda.prorrogacao-pagamento: PT2M` (já existe, era `PT60S`) e `app.agenda.prorrogacao-maximo: 10` (nova). Não ganham campo no painel. O prazo inicial de 3 minutos (`expiracaoPagamentoMin`) não muda.
- **Janela igual.** O pedido só vale no último minuto, `[expiraEm − 60 s, expiraEm)`, com a reserva `AGUARDANDO_PAGAMENTO`. O aviso aparece aos 60 s, e a pessoa tem mais de 20 s para agir, como a WCAG pede. Depois de somar, o prazo volta a ter mais de 60 s, então a janela fecha até o próximo último minuto. Por isso dois pedidos seguidos nunca somam duas vezes.
- **Repetição segura.** O cabeçalho `X-Idempotency-Key` (UUID) é **opcional**. Usamos o mesmo nome do cabeçalho da cobrança, para seguir o contrato, em vez de `Idempotency-Key`. Repetir o **último** pedido com a mesma chave devolve `200` com o mesmo corpo, sem somar nem contar. Uma chave nova, ou nenhuma, é um pedido novo. Sem a chave, uma repetição logo depois de somar cai fora da janela (`422 PRORROGACAO_FORA_DA_JANELA`) e também não soma.
  - O cabeçalho não pode ser obrigatório, porque isso quebraria o front de hoje.
  - **Compatibilidade:** na 1.11.0, a repetição sem chave devolvia `200` ("já prorrogada"). Agora ela devolve `422 PRORROGACAO_FORA_DA_JANELA`. Nos dois casos nada é somado. O front da mesma entrega manda a chave, e o front antigo só mostrava o botão uma vez (`usada`).
- **O 11º pedido** é `422 PRORROGACAO_ESGOTADA`, um código novo, e o prazo não muda.
- **Ordem das checagens.** A ordem de hoje continua, com o código novo logo depois do `RESERVA_EXPIRADA`:
  1. `404`;
  2. `422 TRANSICAO_INVALIDA`;
  3. `200`, quando é a repetição com a mesma chave;
  4. `422 RESERVA_EXPIRADA`;
  5. `422 PRORROGACAO_ESGOTADA`;
  6. `422 PRORROGACAO_FORA_DA_JANELA`;
  7. grava (`201`).
- **Resposta.** O objeto `prorrogacao` ganha `usadas` (inteiro), `maximo` (10) e `restantes` (`maximo − usadas`), e `segundos` passa a 120. O `usada` (booleano) fica `deprecated` e vale `true` quando `usadas > 0`. Com os pedidos esgotados, o objeto continua presente, com `restantes: 0`.
- **Banco.** A próxima migração livre acrescenta:
  - `reserva.prorrogacoes smallint NOT NULL DEFAULT 0` com CHECK `>= 0`; o máximo fica na propriedade, não no CHECK;
  - `reserva.prorrogacao_chave uuid NULL`, a chave do último pedido.
  - A coluna `prorrogada_em` continua com o horário do **último** pedido (D192).
  - Preenchimento: `prorrogacoes = 1` onde `prorrogada_em` não é nulo.
  - A migração só acrescenta, então a versão anterior continua funcionando.
- **Concorrência.** O pedido trava a linha da reserva (`FOR UPDATE`), como hoje. Dois pedidos ao mesmo tempo somam uma vez só: o segundo acorda já fora da janela.
- **Pix e cobrança.** O copia e cola gerado **não muda** (D191 continua). Na criação da cobrança, a validade enviada ao gateway passa a ser o **prazo máximo possível**: o início do prazo da reserva + `expiracaoPagamentoMin` + `prorrogacao-maximo` × `prorrogacao-pagamento`. Com os valores de hoje, são 3 min + 10 × 2 min = **23 min**. Essa conta é **derivada das propriedades**, sem número fixo no código.
  - Quem segura o horário continua sendo o `expiraEm` da **reserva**, e o `cobranca.expira_em` continua acompanhando a reserva pelo evento (D191).
  - O pagamento que chega depois de a reserva expirar cai no fluxo que já existe: reconfirma se o horário estiver livre, ou vira `PAGA_APOS_EXPIRACAO` (R14 e a seção 4 deste ADR).
- **Trilha (D192):** cada pedido publica `PrazoDaReservaProrrogado` e escreve no log técnico só o id e o número do pedido (R30).

### 9. Contrato `1.11.0 → 1.12.0`, só acréscimos

- `FolgaRequest`: `tipo` (opcional, padrão `FOLGA`) e `manterReservas` (opcional, `writeOnly`, padrão `false`).
- `Folga` (resposta): `tipo` e `atualizadaEm` (nulo até a primeira edição). *Ajuste da 1.12.1 (D206): `tipo` e `reservasMantidas` são opcionais na resposta até o código da Fatia 15 estar em produção; ausentes = `FOLGA` e lista vazia.*
- Schema novo `FolgaSalva` (resposta do `POST` e do `PUT`): `Folga` mais `reservasMantidas[]` (vazio quando não houve conflito).
- Schema novo `ConflitoDeFolga` (item da prévia, da extensão do `422` e de `reservasMantidas`).
- `Problema`: extensão opcional `conflitos[]`, presente só no `FOLGA_COM_RESERVAS`.
- Rotas novas: `PUT /admin/barbeiros/{id}/folgas/{folgaId}` e `GET /admin/barbeiros/{id}/folgas/conflitos`.
- `Disponibilidade.barbeiros[].expediente` (schema novo `ExpedienteDoDia`), opcional; `motivoFechado` só `FORA_DO_EXPEDIENTE` ou `FECHADO`.
- Prorrogação: `prorrogacao.usadas`, `maximo` e `restantes`; `usada` vira `deprecated`; cabeçalho opcional `X-Idempotency-Key` nas duas rotas; código novo `422 PRORROGACAO_ESGOTADA` (já entra no enum `CodigoErro` do Java e no catálogo de mensagens do front, que o CI exige iguais ao contrato).
- Um código de erro novo (`PRORROGACAO_ESGOTADA`) e nenhum campo obrigatório novo na entrada. O front de hoje continua funcionando sem mudança (a única diferença está na compatibilidade da seção 8).

## Consequências

- **Bom:** o dono marca as férias sem precisar cancelar ninguém antes; vê quem seria afetado antes de salvar; o cliente nunca vê horário dentro das férias; nada é cancelado sem uma ação explícita do dono.
- **Bom:** a lacuna do pagamento tardio dentro de folga fica fechada.
- **Custo:** zero. Nenhum serviço ou biblioteca nova.
- **Risco:** com `manterReservas`, o dono pode esquecer reservas dentro das férias. Para isso, a resposta lista as mantidas, e o calendário do mês mostra reservas e folga no mesmo dia (`ResumoDoMes`).
- **Risco:** o nome do cliente vai no corpo do `422` e da prévia, só para o `ADMIN` (o mesmo dado que ele já vê no `AgendamentoAdmin`). O tratador de erros não pode logar a extensão `conflitos` (R30), e a resposta vai com `Cache-Control: no-store`.
- **Bom:** a tela do cliente mostra "Fechado" (e o porquê) em vez de "sem horário", e mostra o expediente e o almoço do dia.
- **Bom:** quem paga pelo celular ganha até 20 minutos a mais, sempre com aviso (WCAG 2.2.1).
- **Risco:** o horário pode ficar preso até 23 minutos sem pagamento. É aceito porque segurar o horário depende de a pessoa pedir no último minuto, a cada vez.
- **Não muda:** o cliente continua sem cancelar nem remarcar (R18); avisar o cliente afetado continua sendo pelo WhatsApp, à mão, pelo dono.

## Alternativas descartadas

- **Cancelar automaticamente as reservas do período:** descartado. O Kevin pediu que nada seja cancelado sozinho, e cancelar reserva paga gera devolução.
- **Recurso `/admin/barbeiros/{id}/ferias` separado:** descartado. Teria as mesmas regras e duplicaria código e contrato.
- **`PATCH` com JSON Merge Patch:** descartado. O formulário sempre manda tudo, e o painel já usa `PUT`.
- **Criar uma trava de "data máxima" para evitar abuso:** descartado. Cada consulta já cobre um dia só, e o Kevin pediu agenda sem limite.

## Perguntas para o Kevin

1. Com `manterReservas`, a reserva ainda `AGUARDANDO_PAGAMENTO` dentro das férias deve poder ser paga e confirmada (proposta deste ADR) ou deve deixar de aceitar pagamento?
2. O barbeiro terceiro (`BARBEIRO`) continua sem cadastrar as próprias folgas (H27), ou ele pode pedir folga pelo painel para o dono aprovar? A proposta é manter a H27.
3. O cliente que já tem horário dentro das férias deve ser avisado automaticamente no futuro? Hoje a proposta é o dono avisar pelo WhatsApp.
4. Mostrar "Restam N pedidos." abaixo do botão de mais tempo? É decisão de tela; o contrato já traz o `restantes`.
