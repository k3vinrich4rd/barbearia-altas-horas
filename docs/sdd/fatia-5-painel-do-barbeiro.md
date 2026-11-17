# 📐 Spec · Fatia 5 · Painel do barbeiro e do admin

| | |
|---|---|
| **Status** | ✅ **aprovada pelo Kevin em 17/11/2026**, com as 6 propostas da seção 7 (D-1 a D-6); em implementação, começando pela 5A |
| **Fatias do plano** | S09 (catálogo, jornada, folgas, configuração e auditoria) + S10 (agenda e fechamento) + as pendências de pagamento que a 4B deixou para o painel (R14, R22, H19/H20) |
| **Entrega em duas PRs** (proposta) | **5A** · operação do dia: agenda, fechamento, cancelamento, pendências de pagamento e auditoria (`feat/fatia-5a-operacao-do-dia`) · **5B** · cadastros: serviços, barbeiros, jornada, folgas e configuração (`feat/fatia-5b-cadastros`). A 5B só começa com a 5A na `main` |
| **Regras** | [R19 a R22](../contexto.md#3-regras-de-negócio) (falta, máquina de estados, "a partir do início", cancelamento pela barbearia), R14 (pagamento tardio vira pendência), R15 (vencida não ocupa horário), R23 (recurso alheio → 404), R30 (nada pessoal em log); H9 (barbeiro vê só a própria agenda), H19 e H20 (eventos presos); R3 a R5 e R7 (valores vindos de `configuracao_agenda`, jornada e folga) |
| **ADRs** | [0002](../adr/0002-comunicacao-entre-modulos.md) (API pública e eventos), [0003](../adr/0003-anti-double-booking-no-banco.md) (constraint), [0005](../adr/0005-erros-rfc-9457.md) (erros), [0011](../adr/0011-hexagonal-seletivo-no-monolito-modular.md) (CRUD em camadas simples) |
| **Não cobre** | telas do painel (Fatia 7) · criar contas de barbeiro ou mudar papéis (no beta há um barbeiro, o Eduardo) · estorno automático no gateway (pós-beta; no beta o estorno é manual) · avisar o cliente do cancelamento por WhatsApp ou e-mail (pós-beta) · retenção e limpeza da auditoria, CEP e direitos do titular (Fatia 6) · deploy (Fatia 8) |

## 1. Objetivo

O Eduardo (e, no futuro, outro barbeiro) abre o painel, vê **quem vem hoje ou nesta semana**, marca **concluído** ou **faltou**, **cancela** pela barbearia com motivo, resolve as **devoluções pendentes** e muda serviços, jornada, folgas e regras da agenda **sem deploy e sem mexer no banco**. Tudo o que ele faz fica na **auditoria**. Depois desta fatia, o ciclo do produto fecha de ponta a ponta: o cliente reserva e paga (Fatia 4) e a barbearia opera (esta).

## 2. Contrato

As rotas `/admin/**` já existem no [`openapi.yaml`](../contrato/openapi.yaml). Papéis: **A** = só ADMIN · **B** = ADMIN ou BARBEIRO. Todas exigem login, papel e senha inicial já trocada (`401` · `403 ACESSO_NEGADO` · `403 TROCA_DE_SENHA_OBRIGATORIA`); `CLIENTE` sempre recebe `403`.

### 5A · operação do dia

| Método | Rota | Quem | Sucesso | Erros (além dos de acesso) |
|---|---|---|---|---|
| `GET` | `/admin/agenda?data=&visao=&barbeiroId=` | B (BARBEIRO só a própria) | `200` lista de `AgendamentoAdmin` | `400` · `404` (barbeiro inexistente, alheio ou sem vínculo) |
| `PATCH` | `/admin/agendamentos/{id}/status` | B para `CONCLUIDO` e `NAO_COMPARECEU` (só a própria agenda) · **A** para `CANCELADO` | `200` `AgendamentoAdmin` | `400` · `403` (BARBEIRO cancelando) · `404` · `409 CONFLITO_DE_VERSAO` · `422 TRANSICAO_INVALIDA` · `422 MOTIVO_OBRIGATORIO` |
| `GET` | `/admin/pagamentos?status=&pagina=&tamanho=` | A | `200` `PaginaCobrancas` (itens `CobrancaAdmin`) | `400` |
| `POST` | `/admin/pagamentos/{id}/devolucao` | A | `200` `Cobranca` `DEVOLVIDA` | `404` · `422 TRANSICAO_INVALIDA` |
| `GET` | `/admin/pagamentos/eventos?pagina=&tamanho=` (**nova**) | A | `200` página de eventos em `REVISAO_MANUAL` | `400` |
| `POST` | `/admin/pagamentos/eventos/{id}/revisao` (**nova**) | A | `200` | `404` · `422 TRANSICAO_INVALIDA` (já revisado) |
| `GET` | `/admin/auditoria?pagina=&tamanho=` | A | `200` `PaginaAuditoria` | `400` |

### 5B · cadastros

| Método | Rota | Quem | Sucesso | Erros (além dos de acesso) |
|---|---|---|---|---|
| `GET` · `POST` | `/admin/servicos` | A | `200` · `201` `ServicoAdmin` | `400` · `409 NOME_JA_EXISTE` |
| `PUT` | `/admin/servicos/{id}` | A | `200` | `400` · `404` · `409 NOME_JA_EXISTE` · `409 CONFLITO_DE_VERSAO` |
| `GET` · `POST` | `/admin/barbeiros` | A | `200` · `201` `BarbeiroAdmin` | `400` · `409 USUARIO_JA_VINCULADO` (**novo**) |
| `PUT` | `/admin/barbeiros/{id}` | A | `200` | `400` · `404` · `409 USUARIO_JA_VINCULADO` · `409 CONFLITO_DE_VERSAO` |
| `PUT` | `/admin/barbeiros/{id}/jornada` | A | `200` | `400` · `404` · `422 JORNADA_SOBREPOSTA` |
| `GET` (**nova**) · `POST` | `/admin/barbeiros/{id}/folgas` | A | `200` folgas de hoje em diante · `201` `Folga` | `400` · `404` · `422 FOLGA_COM_RESERVAS` |
| `DELETE` | `/admin/barbeiros/{id}/folgas/{folgaId}` | A | `204` | `404` |
| `GET` · `PUT` | `/admin/configuracao` | A | `200` `ConfiguracaoAgenda` | `400` · `409 CONFLITO_DE_VERSAO` |

### Ajustes no contrato (v1.4.0), aplicados depois da aprovação

1. **`AgendamentoAdmin`** (resposta de `/admin/agenda` e do `PATCH`): o `Agendamento` mais `cliente { nome, whatsapp }` (sem e-mail, sem CPF) e `cobrancaStatus`. O barbeiro precisa saber quem vem; o `Agendamento` do cliente não tem isso.
2. **`CobrancaAdmin`** (itens de `PaginaCobrancas`): `Cobranca` **sem** `pix` e `cartao` (o painel não precisa do copia e cola nem do link) e com `devolvidaEm`, `reserva { id, inicio, servico, statusReserva, motivoCancelamento }` e `contato { nome, whatsapp }` do pagador, para o Eduardo saber para quem fazer o Pix de volta.
3. Rotas novas: `GET /admin/pagamentos/eventos`, `POST /admin/pagamentos/eventos/{id}/revisao` e `GET /admin/barbeiros/{id}/folgas` (o contrato só tinha criar e remover folga; sem listar, a tela não sabe o que remover).
4. Código novo `409 USUARIO_JA_VINCULADO` (a conta já é de outro barbeiro).
5. `ServicoRequest.precoCentavos`: `minimum` passa de 0 para **1** (a reserva exige `preco_centavos > 0`, `reserva_valores_ck`; um serviço de R$ 0 quebraria a reserva).
6. `PaginaAuditoria`: itens ganham `detalhe` (objeto técnico, sem dado pessoal). O IP **não** sai na API.
7. `ConfiguracaoAgenda` na entrada **não** aceita `fuso` (só leitura); mandar o campo é `400 CAMPO_DESCONHECIDO`, como todo DTO de entrada do projeto.

### Banco

`V5__painel.sql`: `ALTER TABLE configuracao_agenda ADD COLUMN version bigint NOT NULL DEFAULT 0` (o contrato já exige `version` na configuração e a V1 não criou a coluna). Nenhuma outra tabela muda: `auditoria_admin`, `folga`, `cobranca.devolvida_em/devolvida_por` e `reserva.cancelado_*/finalizado_*` já existem na V1.

## 3. Decisões de desenho

### 5A · operação do dia

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E1 | Onde mora o código do painel | Controllers, casos de uso do painel, composição das respostas e **auditoria** no módulo `admin`. As regras e as tabelas continuam com os donos, expostas por **API pública**: `agenda.PainelDaAgenda` (agenda, mudar status, e na 5B catálogo, jornada, folga e configuração), `pagamento.PendenciasDePagamento` (listar, devolver, eventos) e `conta.Contas` (a conta existe e tem o papel X?). `admin` só enxerga SQL de `auditoria_admin`; o `package-info` declara `allowedDependencies = {"agenda", "pagamento", "conta"}` | ADR 0002: ninguém lê a tabela do outro (A40 prova) |
| E2 | Quem pode o quê | Matriz abaixo. Duas camadas: o `SegurancaConfig` separa as rotas "só ADMIN" (continua barrando troca de senha pendente) e, no `PATCH` de status, que é uma rota só para três ações, o **caso de uso** recusa `CANCELADO` de BARBEIRO com `403 ACESSO_NEGADO`, porque o filtro não lê o corpo | cancelar mexe em dinheiro (devolução); fechar o dia não |
| E3 | O que o BARBEIRO enxerga | A conta é ligada ao barbeiro por `barbeiro.usuario_id` (`VinculoDoBarbeiro`, D53). O BARBEIRO só vê e fecha reservas **do próprio barbeiro**. `barbeiroId` de outro, reserva de outro ou conta sem vínculo → `404` (não confirma que existe, R23). Quem tem `ADMIN` (o Eduardo tem os dois papéis) vale como ADMIN: vê todos os barbeiros, ou o filtrado por `barbeiroId` | H9 vira regra no código mesmo com um só barbeiro, como o dossiê pede para o template |
| E4 | Que período é "o dia" e "a semana" | Dia civil no **fuso da barbearia** (`configuracao_agenda.fuso`), `[00:00, 24:00)` local; semana = **segunda a domingo** da semana ISO que contém `data`, `[segunda 00:00, segunda seguinte 00:00)`. Filtro por `inicio` da reserva. Os instantes são calculados com o `Clock` e o fuso, nunca com a data do servidor | uma reserva às 23:00 locais é 02:00 UTC do dia seguinte; filtrar em UTC jogaria ela no dia errado |
| E5 | Que reservas aparecem | Todas **menos** `EXPIRADO` e `AGUARDANDO_PAGAMENTO` já vencida (R15: não ocupam horário). Aparecem `AGUARDANDO_PAGAMENTO` válida, `CONFIRMADO`, `CONCLUIDO`, `NAO_COMPARECEU` e `CANCELADO` (o barbeiro vê o que foi desfeito). Ordem: `inicio`, depois `id`. Sem paginação (contrato devolve array): o teto natural é um barbeiro × 7 dias × 28 horários; com vários barbeiros o ADMIN filtra por `barbeiroId` | a agenda é o que importa para o dia, não o lixo de reservas não pagas |
| E6 | Projeção sem N+1 | Uma consulta `reserva JOIN servico JOIN barbeiro LEFT JOIN usuario LEFT JOIN reserva_guest` devolve a linha inteira; o `cobrancaStatus` vem de **uma** segunda consulta em lote em `pagamento` (`WHERE reserva_id IN (...)`, status da cobrança "viva" ou a mais recente), e o `admin` junta em memória. Total constante: 2 consultas, com 2 ou 200 reservas. Nome e WhatsApp saem para o ator da reserva (LGPD, dossiê seção 8); e-mail, token e CPF **nunca** | `agenda` não lê `cobranca`, `pagamento` não lê `reserva` (E1); o número de consultas não depende do tamanho |
| E7 | Fechamento: ordem das checagens | 1) escopo e existência (`404`); 2) papel para `CANCELADO` (`403`); 3) `MOTIVO_OBRIGATORIO` (`422`); 4) `version` (`409`); 5) máquina de estados e **"a partir do início"** (`422 TRANSICAO_INVALIDA`, R20/R21). `agora >= inicio` é **inclusivo** (às 09:00 em ponto já pode marcar). "Antes do início" **reaproveita** `TRANSICAO_INVALIDA` com texto próprio, sem código novo | a tela desabilita o botão antes do início; `409` antes de `422` porque, com a tela velha, recarregar resolve; motivo e papel são erro de pedido |
| E8 | `version` e concorrência | `MudancaStatusRequest.version` precisa ser igual ao `version` atual da reserva, senão `409 CONFLITO_DE_VERSAO`. A entidade `Reserva` já tem `@Version`: se outro pedido alterar a linha entre a leitura e o commit, o `OptimisticLockException` também vira `409 CONFLITO_DE_VERSAO`. Job de expiração, webhook e outro fechamento sobem o `version`, então todos derrubam a tela velha | dois cliques, ou o Eduardo no celular e no computador, nunca aplicam duas transições |
| E9 | Cancelamento pela barbearia | Só de `AGUARDANDO_PAGAMENTO` ou `CONFIRMADO` (a máquina decide). `motivo` obrigatório, `trim`, de 1 a 300 caracteres (vazio ou só espaços → `422 MOTIVO_OBRIGATORIO`; mais de 300 → `400`). Grava `motivo_cancelamento`, `cancelado_por` (o `sub` do token) e `cancelado_em`; o horário volta na disponibilidade porque `CANCELADO` sai do filtro da constraint. `motivo` mandado com `CONCLUIDO` ou `NAO_COMPARECEU` é ignorado e não gravado. `CONCLUIDO` e `NAO_COMPARECEU` gravam `finalizado_por` e `finalizado_em` | R22 e a coluna que já existe na V1 |
| E10 | Efeito no pagamento | A agenda publica **`ReservaCancelada(reservaId)`** (só o id, pelo outbox, na mesma transação do cancelamento, como `ReservaExpirada`). O `pagamento` escuta e aplica: cobrança `PAGA` → `DEVOLUCAO_PENDENTE`; cobrança `PENDENTE` → `CANCELADA`; qualquer outro status (`PAGA_APOS_EXPIRACAO`, `DEVOLVIDA`, `CANCELADA`, `FALHOU`) não muda. O `UPDATE` filtra pelo status de origem, então reentrega do evento não tem efeito | a agenda não depende do pagamento (a seta é a mesma da expiração, E24 da 4B); o outbox reenvia se o listener falhar. A ordem inversa (pagamento chegando depois do cancelamento) já está no D69: a cobrança vira `PAGA_APOS_EXPIRACAO`, que também é pendência (A24) |
| E11 | Auditoria | Cada ação **bem-sucedida** do painel grava uma linha em `auditoria_admin` (`ator_id` = `sub`, `acao`, `recurso`, `recurso_id`, `detalhe`, `ip` = `request.getRemoteAddr()`, `ocorrido_em` = `Clock`) **na mesma transação** da ação: se a gravação falhar, a ação desfaz; se a ação falhar (403, 404, 409, 422), nada é gravado. Leituras (`GET`) não são auditadas. O `detalhe` é uma lista fechada de campos técnicos (`de`/`para` do status, nomes dos campos alterados, ids): **nunca** motivo do cancelamento, nome, WhatsApp, e-mail ou token. Ações da 5A: `AGENDAMENTO_CONCLUIDO`, `AGENDAMENTO_NAO_COMPARECEU`, `AGENDAMENTO_CANCELADO`, `DEVOLUCAO_REGISTRADA`, `EVENTO_REVISADO`; da 5B: `SERVICO_CRIADO`, `SERVICO_ALTERADO`, `BARBEIRO_CRIADO`, `BARBEIRO_ALTERADO`, `JORNADA_SUBSTITUIDA`, `FOLGA_CRIADA`, `FOLGA_REMOVIDA`, `CONFIGURACAO_ALTERADA` | prova de quem fez o quê (R-STRIDE "repudiation"); o motivo fica na reserva, que já é o registro |
| E12 | O que é "pendência de pagamento" | `GET /admin/pagamentos` **sem** `status` lista as pendências: `PAGA_APOS_EXPIRACAO` e `DEVOLUCAO_PENDENTE`, **mais antigas primeiro** (quem espera há mais tempo aparece no topo; usa o índice parcial `cobranca_devolucao_ix`). Com `status`, qualquer `StatusCobranca`, mais recentes primeiro. `pagina` ≥ 0, `tamanho` de 1 a 50 (padrão 20), acima disso `400` (OWASP API4). O contato do pagador e o resumo da reserva vêm em lote pela API da agenda (`Reservas.resumos(ids)`), sem N+1 | o painel mostra primeiro o que o Eduardo precisa resolver |
| E13 | Devolução manual | No beta o estorno é **feito fora do app** (no banco do Eduardo, no Pix de volta); o sistema só **registra**. `DEVOLUCAO_PENDENTE` ou `PAGA_APOS_EXPIRACAO` → `DEVOLVIDA` com `devolvida_em` (`Clock`) e `devolvida_por` (`sub`), por um `UPDATE ... WHERE status IN (...)` atômico; zero linhas afetadas distingue `404` (não existe) de `422 TRANSICAO_INVALIDA` (já devolvida, paga, pendente...). Repetir a devolução é `422` e **não** altera `devolvida_em` | dois cliques não registram duas devoluções e a data original é preservada |
| E14 | Eventos em `REVISAO_MANUAL` | Lista paginada (mais antigos primeiro) com `id`, `tipo`, `recebidoEm`, `tentativas`, `ultimoErro` (só o `code`, como a V1 já grava) e o id da cobrança no provedor; **sem payload e sem dado pessoal**. "Marcar revisado" grava `resultado = 'REVISADO'` e `processado_em`, **não reprocessa**: serve para o Eduardo conferir o gateway e tirar o item da fila. Já revisado → `422` | sem isso a lista nunca esvazia; reprocessar à mão fica para a saída do beta, quando houver gateway real |
| E15 | Contagem de erros do corpo do `PATCH` | `status` fora de `CONCLUIDO`, `NAO_COMPARECEU`, `CANCELADO`, `version` ausente ou campo desconhecido → `400` (`VALIDACAO_INVALIDA` ou `CAMPO_DESCONHECIDO`) antes de qualquer regra | DTO por operação, como a Fatia 4 |

