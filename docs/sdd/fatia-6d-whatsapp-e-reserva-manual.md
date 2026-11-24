# 📐 Spec · Fatia 6D · Prazo de 3 minutos, WhatsApp do barbeiro e reserva manual

| | |
|---|---|
| **Status** | ✅ aprovada pelo Kevin (18/11/2026): as três mudanças foram pedidas e aprovadas por ele no mesmo dia |
| **Origem** | pedido do Kevin em 18/11/2026, depois da 6C: (1) o prazo de pagamento cai de 15 para **3 minutos**; (2) o **WhatsApp do barbeiro** vira contato direto no site; (3) **reserva manual** pelo painel, para travar um horário combinado por WhatsApp, fora do app |
| **Regras** | [R7](../contexto.md#3-regras-de-negócio) (prazo, agora 3 min), R14 e R15 (pagamento tardio e vencida não ocupa), R1 a R5 (grade, almoço, folga, constraint), R19 a R23 (fechar, cancelar, escopo), R29 e R30 (retenção e log), **R33 e R34 (novas)** |
| **ADRs** | [0003](../adr/0003-anti-double-booking-no-banco.md) (a constraint decide a disputa), [0009](../adr/0009-lgpd-marketing-cpf-e-retencao.md) (LGPD), [0002](../adr/0002-comunicacao-entre-modulos.md) (módulos) |
| **Custo** | **zero**: nenhum serviço novo |
| **Não cobre** | telas (Fatia 7: prazo na contagem, botão `wa.me`, formulário da reserva manual, origem na agenda) · aviso automático por WhatsApp (pós-beta) · registro financeiro do que for pago por fora (decisão: não existe) · CEP na reserva manual (D127) · cancelar a reserva manual como BARBEIRO (D124) |

## 1. Objetivo

Três ajustes que o dia a dia da barbearia pediu. **(1)** Quem abre o Pix e não paga em 3 minutos devolve o horário para a fila (hoje são 15). **(2)** O cliente que prefere conversar chama o barbeiro pelo WhatsApp com um botão no site. **(3)** O horário que o Eduardo combinou por WhatsApp entra na agenda pelo painel e o app deixa de oferecê-lo a outra pessoa, sem cobrança e sem pagamento no sistema.

## 2. Contrato

Versão **1.6.0** do [`openapi.yaml`](../contrato/openapi.yaml) e do [`contrato-api.md`](../contrato/contrato-api.md).

| Método | Rota | Quem | Sucesso | Erros |
|---|---|---|---|---|
| `POST` | `/admin/agendamentos` (**nova**) | `ADMIN` (qualquer barbeiro) · `BARBEIRO` (só para si) | `201` `AgendamentoAdmin` (`status = CONFIRMADO`, `origem = MANUAL`), `Location: /admin/agendamentos/{id}` | `400 VALIDACAO_INVALIDA` · `400 CAMPO_DESCONHECIDO` · `401` · `403` (cliente; admin com senha inicial: `TROCA_DE_SENHA_OBRIGATORIA`) · `404` (barbeiro alheio, sem vínculo ou inexistente) · `409 HORARIO_INDISPONIVEL` · `409 CONFLITO_CONCORRENTE` · `422 FORA_DO_EXPEDIENTE`, `SERVICO_INATIVO`, `ANTECEDENCIA_INSUFICIENTE` (horário que já passou), `FORA_DO_HORIZONTE` |
| `GET` | `/barbeiros` (muda) | público | cada item ganha `whatsapp` (`string \| null`, só dígitos) | |
| `POST` / `PUT` | `/admin/barbeiros` e `/admin/barbeiros/{id}` (mudam) | só `ADMIN` | corpo aceita `whatsapp` opcional (10 a 13 dígitos, com ou sem máscara); resposta devolve só dígitos. No `PUT`, sem o campo = sem número | `400 VALIDACAO_INVALIDA` no campo `whatsapp` |
| `GET` | `/admin/agenda` · `PATCH /admin/agendamentos/{id}/status` (mudam) | como antes | `AgendamentoAdmin` ganha `observacao` (nulo) e `origem` aceita `MANUAL` (`cliente.nome` = `Sem nome` quando não há nome) | |

Corpo do `POST /admin/agendamentos`: `{ servicoId, barbeiroId, inicio, nomeCliente? (até 80), observacao? (até 200) }`. **Não existe campo de WhatsApp, e-mail nem CPF do cliente**; mandar qualquer campo fora do contrato é `400 CAMPO_DESCONHECIDO`. As descrições de `15 min` no contrato passam a `3 min`. `expiracaoPagamentoMin` aceita de **3** a 60 (era 5 a 60). `GET /politica-privacidade` passa a devolver a versão **1.1**.

### Banco · `V7__whatsapp_e_reserva_manual.sql`

1. `configuracao_agenda`: o CHECK passa a `expiracao_pagamento_min BETWEEN 3 AND 60`; a linha única vai de 15 para 3 **só se ainda estiver em 15** (se o admin já escolheu outro prazo, vale o dele), com `version + 1`.
2. `barbeiro.whatsapp varchar(13)` opcional, `CHECK (whatsapp ~ '^[0-9]{10,13}$')` (o mesmo do `usuario`).
3. `reserva.nome_cliente varchar(80)`, `observacao varchar(200)`, `criado_por uuid`; `origem` aceita `MANUAL` (sem `cliente_id`); `reserva_aceite_ck` dispensa o aceite para `MANUAL`; `reserva_manual_ck` (MANUAL nunca aguarda pagamento, nunca expira e não tem aceite); `reserva_contato_manual_ck` (nome e observação só existem na origem MANUAL); índice parcial para a retenção.

## 3. Decisões de desenho

| # | Tema | Decisão | Por quê |
|---|---|---|---|
| E1 | Prazo (D121) | O padrão vira 3 min. Continua configurável (3 a 60 no painel). A reserva já criada mantém o `expira_em` dela; o prazo vale para as novas | pedido do Kevin; mudar o passado confundiria quem já está pagando |
| E2 | Pagamento tardio | Sem mudança de regra: depois dos 3 min, horário livre volta a `CONFIRMADO` (R14); horário já tomado fica `PAGA_APOS_EXPIRACAO` e vira devolução. Com 3 min isso acontece mais, e o painel de devoluções já cobre | o risco é operacional, não de código |
| E3 | WhatsApp do barbeiro (D122) | Coluna em `barbeiro`, **contato comercial**, não dado de cliente. Só o ADMIN edita (rota já exclusiva). `GET /barbeiros` devolve o número; o front monta `https://wa.me/<numero>`. No dev o seed fica sem número | quem divulga o número é o próprio barbeiro; não há consentimento de cliente envolvido |
| E4 | Modelo da reserva manual (D123) | Linha em `reserva` com `origem = MANUAL`, `CONFIRMADO`, sem `cliente_id`, sem `reserva_guest`, **sem cobrança** e sem aceite. O preço do serviço é copiado só como referência (a V1 exige `preco_centavos > 0`) | reaproveita a constraint EXCLUDE, a agenda e o fechamento sem tabela nova |
| E5 | Regras da reserva manual (D124) | Mesma grade da reserva do cliente (jornada, almoço, folga, granularidade, serviço ativo, horizonte). **A antecedência mínima não vale** (quem trava é a barbearia), mas horário que já passou é `422 ANTECEDENCIA_INSUFICIENTE`. A disputa é da constraint (`409`). A reserva e a auditoria ficam na **mesma transação** (a auditoria roda dentro da transação da agenda; falhou, desfaz) | o Eduardo precisa travar um horário daqui a 30 minutos; a corrida continua resolvida pelo banco |
| E6 | Escopo | ADMIN: qualquer barbeiro. BARBEIRO: só o próprio; alheio, inexistente ou sem vínculo é `404` (R23), igual ao resto do painel | regra do painel |
| E7 | Fechar e cancelar | Como as demais: concluir e marcar falta a partir do início (R21); **só o ADMIN cancela**, com motivo (R22). Cancelar sem cobrança não cria devolução: o listener do pagamento só troca cobranças que existem | o pedido foi "como as demais"; o BARBEIRO cancelar a própria fica como melhoria |
| E8 | LGPD (D125) | `nomeCliente` e `observacao` são dado pessoal opcional. **Não se guarda WhatsApp, e-mail nem CPF do cliente.** Nada em log (`toString` sem dados, auditoria só com nomes técnicos). O cliente logado e o visitante nunca enxergam a reserva manual (404) | R29 e R30 |
| E9 | Retenção (D126) | `RetencaoDeReservasManuais`: nome e observação viram `NULL` 90 dias depois do **fim** da reserva (o mesmo `app.privacidade.retencao-guest-dias` do visitante), em lotes, idempotente, sem mexer em `version` | mesma regra do visitante |
| E10 | Alcance e financeiro (D127) | A reserva manual **não conta no alcance** (sem região; contá-la só inflaria "sem região") e **não aceita CEP**: reaproveitar o fluxo da 6C exigiria guardar CEP de quem não passou pelo app. Pagamento por fora (dinheiro, Pix direto) **não gera registro financeiro** | simples, sem dado novo |
| E11 | Política (D128) | A política ganha texto (WhatsApp comercial, reserva por fora, retenção do nome, backup semanal de até 30 dias) e **sobe para a versão 1.1**; o texto do consentimento de marketing não muda (cópia na 1.1). Os arquivos da 1.0 ficam | a política 8 diz que mudança de texto sobe a versão; passa a haver um dado novo (nome digitado pela barbearia) |

## 4. Critérios de aceite

A numeração é própria desta fatia (A1 a A28). Os testes antigos que dependiam de 15 minutos mudaram de valor (calculado pela regra), sem perder asserção: ver D129.

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | banco migrado | ler `configuracao_agenda` e tentar 2 e 61 | prazo = 3; o CHECK aceita de 3 a 60 | `V7MigracaoIT` |
| A2 | barbeiro sem número | gravar números diferentes | opcional; só 10 a 13 dígitos (letras, máscara e 9 dígitos recusados) | `V7MigracaoIT` |
| A3 | reserva MANUAL | inserir variações | aceita CONFIRMADO sem cliente e sem aceite; recusa aguardando pagamento, com aceite, com cliente e `nome_cliente` fora da origem MANUAL | `V7MigracaoIT` |
| A4 | relógio às 08:00:00 | reserva do visitante | `expira_em` e `expiraEm` às 08:03:00 | `PrazoDeTresMinutosIT` |
| A5 | reserva feita às 08:00:00 | às 08:02:59 e às 08:03:00 | 08:02:59: horário ocupado e `409`; 08:03:00: horário volta e `201` | `PrazoDeTresMinutosIT` |
| A6 | Pix aprovado 4 min depois, horário já de outro | webhook | reserva `EXPIRADO`, cobrança `PAGA_APOS_EXPIRACAO`, pendência no `GET /admin/pagamentos` | `PrazoDeTresMinutosIT` |
| A7 | ADMIN | `POST` e `PUT /admin/barbeiros` com WhatsApp mascarado | banco com só dígitos; resposta e lista mostram; `PUT` sem o campo remove; auditoria sem o número | `WhatsappDoBarbeiroIT` |
| A8 | barbeiros ativos, um sem número, um inativo | `GET /barbeiros` sem token | número dos ativos (nulo se faltar), inativo fora, cache de 60 s | `WhatsappDoBarbeiroIT` |
| A9 | números com 9 ou 14 dígitos ou sem dígitos | `POST` e `PUT` | `400 VALIDACAO_INVALIDA` no campo `whatsapp`, nada gravado | `WhatsappDoBarbeiroIT` |
| A10 | BARBEIRO, CLIENTE, sem token | `POST` e `PUT` de barbeiro | `403`, `403`, `401`; número intacto | `WhatsappDoBarbeiroIT` |
| A11 | política | `GET /politica-privacidade` | versão `1.1`; texto do WhatsApp comercial, da reserva por fora e do backup de 30 dias | `WhatsappDoBarbeiroIT` |
| A12 | perfil dev | seed e `GET /barbeiros` | o seed dev não grava WhatsApp; desde a D152 o Eduardo tem o número comercial público da V8 | `SeedDevWhatsappIT` |
| A13 | BARBEIRO, 15:00 livre | `POST /admin/agendamentos` | `201` CONFIRMADO, MANUAL, sem prazo, sem cobrança, sem `reserva_guest`, sem aceite, `criado_por`, auditoria sem nome | `ReservaManualIT` |
| A14 | ADMIN | para o barbeiro B | `201` | `ReservaManualIT` |
| A15 | BARBEIRO para outro barbeiro · conta sem vínculo · ADMIN com barbeiro inexistente | `POST` | `404`, nada gravado nem auditado | `ReservaManualIT` |
| A16 | horário de CONFIRMADO ou de pendente válida · pendente vencida | `POST` | `409 HORARIO_INDISPONIVEL`, nada gravado · `201` (a vencida expira) | `ReservaManualIT` |
| A17 | almoço, antes da jornada, fora da granularidade, segunda, folga, serviço inativo, horário passado, além do horizonte | `POST` | `422` com `FORA_DO_EXPEDIENTE`, `SERVICO_INATIVO`, `ANTECEDENCIA_INSUFICIENTE`, `FORA_DO_HORIZONTE` | `ReservaManualIT` |
| A18 | 10:30 com o relógio às 09:30 e antecedência de 2 h | cliente e `POST` manual | cliente `422 ANTECEDENCIA_INSUFICIENTE`; manual `201` | `ReservaManualIT` |
| A19 | duas reservas manuais | agenda do dia e disponibilidade | origem MANUAL, nome, observação, sem WhatsApp (`Sem nome` se faltar); horários somem da disponibilidade | `ReservaManualIT` |
| A20 | 4 pedidos simultâneos no mesmo horário | `POST` | um `201`, três `409`; uma reserva gravada | `ReservaManualIT` |
| A21 | reserva manual | concluir e marcar falta pelo BARBEIRO | antes do início `422`; depois `200`; nenhuma cobrança nem pendência | `ReservaManualIT` |
| A22 | reserva manual | cancelar | BARBEIRO `403`; sem motivo `422`; ADMIN `200` CANCELADO sem cobrança nem devolução; horário volta | `ReservaManualIT` |
| A23 | corpos inválidos e acessos | `POST` | `400` (81 e 201 caracteres, campos fora do contrato, `servicoId` faltando), `401`, `403`; nome em branco vira nulo | `ReservaManualIT` |
| A24 | cliente logado e visitante | ler e pagar a reserva manual | `404` (R23); fora de "Meus agendamentos" | `ReservaManualIT` |
| A25 | manual com fim há 90 dias exatos | job de retenção | nome e observação `NULL`; reserva, status, preço e `version` iguais; segunda rodada não muda nada | `ReservaManualRetencaoIT` |
| A26 | manual com fim um minuto antes dos 90 dias | job | nome e observação intactos | `ReservaManualRetencaoIT` |
| A27 | 5 atendimentos em Osasco e 3 manuais | `GET /admin/alcance` | total 5, `semRegiao` 0 | `ReservaManualAlcanceIT` · substituído pela Fatia 11 (A27), D162 a D170 |
| A28 | roteiro com sucesso e erros, e cadastro do WhatsApp do barbeiro | ler o log | nenhum nome, observação, WhatsApp nem token | `SemDadoPessoalNoLogReservaManualIT` |

## 5. Entregas

| Módulo | O quê |
|---|---|
| `banco` | `V7__whatsapp_e_reserva_manual.sql` |
| `agenda` | `Barbeiro.whatsapp`, `BarbeiroNoCatalogo` (`GET /barbeiros`), `Reserva.Origem.MANUAL` e `Reserva.manual`, `ReservarHorario.comoBarbearia`, `PainelDaAgenda.criarReservaManual` (+ `observacao` no `Item`), `RetencaoDeReservasManuais`, filtro `origem <> 'MANUAL'` em `ConsultaAlcance` |
| `admin` | `POST /admin/agendamentos` (`ReservaManualRequest`, `CriarReservaManual`), `whatsapp` nos DTOs de barbeiro, mínimo de 3 min em `ConfiguracaoRequest` |
| `privacidade` | `politica-1.1.md`, `consentimento-1.1.md`, `VERSAO_DO_TEXTO = 1.1` |
| docs | contrato 1.6.0, contexto (R7, R33, R34), README, fluxo ponta a ponta, guia de testes (V10 em 3 min e fluxo manual da 6D), decisões D121 a D129, spec da Fatia 9 (migrações V8 e V9) |

## 6. Riscos e cuidados

- **3 minutos é pouco para quem paga com cartão e erra o CVV.** Mais pagamentos vão cair depois do prazo e virar devolução manual. Mitigação: o prazo é configurável (até 60) e a devolução já tem tela.
- **Reserva manual sem registro financeiro:** o que for pago em dinheiro ou Pix direto não aparece em lugar nenhum do sistema. É a decisão do Kevin; se um dia precisar de relatório, vira Fatia nova.
- **Observação livre pode receber dado pessoal demais** (telefone, endereço). O limite de 200 caracteres, o apagamento em 90 dias e o aviso na tela mitigam; o front deve dizer "não digite telefone nem documento".
- **BARBEIRO não cancela a reserva manual que criou por engano** (só o ADMIN cancela, R22). Ajuste simples se o Kevin pedir.
- **O WhatsApp do barbeiro é público:** fica visível a qualquer visitante e robôs. Por isso é contato comercial, não pessoal.

## 7. Dúvidas (todas respondidas pelo Kevin em 18/11/2026)

| # | Dúvida | Resposta |
|---|---|---|
| D-1 | Prazo de 15 para 3 minutos | ✅ 3 minutos |
| D-2 | WhatsApp do barbeiro: coluna própria, ADMIN edita, público lê | ✅ |
| D-3 | Reserva manual pelo painel, sem cobrança, ADMIN e BARBEIRO | ✅ |
| D-4 | CEP na reserva manual | decidido na implementação: **não** (D127) |
| D-5 | Política: versão nova ou não | decidido na implementação: **1.1** (D128) |