**Matriz de papéis** (a conferir em `AcessoAdminIT`):

| Ação | BARBEIRO | ADMIN |
|---|---|---|
| Ver a agenda | só a própria | todas |
| Marcar `CONCLUIDO` e `NAO_COMPARECEU` | só a própria | todas |
| Cancelar pela barbearia | 403 | sim |
| Pendências, devolução e eventos de pagamento | 403 | sim |
| Auditoria | 403 | sim |
| Serviços, barbeiros, jornada, folgas e configuração (5B) | 403 | sim |

### 5B · cadastros

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E16 | Serviço: apagar ou inativar | **Não existe `DELETE`**: o serviço sai de cena com `ativo = false` (some de `GET /servicos`, `POST /agendamentos` responde `422 SERVICO_INATIVO`). Preço de 1 centavo para cima; duração de 5 a 480 min (como a V1). Nome único sem diferença de maiúsculas (`servico_nome_uk`); a corrida de dois `POST` com o mesmo nome termina na violação `23505`, traduzida para `409 NOME_JA_EXISTE`, nunca `500` | reservas e cobranças antigas apontam para o serviço |
| E17 | Mudar preço ou duração não mexe no que já foi vendido | A reserva guarda `preco_centavos`, `fim` e `fim_ocupacao` (E6 da Fatia 4, R8): nenhuma reserva ou cobrança existente é reescrita. Só as reservas novas usam o valor novo | o cliente pagou aquele preço |
| E18 | Barbeiro e o vínculo com a conta | `usuarioId` precisa ser de uma conta que **existe e tem o papel `BARBEIRO`** (pergunta feita a `conta.Contas`, sem ler a tabela): senão `400 VALIDACAO_INVALIDA` com `errors[].field = usuarioId`. Conta já ligada a outro barbeiro → `409 USUARIO_JA_VINCULADO` (a `barbeiro_usuario_uk` do banco também pega a corrida). Inativar um barbeiro o tira de `GET /barbeiros` e da disponibilidade, mas **não cancela** reservas futuras: o ADMIN decide uma a uma pelo painel | cancelar em massa por engano seria pior do que o trabalho de conferir |
| E19 | Jornada | `PUT` **substitui** a semana inteira numa transação (apaga e grava de novo). Cada intervalo: `horaInicio < horaFim`, `HH:mm` válido (`25:00` → `400`), `diaSemana` de 1 a 7, no máximo 28 intervalos. Dois intervalos do mesmo dia que se sobrepõem → `422 JORNADA_SOBREPOSTA`; **encostar não é sobrepor** (`07:00–12:00` e `12:00–13:00` valem; é assim que o almoço da R4 é um buraco entre dois intervalos). Lista vazia é válida (barbeiro sem expediente). Reservas já feitas fora da nova jornada **continuam válidas** | consistente com a disponibilidade, que trata o intervalo como meio aberto |
| E20 | Folga: o que conta como "reserva ativa" | `CONFIRMADO` e `AGUARDANDO_PAGAMENTO` com prazo aberto (R15), que **sobrepõem** o período `[inicio, fim)` da folga contra `[inicio, fim_ocupacao)` da reserva (meio aberto, R2: folga que começa quando a reserva termina é permitida). `CANCELADO`, `EXPIRADO`, `CONCLUIDO`, `NAO_COMPARECEU` e pendente vencida não impedem. Folga com `fim <= inicio` ou toda no passado → `400`; `motivo` até 200 caracteres | o painel não pode esconder um horário que já tem cliente |
| E21 | Corrida folga × reserva | A folga não tem constraint como a reserva. Solução: a transação da folga trava a linha do `barbeiro` com `FOR UPDATE` antes de conferir as reservas, e a transação de `ReservarHorario` passa a fazer `SELECT 1 FROM barbeiro WHERE id = :id FOR SHARE` **antes** de conferir folga (E1 da Fatia 4). `FOR SHARE` não serializa duas reservas entre si (são compatíveis); só faz a reserva esperar uma folga em andamento, e a folga esperar as reservas em andamento. Resultado: nunca existem folga e reserva ativa no mesmo horário | sem o lock, as duas transações poderiam passar na checagem ao mesmo tempo; mexe numa linha de `ReservarHorario`, então o A9 da Fatia 4 precisa continuar verde |
| E22 | Configuração | Linha única (`id = 1`). `PUT` com `version`; valores validados como a `configuracao_agenda_valores_ck` (antecedência ≥ 0, intervalo 0 a 120, granularidade em 5/10/15/20/30/60, horizonte 1 a 365, expiração 5 a 60), erro por campo em `errors[]`. Vale na **próxima requisição** (nada em cache, nada de deploy). Reservas existentes não são reescritas (`expira_em` e `fim_ocupacao` já gravados). Grava `atualizado_em` e `atualizado_por` | D15: regra como dado |
| E23 | Erros do banco viram erros do contrato | `23505` (único) → `409` do código certo; `OptimisticLockException` → `409 CONFLITO_DE_VERSAO`; nenhum `500` por regra de negócio | a tela decide pelo `code` |

## 4. Critérios de aceite

Relógio fixo dos critérios de cadastro: terça, 17/11/2026, 08:00 (-03:00). Relógio dos critérios de agenda e fechamento: terça, **01/12/2026, 09:30** (-03:00). Datas conferidas por código: 30/11 é segunda, 01/12 terça, 06/12 domingo e 07/12 segunda. Jornada original: terça a sábado, 07:00–12:00 e 13:00–20:00; serviço de 30 min (Corte Clássico, R$ 45,00). Contas e nomes são fictícios.

**Massa dos critérios de agenda** (barbeiro A, o Eduardo; barbeiro B, conta `BARBEIRO` própria):

| Reserva | Quando (-03:00) | Status | Origem |
|---|---|---|---|
| R1 | 01/12 09:00 | `CONFIRMADO`, cobrança `PAGA` | cliente |
| R2 | 01/12 10:00 | `AGUARDANDO_PAGAMENTO`, vence 09:40 | visitante |
| R3 | 01/12 11:00 | `EXPIRADO` | visitante |
| R4 | 01/12 11:30 | `AGUARDANDO_PAGAMENTO`, venceu 09:25 | visitante |
| R5 | 01/12 14:00 | `CANCELADO` | cliente |
| R6 | 01/12 09:30, barbeiro B | `CONFIRMADO` | cliente |

### 5A · agenda e acesso

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | massa acima; token do barbeiro A | `GET /admin/agenda?data=2026-12-01` | `200` com **3** itens, nesta ordem: R1, R2, R5 (R3 e R4 não aparecem, E5); cada item com `status`, `inicio`, `servico`, `version`, `cliente.nome`, `cliente.whatsapp` e `cobrancaStatus`; **sem** e-mail, token ou CPF no corpo | `AgendaAdminIT` |
| A2 | massa acima; token de ADMIN | `GET /admin/agenda?data=2026-12-01` · o mesmo com `barbeiroId` do barbeiro B | **4** itens (R1, R6 às 09:30, R2, R5, nessa ordem) · só R6 | `AgendaAdminIT` |
| A3 | barbeiro A com reservas em 29/11 23:30, 30/11 00:00, R1, R2, R5, 06/12 23:30 e 07/12 00:00 | `GET /admin/agenda?data=2026-12-03&visao=SEMANA` (quinta) | **5** itens: 30/11 00:00, R1, R2, R5 e 06/12 23:30; ficam de fora 29/11 23:30 (domingo anterior) e 07/12 00:00 (segunda seguinte) | `AgendaAdminIT` |
| A4 | reserva às 23:00 de 01/12 (= 02:00 UTC de 02/12) e outra às 00:00 de 02/12 | `GET /admin/agenda` de 01/12 e de 02/12 | a das 23:00 aparece só em 01/12; a das 00:00 só em 02/12 (dia no fuso da barbearia, E4) | `AgendaAdminIT` |
| A5 | agenda do dia com 2 reservas · com 40 | contar SQL do `GET /admin/agenda` | o número de consultas é o mesmo (2, E6): não cresce com as reservas | `AgendaAdminIT` |
| A6 | barbeiro A autenticado como BARBEIRO · BARBEIRO sem vínculo · ADMIN | `barbeiroId` do barbeiro B · qualquer agenda · `barbeiroId` inexistente | `404` · `404` · `404`; o BARBEIRO sem `barbeiroId` vê só a própria | `AgendaAdminIT` |
| A7 | token de barbeiro | sem `data` · `data=31-12-2026` · `visao=MES` | `400 VALIDACAO_INVALIDA` nos três | `AgendaAdminIT` |
| A8 | sem token · token de CLIENTE · ADMIN e BARBEIRO com `trocar=true` | `GET /admin/agenda`, `PATCH .../status`, `GET /admin/pagamentos`, `GET /admin/auditoria` | `401` · `403 ACESSO_NEGADO` · `403 TROCA_DE_SENHA_OBRIGATORIA` em todas as rotas | `AcessoAdminIT` |
| A9 | token só de BARBEIRO · token com `ADMIN` e `BARBEIRO` (o Eduardo) | `GET /admin/pagamentos`, `POST /admin/pagamentos/{id}/devolucao`, `GET /admin/pagamentos/eventos`, `POST .../eventos/{id}/revisao`, `GET /admin/auditoria` | `403 ACESSO_NEGADO` em todas · `200` ou o erro da própria rota (nunca `403`) | `AcessoAdminIT` |

### 5A · fechamento do atendimento

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A10 | R1 `CONFIRMADO` com `version` v, relógio 09:30 | barbeiro A: `PATCH` `CONCLUIDO` com `version` v | `200`, `status = CONCLUIDO`, `version` = v+1; no banco `finalizado_por` = o barbeiro e `finalizado_em` = 09:30; a cobrança **continua `PAGA`** | `FechamentoAtendimentoIT` |
| A11 | R1 `CONFIRMADO` paga | `PATCH` `NAO_COMPARECEU` | `200`; a cobrança continua `PAGA` (valor retido, R19) e **nenhuma** pendência de devolução aparece em `GET /admin/pagamentos` | `FechamentoAtendimentoIT` |
| A12 | reserva `CONFIRMADO` com início às 09:30 e relógio 09:30 · a mesma com relógio 09:29 | `PATCH` `CONCLUIDO` | `200` (início inclusivo) · `422 TRANSICAO_INVALIDA`, status e `version` intactos (R21) | `FechamentoAtendimentoIT` |
| A13 | reservas em `AGUARDANDO_PAGAMENTO`, `EXPIRADO`, `CONCLUIDO`, `NAO_COMPARECEU` e `CANCELADO` | `PATCH` `CONCLUIDO` · `PATCH` `CANCELADO` (ADMIN) nas que a máquina proíbe | `422 TRANSICAO_INVALIDA` em cada uma; nada gravado, nenhuma auditoria (R20) | `FechamentoAtendimentoIT` |
| A14 | R1 com `version` 3 | `PATCH` com `version: 2` | `409 CONFLITO_DE_VERSAO`; a reserva continua `CONFIRMADO`, sem auditoria | `FechamentoAtendimentoIT` |
| A15 | R1 `CONFIRMADO` com `version` v | dois `PATCH` ao mesmo tempo com o mesmo `version`, um `CONCLUIDO` e um `NAO_COMPARECEU` | exatamente um `200`; o outro `409 CONFLITO_DE_VERSAO` (nunca `500`); o banco termina com um só status final e uma só linha de auditoria | `FechamentoConcorrenteIT` |
| A16 | barbeiro A | `PATCH` em R6 (do barbeiro B) · em id inexistente | `404` · `404` (R23); R6 intacta | `FechamentoAtendimentoIT` |
| A17 | barbeiro A como BARBEIRO, R1 `CONFIRMADO` | `PATCH` `CANCELADO` com motivo | `403 ACESSO_NEGADO`; R1 intacta | `FechamentoAtendimentoIT` |
| A18 | ADMIN | `PATCH` com `status: CONFIRMADO` · `status: EXPIRADO` · sem `version` · com campo `precoCentavos` | `400` nos quatro (`VALIDACAO_INVALIDA` ou `CAMPO_DESCONHECIDO`); nada gravado | `FechamentoAtendimentoIT` |

### 5A · cancelamento pela barbearia

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A19 | R1 `CONFIRMADO` com cobrança `PAGA`, ADMIN | `PATCH` `CANCELADO` com `motivo: "Barbeiro doente"` | `200`, `CANCELADO`, `motivoCancelamento` preenchido; no banco `cancelado_por` e `cancelado_em`; depois do evento a cobrança é **`DEVOLUCAO_PENDENTE`** e aparece em `GET /admin/pagamentos`; `GET /disponibilidade` volta a mostrar o horário das 09:00 | `CancelamentoAdminIT` |
| A20 | R2 `AGUARDANDO_PAGAMENTO` com cobrança `PENDENTE` · outra `AGUARDANDO_PAGAMENTO` sem cobrança | ADMIN cancela as duas | `200` nas duas; a cobrança vira `CANCELADA` (nunca `DEVOLUCAO_PENDENTE`); a sem cobrança só cancela | `CancelamentoAdminIT` |
| A21 | R1 `CONFIRMADO` | `PATCH` `CANCELADO` sem `motivo` · com `motivo: "   "` · com 301 caracteres | `422 MOTIVO_OBRIGATORIO` · `422 MOTIVO_OBRIGATORIO` · `400`; R1 e a cobrança intactas | `CancelamentoAdminIT` |
| A22 | cancelamento feito, com o listener de pagamento falhando na primeira entrega | olhar `event_publication` e reenviar as publicações incompletas | um `ReservaCancelada` gravado com **só o id** (nenhum dado pessoal); depois do reenvio a cobrança vira `DEVOLUCAO_PENDENTE`; reenviar de novo não muda nada | `CancelamentoAdminIT` |
| A23 | R1 cancelada pelo ADMIN | o cliente dono pede `GET /agendamentos/{id}` e `GET /me/agendamentos` | `200`, `status = CANCELADO` e `motivoCancelamento` visíveis para ele; o cliente B recebe `404` | `CancelamentoAdminIT` |
| A24 | reserva `AGUARDANDO_PAGAMENTO` com cobrança `PENDENTE`, cancelada pelo ADMIN | o gateway confirma o pagamento depois (webhook) | reserva continua `CANCELADO`; cobrança `PAGA_APOS_EXPIRACAO` (D69), que aparece como pendência; vale também na ordem inversa: pagamento confirmado **antes** do evento ser tratado termina no mesmo estado | `CancelamentoPagamentoTardioIT` |

### 5A · pagamentos pendentes

Massa: cobrança `PAGA` (criada 08:00), `DEVOLUCAO_PENDENTE` (criada 10:00), `PAGA_APOS_EXPIRACAO` (criada 09:00) e `CANCELADA`.

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A25 | massa acima, ADMIN | `GET /admin/pagamentos` (sem `status`) | `200` com **2** itens, `PAGA_APOS_EXPIRACAO` (09:00) antes de `DEVOLUCAO_PENDENTE` (10:00); cada um com `valorCentavos`, `reserva.inicio`, `reserva.servico`, `contato.nome` e `contato.whatsapp` do pagador; **sem** `pix`, `cartao`, CPF ou e-mail | `PendenciasPagamentoIT` |
| A26 | massa acima | `?status=PAGA` · `?status=CANCELADA` · `?status=XYZ` · `?tamanho=51` · `?pagina=-1` | só as pagas · só a cancelada · `400` · `400` · `400` (mais recentes primeiro nos filtros explícitos) | `PendenciasPagamentoIT` |
| A27 | 2 pendências · 20 pendências | contar SQL do `GET /admin/pagamentos` | o número de consultas é o mesmo (lista + contagem + resumos em lote): não cresce | `PendenciasPagamentoIT` |
| A28 | cobrança `DEVOLUCAO_PENDENTE`, relógio 09:30 | ADMIN: `POST /admin/pagamentos/{id}/devolucao` | `200`, `status = DEVOLVIDA`, `devolvidaEm` = 09:30; no banco `devolvida_por` = o ADMIN; some das pendências; auditoria `DEVOLUCAO_REGISTRADA` | `DevolucaoIT` |
| A29 | cobrança `PAGA_APOS_EXPIRACAO` (pagamento tardio sem horário, R14) | devolver | `200` `DEVOLVIDA` | `DevolucaoIT` |
| A30 | cobrança `PAGA` · `PENDENTE` · já `DEVOLVIDA` · id inexistente | devolver | `422 TRANSICAO_INVALIDA` × 3 · `404`; a `devolvida_em` da já devolvida **não muda** | `DevolucaoIT` |
| A31 | cobrança `DEVOLUCAO_PENDENTE` | dois `POST .../devolucao` ao mesmo tempo | exatamente um `200` e um `422`; uma só linha de auditoria | `DevolucaoIT` |
| A32 | 2 eventos em `REVISAO_MANUAL` (recebidos 08:00 e 09:00) e 1 `PROCESSADO` | `GET /admin/pagamentos/eventos` | `200` com os 2, o das 08:00 primeiro; campos `id`, `tipo`, `recebidoEm`, `tentativas`, `ultimoErro`; **nenhum** payload ou dado pessoal | `EventosRevisaoIT` |
| A33 | evento em `REVISAO_MANUAL` | `POST /admin/pagamentos/eventos/{id}/revisao` · repetir · id inexistente | `200` (`resultado = REVISADO`, `processado_em` preenchido, some da lista, auditoria `EVENTO_REVISADO`) · `422 TRANSICAO_INVALIDA` · `404` | `EventosRevisaoIT` |

### 5A · auditoria

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A34 | ADMIN faz: concluir, faltar, cancelar, devolver e revisar evento | olhar `auditoria_admin` | uma linha por ação com `ator_id` = o `sub`, `acao`, `recurso` (`agendamento`, `cobranca`, `evento_webhook`), `recurso_id`, `ocorrido_em` = relógio e o IP da requisição | `AuditoriaIT` |
| A35 | as mesmas cinco ações, com o motivo `"Cliente João pediu"` e a massa com nome, WhatsApp e e-mail | procurar no `detalhe` | nenhuma ocorrência de motivo, nome, WhatsApp, e-mail ou token; só campos técnicos (`de`/`para`, ids) | `AuditoriaIT` |
| A36 | a gravação da auditoria falha (gravador trocado por um que lança erro) | cancelar R1 | a requisição falha e a reserva continua `CONFIRMADO` com a cobrança `PAGA` (mesma transação, E11) | `AuditoriaIT` |
| A37 | ações recusadas (`403`, `404`, `409`, `422`) e `GET`s do painel | olhar `auditoria_admin` | nenhuma linha nova | `AuditoriaIT` |
| A38 | 30 linhas de auditoria, ADMIN · BARBEIRO | `GET /admin/auditoria` · `?tamanho=51` · com BARBEIRO | `200` mais recentes primeiro, `itens`, `pagina`, `tamanho` e `total`, sem `ip` · `400` · `403` | `AuditoriaIT` |

### 5A · transversais

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A39 | fluxo do painel (agenda, fechar, cancelar com motivo, devolver) com cliente e visitante da massa | ler o log da aplicação | nenhum nome, WhatsApp, e-mail, token, CPF ou motivo no log (R30) | `SemDadoPessoalNoLogPainelIT` |
| A40 | módulos | `./mvnw verify` | `admin` só depende de `agenda`, `pagamento` e `conta` pelas APIs públicas; ninguém depende de `admin`; `agenda` não conhece `pagamento` (só o evento `ReservaCancelada`); `admin` não usa `JdbcClient` nem `EntityManager` em tabela alheia | `ModulithTest`, `ArquiteturaTest` |

### 5B · serviços

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A41 | 5 serviços ativos e 1 inativo | `GET /admin/servicos` · `GET /servicos` (público) | `200` com **6** (com `ativo` e `version`) · só os **5** ativos | `ServicoAdminIT` |
| A42 | ADMIN | `POST /admin/servicos` `{nome: "Hidratação", duracaoMin: 30, precoCentavos: 3500}` | `201`, `Location`, `ativo = true`, `version = 0`, `precoFormatado = "R$ 35,00"`; aparece em `GET /servicos` | `ServicoAdminIT` |
| A43 | já existe "Corte Clássico/Social" | criar `"corte clássico/social"` · dois `POST` simultâneos com o mesmo nome novo | `409 NOME_JA_EXISTE` · um `201` e um `409` (nunca `500`) | `ServicoAdminIT` |
| A44 | ADMIN | criar com nome de 1 letra · duração 4 · duração 481 · preço 0 · campo `ativo` ou `id` no corpo | `400` com `errors[].field`; `CAMPO_DESCONHECIDO` para o campo extra; nada gravado | `ServicoAdminIT` |
| A45 | serviço com `version` 0 | `PUT` com `version: 0` e preço novo · de novo com `version: 0` · renomear para o nome de outro serviço · dois `PUT` simultâneos com a mesma `version` | `200` com `version: 1` · `409 CONFLITO_DE_VERSAO` · `409 NOME_JA_EXISTE` · um `200` e um `409` | `ServicoAdminIT` |
| A46 | serviço com reserva futura | `PUT` com `ativo: false` | `GET /servicos` deixa de listar; `POST /agendamentos` desse serviço → `422 SERVICO_INATIVO`; a reserva futura **continua** `CONFIRMADO` e aparece na agenda | `ServicoAdminIT` |
| A47 | reserva e cobrança de R$ 45,00 (4500) já feitas | mudar o preço para R$ 50,00 e a duração para 45 min | a reserva e a cobrança antigas continuam com 4500 e o mesmo `fim`; uma reserva nova sai com 5000 | `ServicoAdminIT` |

### 5B · barbeiros

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A48 | ADMIN | `GET /admin/barbeiros` · `POST` `{nomeExibicao: "Bruno"}` | lista com `ativo`, `version`, `usuarioId` e `jornada` em uma consulta só · `201` com `ativo = true` e `jornada: []` | `BarbeiroAdminIT` |
| A49 | conta `CLIENTE` · id de conta inexistente · conta `BARBEIRO` livre · conta já ligada ao Eduardo | `POST` ou `PUT` com `usuarioId` | `400 VALIDACAO_INVALIDA` (campo `usuarioId`) · `400` · `201` · `409 USUARIO_JA_VINCULADO` | `BarbeiroAdminIT` |
| A50 | barbeiro com reserva futura `CONFIRMADO` | `PUT` com `ativo: false` e `version` certa · `version` errada | `200`: some de `GET /barbeiros` e da disponibilidade, a reserva futura **continua** `CONFIRMADO` · `409 CONFLITO_DE_VERSAO` | `BarbeiroAdminIT` |
| A51 | barbeiro B sem conta ligada, conta `BARBEIRO` livre | ADMIN liga a conta; essa conta chama `GET /admin/agenda?data=...` | antes: `404` (sem vínculo); depois: `200` com a agenda do B | `BarbeiroAdminIT` |

### 5B · jornada e folgas

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A52 | disponibilidade de terça 01/12 com a jornada original: **24** horários (07:00–11:30 e 13:00–19:30, serviço de 30 min) | `PUT /admin/barbeiros/{id}/jornada` terça 08:00–12:00 e 13:00–18:00 | `200` com os dois intervalos; `GET /disponibilidade` de 01/12 passa a ter **18** horários (8 + 10), o primeiro 08:00 e o último 17:30 | `JornadaAdminIT` |
| A53 | ADMIN | terça 08:00–12:00 e 11:00–14:00 · terça 08:00–12:00 e 12:00–13:00 | `422 JORNADA_SOBREPOSTA`, jornada antiga intacta · `200` (encostar vale) | `JornadaAdminIT` |
| A54 | ADMIN | `horaFim` igual ou antes de `horaInicio` · `25:00` · `diaSemana: 8` · 29 intervalos · campo extra | `400` em todos; jornada antiga intacta | `JornadaAdminIT` |
| A55 | barbeiro com reserva futura às 19:00 | `PUT` com `[]` · `PUT` com jornada que termina às 18:00 | `200` vazia: a disponibilidade fica com **0** horários · `200`; nos dois casos a reserva das 19:00 **continua** `CONFIRMADO` e na agenda | `JornadaAdminIT` |
| A56 | ADMIN | `PUT` de jornada em barbeiro inexistente | `404` | `JornadaAdminIT` |
| A57 | disponibilidade de 01/12: 24 horários | `POST /admin/barbeiros/{id}/folgas` de 01/12 14:00 a 16:00 (-03:00) | `201`; a disponibilidade passa a **20** horários (somem 14:00, 14:30, 15:00 e 15:30; 16:00 continua livre); `POST /agendamentos` às 14:30 → `422 FORA_DO_EXPEDIENTE` | `FolgaAdminIT` |
| A58 | reserva `CONFIRMADO` às 15:00–15:30 · outra `AGUARDANDO_PAGAMENTO` ainda válida às 15:30–16:00 | folga 14:00–16:00 · folga 14:00–15:00 (só encosta na reserva das 15:00) | `422 FOLGA_COM_RESERVAS`, nada gravado · `201` (meio aberto, R2) | `FolgaAdminIT` |
| A59 | reservas `CANCELADO`, `EXPIRADO`, `CONCLUIDO` e `AGUARDANDO_PAGAMENTO` vencida dentro de 14:00–16:00 | criar a folga | `201` (só reserva ativa impede, E20) | `FolgaAdminIT` |
| A60 | ADMIN | folga com `fim` igual ao `inicio` · `fim` antes do `inicio` · toda no passado · motivo de 201 caracteres · campo extra | `400` em todos | `FolgaAdminIT` |
| A61 | folga criada | `GET /admin/barbeiros/{id}/folgas` · `DELETE` da folga · `DELETE` de novo · `DELETE` com `folgaId` de outro barbeiro | lista a folga (só de hoje em diante) · `204`, e a disponibilidade volta a **24** · `404` · `404` | `FolgaAdminIT` |
| A62 | horário livre às 15:00 | uma folga que cobre 15:00 e uma reserva às 15:00, disparadas ao mesmo tempo (50 repetições) | em toda repetição: **ou** (folga `201` e reserva `422 FORA_DO_EXPEDIENTE`) **ou** (reserva `201` e folga `422 FOLGA_COM_RESERVAS`); nunca os dois `201`, nunca `500` | `ConcorrenciaFolgaIT` |
| A63 | A9 da Fatia 4 (10 reservas disputando o mesmo horário) depois do `FOR SHARE` em `ReservarHorario` | rodar de novo | continua: exatamente um `201`, os demais `409` | `ReservaConcorrenteIT` |

### 5B · configuração

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A64 | configuração inicial (V1) | `GET /admin/configuracao` | `200`: antecedência 120, intervalo 0, granularidade 30, horizonte 30, expiração 15, `fuso = America/Sao_Paulo`, `version` 0 | `ConfiguracaoAdminIT` |
| A65 | relógio 17/11 08:00: reservar 17/11 09:30 (90 min à frente) dá `422 ANTECEDENCIA_INSUFICIENTE` | ADMIN: `PUT` com `antecedenciaMinimaMin: 60` e `version: 0`; reservar de novo | `200` com `version: 1`; a reserva das 09:30 passa a dar `201`, sem reiniciar a API | `ConfiguracaoAdminIT` |
| A66 | `expiracaoPagamentoMin: 30` | reservar | `expiraEm` = agora + 30 min; uma reserva feita **antes** da mudança mantém o `expiraEm` antigo | `ConfiguracaoAdminIT` |
| A67 | ADMIN | `PUT` com antecedência -1 · intervalo 121 · granularidade 25 · horizonte 0 e 366 · expiração 4 e 61 · `fuso` no corpo | `400 VALIDACAO_INVALIDA` com `errors[].field` do campo errado · `400 CAMPO_DESCONHECIDO`; nada gravado | `ConfiguracaoAdminIT` |
| A68 | `version` 1 | `PUT` com `version: 0` | `409 CONFLITO_DE_VERSAO`; configuração intacta | `ConfiguracaoAdminIT` |

### 5B · transversais

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A69 | ADMIN faz uma ação de cada tipo da 5B (criar e alterar serviço, criar e alterar barbeiro, trocar jornada, criar e remover folga, alterar configuração) | olhar `auditoria_admin` | **8** linhas, uma por ação (`SERVICO_CRIADO` ... `CONFIGURACAO_ALTERADA`), com ator, recurso e id; `detalhe` só com nomes de campos e ids, sem nome do barbeiro | `AuditoriaCadastrosIT` |
| A70 | sem token · `CLIENTE` · só `BARBEIRO` | todas as 12 rotas de cadastro da 5B | `401` · `403` · `403 ACESSO_NEGADO` | `AcessoAdminIT` |
| A71 | módulos | `./mvnw verify` | `admin` chama o catálogo, a jornada, a folga e a configuração só pela API pública da agenda (`PainelDaAgenda`); o vínculo da conta é checado só por `conta.Contas` | `ModulithTest`, `ArquiteturaTest` |

## 5. Entregas

| Módulo | 5A | 5B |
|---|---|---|
| `admin` | `AgendaAdminController`, `PagamentosAdminController`, `AuditoriaController`, casos de uso `ConsultarAgendaDoPainel`, `MudarStatusDoAgendamento`, `ListarPendencias`, `RegistrarDevolucao`, `RevisarEvento`, `Auditor` (gravador na transação) e `AuditoriaAdmin` (consulta paginada) | `ServicosAdminController`, `BarbeirosAdminController`, `ConfiguracaoAdminController` e os casos de uso finos que chamam a agenda e registram a auditoria |
| `agenda` | API pública `PainelDaAgenda` (agenda por período e barbeiro, mudar status com `version`), `Reservas.resumos(ids)`, evento **`ReservaCancelada`**, campos `cancelado_*` e `finalizado_*` na entidade `Reserva` | catálogo (`ServicoAdmin`, `BarbeiroAdmin`), jornada, folga (`FOR UPDATE` no barbeiro) e `ConfiguracaoAgenda` com `@Version`; `FOR SHARE` em `ReservarHorario` |
| `pagamento` | API pública `PendenciasDePagamento` (listar, devolver, eventos, status por reserva), listener `ReservaCancelada` | — |
| `conta` | API pública `Contas` (existe conta com o papel X?) | — |
| `plataforma` | matchers por papel no `SegurancaConfig` (rotas só ADMIN, senha inicial ainda barrada) | — |
| banco | — | `V5__painel.sql` (`configuracao_agenda.version`) |
| docs | contrato 1.4.0 (itens 1 a 4, 6 e 7 da seção 2), guia de testes com a rotina do Eduardo em saída real, README | contrato 1.4.0 (itens 5), guia de testes: cadastros e folga, decisões novas em `decisoes.md` (D70 em diante) |
| contrato | `lint` sem erro | `lint` sem erro |

## 6. Riscos e cuidados

- **Dado pessoal na agenda:** nome e WhatsApp saem só para o painel, nunca em log (A39) nem na auditoria (A35). E-mail e CPF não saem em lugar nenhum desta fatia.
- **`FOR SHARE` em `ReservarHorario`:** mexe no caminho mais sensível do produto. Mitigação: o A63 repete o teste de 10 reservas simultâneas e o A62 prova a corrida com a folga; se o ganho não compensar, a alternativa é aceitar a janela de milissegundos e só avisar (a folga não apaga reservas já feitas).
- **Cobrança pendente no gateway real:** cancelar uma reserva só cancela a cobrança **local**. No modo simulado não há o que cancelar; com o Asaas real (saída do beta) a porta `GatewayDePagamento` precisa ganhar "cancelar cobrança", senão o cliente poderia pagar um Pix que o gateway ainda aceita (cairia em `PAGA_APOS_EXPIRACAO`, A24, e viraria devolução). Anotado para o roteiro de saída do beta.
- **Cliente não é avisado do cancelamento** (fora do beta): ele só vê `CANCELADO` e o motivo no app (A23); o Eduardo avisa por fora. Sem WhatsApp automático, há risco de o cliente aparecer à toa.
- **Agenda sem paginação:** o contrato devolve array. Com um barbeiro, o máximo de uma semana cabe em poucas centenas de linhas; com muitos barbeiros o ADMIN usa `barbeiroId`.
- **`version` de reserva sobe por vários motivos** (aceite, expiração, webhook): a tela do Eduardo pode tomar `409` mesmo sem outra pessoa mexendo. É o comportamento esperado (recarregar resolve).
- **Auditoria e retenção:** o IP fica na tabela por 1 ano (proposta do dossiê, H13 pendente de fonte legal). A limpeza automática fica para a Fatia 6.
- **Evento revisado não reprocessa:** se um pagamento ficou preso de verdade, o Eduardo confere no gateway e acerta à mão. Com o modo simulado do beta isso é raro.

## 7. Dúvidas respondidas (17/11/2026: o Kevin aprovou todas as propostas)

| # | Dúvida | Decisão (proposta aprovada) |
|---|---|---|
| D-1 | Dividir em duas PRs (5A operação do dia, 5B cadastros)? São 71 critérios, mais que a Fatia 4 (45) | **sim**, como a Fatia 4; a 5B só começa com a 5A na `main` |
| D-2 | O BARBEIRO pode **cancelar** reserva (R22) ou só o ADMIN? | só o **ADMIN** (cancelar gera devolução de dinheiro); BARBEIRO só conclui e marca falta. Sem efeito hoje: o Eduardo tem os dois papéis |
| D-3 | O BARBEIRO pode criar e remover folga **da própria agenda**? O plano (S09) diz 403 para BARBEIRO em tudo do catálogo | só o **ADMIN** na primeira versão, como o plano; liberar a própria folga depois, se houver segundo barbeiro |
| D-4 | O que o barbeiro vê do cliente na agenda | **nome e WhatsApp** (para avisar de atraso ou desistência); e-mail e CPF não |
| D-5 | "Marcar evento como revisado" sem reprocessar basta para o beta? | **sim**; reprocessar à mão entra na saída do beta, com o gateway real |
| D-6 | Cancelar reserva com cobrança `PENDENTE` no gateway real fica para a saída do beta (porta nova "cancelar cobrança")? | **sim**, no beta o gateway é simulado (D59) |

## 8. Pronto quando

- [ ] A1 a A40 verdes na PR 5A e A41 a A71 na PR 5B (`scripts/conferir-spec.sh 5 --exigir`)
- [ ] `./mvnw verify` verde, com A9 da Fatia 4 (`ReservaConcorrenteIT`) ainda passando depois do `FOR SHARE`
- [ ] `redocly lint` com 0 erros no contrato 1.4.0
- [ ] rotina do Eduardo rodada à mão no perfil `dev`: ver o dia, fechar, cancelar uma reserva paga, devolver, criar folga, mudar a antecedência (saídas em `docs/guia-testes.md`)
- [ ] decisões novas registradas em `decisoes.md` (D70 em diante: matriz de papéis, auditoria na mesma transação, `ReservaCancelada`, `FOR SHARE`)
- [ ] [checklist da PR](checklist-pr.md) completo
