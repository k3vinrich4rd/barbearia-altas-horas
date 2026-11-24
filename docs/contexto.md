# 🧭 Dossiê de contexto · Barbearia Altas Horas

| | |
|---|---|
| 🎯 **Para que serve** | o "por quê", "para quem" e "até onde" do projeto: a fonte do contrato, dos ADRs e do README |
| 👥 **Área responsável** | Kevin (regras de negócio e texto) · revisão de arquitetura |
| ⏰ **Quando usar** | antes de mexer no projeto; atualize no mesmo PR que mudar regra, dado pessoal, dependência externa ou requisito não funcional |
| 🧰 **Como** | tabelas e regras numeradas (R#), citadas nos testes (`@DisplayName("R12 ...")`) e nos PRs |
| 🚫 **Não cobre** | rotas e payloads ([contrato](contrato/contrato-api.md)) · decisões de arquitetura ([ADRs](adr/)) |

> [!IMPORTANT]
> **Fase de desenho (13/11/2026).** Nada aqui foi medido ainda. As regras reais da barbearia (horários, barbeiros, serviços, preços, intervalo, antecedência, janela de cancelamento e política do sinal) estão sendo perguntadas ao Kevin. Todo valor marcado **HIPÓTESE** é provisório e entra no sistema como **dado configurável** (tabela `configuracao_agenda` e cadastros do painel), nunca fixo no código. Nomes, preços e pessoas dos exemplos são **fictícios**.

## Sumário
1. [Visão geral](#1-visão-geral)
2. [Glossário](#2-glossário)
3. [Regras de negócio](#3-regras-de-negócio)
4. [Hipóteses em aberto](#4-hipóteses-em-aberto)
5. [Requisitos não funcionais](#5-requisitos-não-funcionais)
6. [Personas e jornadas](#6-personas-e-jornadas)
7. [Riscos e ameaças](#7-riscos-e-ameaças)
8. [LGPD: inventário de dados pessoais](#8-lgpd-inventário-de-dados-pessoais)
9. [Dependências externas](#9-dependências-externas)
10. [Custos](#10-custos)
11. [Métricas de sucesso](#11-métricas-de-sucesso)
12. [Roadmap e débitos](#12-roadmap-e-débitos)

---

## 1. Visão geral

| | |
|---|---|
| **Problema** | A agenda da barbearia vive no WhatsApp e no caderno. Horário marcado e não cumprido é cadeira vazia, e dois clientes no mesmo horário é constrangimento. |
| **Quem sofre** | o barbeiro (agenda bagunçada, faltas), o cliente (não sabe o que está livre) e o dono (não enxerga a semana). |
| **Como resolve** | agenda online com disponibilidade real, reserva impossível de duplicar (garantida pelo banco), sinal de 50% para quem marca sem conta e um painel para a barbearia operar sem mexer no banco. |
| **Propósito duplo** | produto real da Barbearia Altas Horas (desde 1997) e **template** de API Java de nível pleno para os próximos projetos. |

**Fora do escopo do beta:** login social ligado (o código entra atrás da flag `app.features.social-login=false`), painel de alcance geográfico, lembrete por WhatsApp, reembolso automático do sinal, múltiplas unidades, comissão de barbeiro, estoque, nota fiscal.

## 2. Glossário

| Termo | Significa | No código / na API |
|---|---|---|
| Reserva | um horário de um barbeiro para um serviço, de um cliente ou de um guest. Na tela e nas rotas aparece como **agendamento** | entidade `Reserva`, tabela `reserva`, rota `/agendamentos` |
| Cliente | pessoa com conta (papel `CLIENTE`) | `Usuario` com papel `CLIENTE` |
| Guest | pessoa que agenda **sem conta**; paga o sinal | `ReservaGuest` (dados de contato ligados à reserva) |
| Barbeiro | quem atende; pode ter login (papel `BARBEIRO`) | `Barbeiro` (agenda) ligado opcionalmente a um `Usuario` (conta) |
| Admin | quem opera o painel (papel `ADMIN`) | `Usuario` com papel `ADMIN` |
| Serviço | o que é feito (ex.: corte), com duração e preço | `Servico` |
| Jornada | horário semanal de trabalho de um barbeiro | `Jornada` (dia da semana + hora de início e fim) |
| Folga | período em que o barbeiro não atende (dia ou intervalo) | `Folga` |
| Disponibilidade | horários livres calculados de jornada − folgas − reservas que ocupam horário | `GET /disponibilidade` |
| Sinal | 50% do preço do serviço, pago pelo guest para segurar o horário, **congelado** no momento da reserva | `reserva.sinal_centavos` |
| Cobrança | pedido de pagamento do sinal no gateway (Pix) | `Cobranca`, tabela `cobranca` |
| Evento de webhook | aviso do gateway de que algo mudou numa cobrança | `evento_webhook`, único por id do evento do provedor |
| Expiração | fim do prazo para o guest pagar | `reserva.expira_em` |
| Status que ocupam horário | `AGUARDANDO_PAGAMENTO` e `CONFIRMADO` | filtro da constraint `EXCLUDE` |
| Configuração da agenda | valores que a barbearia muda sem deploy (antecedência, janela, intervalo, sinal...) | tabela `configuracao_agenda` |

**Sinônimos proibidos no código:** "booking", "appointment", "slot" para reserva (use `Reserva`); "deposit" para sinal.

## 3. Regras de negócio

Cada regra vira pelo menos um teste que cita o número. "Origem" diz de onde veio: **P** = prompt aprovado pelo Kevin em 13/11/2026; **A** = proposta de arquitetura; **H** = hipótese (seção 4).

| # | Regra | Origem | Teste previsto (fatia) |
|---|---|---|---|
| R1 | Um barbeiro não pode ter duas reservas que se sobrepõem entre as que ocupam horário (`AGUARDANDO_PAGAMENTO`, `CONFIRMADO`, inclusive a reserva manual). Garantido pelo banco (`EXCLUDE USING gist`), não só pelo código. | P, A | `ReservaConcorrenciaIT` (S01, S07) |
| R2 | O intervalo de uma reserva é meio aberto `[inicio, fim_ocupacao)`: uma reserva que termina às 10h00 não conflita com outra que começa às 10h00. | A | `ReservaConstraintIT` (S01) |
| R3 | `fim = inicio + duração do serviço`; `fim_ocupacao = fim + intervalo entre atendimentos` (**0 min**, H4 decidida). Serviço de 60 min ocupa 2 slots seguidos. | P, H4 | `CalculoHorarioTest` (S03) |
| R4 | Só é possível reservar dentro da jornada (terça a sábado, 07h00 às 20h00, pausa de almoço 12h00 às 13h00 como intervalo editável da jornada), fora das folgas, começando em múltiplos de **30 min** (H1, H11). | P, H1, H11 | `DisponibilidadeTest` (S03) |
| R5 | **Reescrita em 24/11/2026 (D197, Fatia 14A):** não há antecedência mínima nem limite de dias à frente. A **única** regra de tempo é o início da reserva ser **depois de agora**, no fuso `America/Sao_Paulo`: dá para marcar para daqui a 5 minutos, para hoje mais tarde ou para qualquer data futura. (Antes: 2 horas de antecedência, H5, e 30 dias à frente, H12.) "Pagou, a agenda é sua e não pode cancelar, então o barbeiro não perde nunca." | ~~H5~~, ~~H12~~ (revogadas) | `AgendaSemLimitesIT` (14A) |
| R6 | **Todo** agendamento, de cliente logado ou de guest, nasce `AGUARDANDO_PAGAMENTO` e só vira `CONFIRMADO` com 100% do valor pago (decisão de 16/11/2026; substitui "cliente logado reserva direto em `CONFIRMADO`"). Única exceção: a reserva manual da barbearia (R33). | P | `AgendarClienteIT` (S07) |
| R7 | A reserva `AGUARDANDO_PAGAMENTO` tem `expira_em = agora + 3 min` (configurável de 3 a 60; era 15 até 18/11/2026, D121) e ocupa o horário enquanto não expira. Sem pagamento no prazo, vira `EXPIRADO` e o horário volta. | P | `AgendarGuestIT` (S11) |
| R8 | A cobrança é de **100%** do preço do serviço **no momento da reserva**, em centavos, por **Pix** ou **cartão de crédito** (checkout hospedado do gateway; o número do cartão nunca passa pela API). Mudar o preço depois não muda a cobrança. | P | `CobrancaValorTest` (S12) |
| R9 | O CPF é pedido **só** na criação da cobrança, porque o gateway exige. Não é pedido no cadastro nem guardado. | P | `CobrancaIT` (S12) |
| R10 | A criação de cobrança exige `X-Idempotency-Key`. Mesma chave e mesmo corpo devolvem a mesma cobrança; mesma chave e corpo diferente dão `422`. | P | `CobrancaIdempotenciaIT` (S12) |
| R11 | O webhook só é aceito com autenticação válida do provedor (ADR 0007); senão `401` e nada muda. | P | `WebhookAssinaturaIT` (S13) |
| R12 | O id do **evento** do provedor é único (`evento_webhook`). Evento repetido responde `200` sem efeito. | P | `WebhookIdempotenciaIT` (S13) |
| R13 | Pagamento confirmado de reserva ainda válida → reserva vai para `CONFIRMADO`. | P, A | `ConfirmacaoPagamentoIT` (S13) |
| R14 | Pagamento confirmado **depois** de a reserva expirar: se o horário ainda estiver livre, a reserva volta de `EXPIRADO` para `CONFIRMADO` (passando pela constraint); se já estiver ocupado, a cobrança fica `PAGA_APOS_EXPIRACAO` e vira **pendência administrativa** no painel (H8 decidida). | P | `PagamentoTardioIT` (S13) |
| R15 | Reserva `AGUARDANDO_PAGAMENTO` com `expira_em` no passado não ocupa horário na disponibilidade, mesmo antes do job rodar. | P | `DisponibilidadeExpiracaoIT` (S03, S14) |
| R16 | Antes de inserir uma reserva, as reservas vencidas que conflitam com o horário são expiradas na mesma transação (expiração preguiçosa), para a constraint não recusar um horário que a tela mostrou livre. | A (ADR 0003) | `ExpiracaoPreguicosaIT` (S07) |
| R17 | Um job com ShedLock marca `EXPIRADO` as reservas vencidas a cada minuto (intervalo configurável) e publica `ReservaExpirada`. | P, A | `ExpiracaoJobIT` (S14) |
| R18 | O cliente **não** cancela nem remarca: não há botão nem rota pública para isso. Antes de pagar, ele aceita (checkbox obrigatório, `aceitePolitica: true` + versão do texto, gravados na reserva) que não há cancelamento, remarcação ou estorno por desistência ou falta. Sem aceite: `422 ACEITE_POLITICA_OBRIGATORIO`. | P | `AceitePoliticaIT` (S12) |
| R19 | Falta: o barbeiro marca `NAO_COMPARECEU` no painel e o valor pago fica retido pela barbearia (sem devolução). | P | `FechamentoAtendimentoIT` (S10) |
| R20 | Transições válidas são só as da [máquina de estados](dominio/modelo-de-dominio.md#2-máquina-de-estados); qualquer outra dá `422 TRANSICAO_INVALIDA`. | P | `ReservaStatusTest` (S10) |
| R21 | `CONCLUIDO` e `NAO_COMPARECEU` só a partir do início da reserva; quem marca é o ADMIN/BARBEIRO (hoje uma pessoa só, Carlos Eduardo Candido). | P | `FechamentoAtendimentoIT` (S10) |
| R22 | Cancelamento **pela barbearia** (único cancelamento possível) exige motivo; o motivo e quem cancelou ficam registrados e a cobrança vai para `DEVOLUCAO_PENDENTE`. | P | `CancelamentoAdminIT` (S10) |
| R23 | Quem não é dono de uma reserva recebe `404` ao acessá-la (não confirma que o id existe). | P | `ReservaBolaIT` (S08) |
| R24 | E-mail é único por conta, sem diferença de maiúsculas. | P | `CadastroIT` (S04) |
| R25 | O primeiro admin é criado na subida a partir de `ADMIN_EMAIL` e `ADMIN_SENHA` só se ainda não existir admin; a senha é gravada só como hash e nunca é sobrescrita por variável depois. | P | `AdminInicialIT` (S06) |
| R26 | Login (inclusive do admin), cadastro e reserva guest têm limite de tentativas (**H14**), e a consulta pública de CEP também (**H21**); passou do limite: `429` com `Retry-After`. | P, H14, H21 | `LimiteTentativasIT` (S06, S11), `CepLimiteIT` (S15) |
| R27 | Refresh token é de uso único e rotativo; reuso de um refresh já usado revoga toda a família da sessão. | P, A | `RefreshRotativoIT` (S05) |
| R28 | Consentimento de marketing é opcional, nasce desmarcado, guarda versão do texto e data, e pode ser revogado a qualquer momento. | P | `ConsentimentoIT` (S16) |
| R29 | Dados de contato do guest são anonimizados depois do prazo de retenção (**H13**). | P | `RetencaoGuestIT` (S16) |
| R30 | CPF, telefone e CEP nunca aparecem em log (desde a Fatia 11 o CEP nem é guardado). | P | `SemDadoPessoalNoLogIT` (S16 e em toda fatia) |
| R31 | A consulta de CEP nunca derruba o cadastro: com ViaCEP e BrasilAPI fora, o CEP é salvo e bairro e cidade ficam para completar. | P | `CepDegradacaoIT` (S15) |
| R32 | Rotas de login social respondem `404` enquanto a flag estiver desligada. | P | `SocialDesligadoIT` (pós-beta) |
| R34 | **Região por consentimento (Fatia 11, ADR 0014):** o bairro e a cidade só são usados no alcance se a pessoa marcar a caixinha (revogável, apaga a região); o alcance conta pessoas, com k = 5 e subida de nível. | P | `RegiaoConsentidaIT`, `AlcancePropriedadeTest` |
| R33 | **Reserva manual (Fatia 6D, 18/11/2026):** a barbearia pode travar pelo painel um horário combinado por WhatsApp, fora do app. Nasce `CONFIRMADO` com origem `MANUAL`, sem cobrança, sem aceite e sem pagamento no sistema (o que for pago por fora não gera registro financeiro). ADMIN para qualquer barbeiro, BARBEIRO só para si; respeita jornada, almoço, folga e a constraint (R1), mas não a antecedência mínima (R5). Só guarda nome e observação opcionais do cliente, nunca o WhatsApp, e os apaga 90 dias depois do fim (R29). Não conta no alcance. É exceção declarada de R6 | P | `ReservaManualIT` (6D) |
| R34 | **WhatsApp do barbeiro (Fatia 6D):** é contato comercial, cadastrado só pelo ADMIN, opcional e público em `GET /barbeiros`; o front monta `https://wa.me/<número>`. Não é dado de cliente | P | `WhatsappDoBarbeiroIT` (6D) |
| R35 | **Convite do barbeiro (Fatia 10B, 23/11/2026):** só o ADMIN (Dono/Admin) gera o convite de um barbeiro e o desliga. O convite é de uso único, vale 48 h e o código só existe na tela do dono (o banco guarda o SHA-256). O barbeiro digita o **próprio e-mail** e a senha; tudo que não é um aceite certo é o mesmo `422`. Conta de cliente que já existe só aceita logada nela e o convite nunca troca a senha. Desligar tira o acesso e mantém o histórico (não apaga, D153) | P | `ConviteDoBarbeiroIT`, `ConviteSegurancaIT` (10B) |
| R36 | **Quem mexe na agenda de quem (Fatia 10A, 23/11/2026):** o ADMIN vê, faz reserva manual, conclui, marca falta e cancela na agenda de qualquer barbeiro; o BARBEIRO só mexe na própria e o que é de outro responde `404` igual a "não existe" (R23). Só o dono cancela (D124) | P | `IsolamentoEntreBarbeirosIT` (10A) |

## 4. Hipóteses em aberto

> **16/11/2026:** o Kevin confirmou com a barbearia as regras reais. As linhas marcadas **decidida** deixam de ser hipótese; o valor continua configurável (dado, não código).

Perguntas enviadas ao Kevin em 13/11/2026. Até a resposta, vale o **valor provisório**, sempre como dado configurável ou seed fictício do perfil `dev`.

| # | Pergunta | Valor provisório | Onde fica configurado |
|---|---|---|---|
| H1 | Dias e horário de funcionamento | **decidida (16/11)**: terça a sábado, 07h00 às 20h00; almoço 12h00 às 13h00 | `jornada` do barbeiro (painel) |
| H2 | Quantos barbeiros atendem | **decidida (16/11)**: 1, Carlos Eduardo Candido (ADMIN e BARBEIRO); a entidade `Barbeiro` continua no modelo | seed + painel |
| H3 | Serviços, duração e preço | **decidida (16/11), duração revista em 18/11 (D94)**: todos os serviços duram **60 min** (2 slots) · Corte Clássico/Social R$ 45,00 · Corte Degradê/Fade/Navalhado R$ 55,00 · Corte Moderno/Visagismo (tesoura) R$ 60,00 · Barba Completa/Desenhada R$ 30,00 · Acabamento (pezinho)/Sobrancelha R$ 20,00 · sem combos | cadastro de serviços (painel) |
| H4 | Intervalo entre atendimentos | **decidida (16/11)**: 0 min | `configuracao_agenda.intervalo_entre_atendimentos_min` |
| H5 | Antecedência mínima para agendar | **revogada (24/11, D197)**: não existe; era 120 min | ~~`configuracao_agenda.antecedencia_minima_min`~~ (coluna apagada na V14) |
| H6 | Janela para cancelar ou remarcar | **decidida (16/11)**: não existe; o cliente não cancela nem remarca (R18) | — |
| H7 | Política de pagamento em falta ou cancelamento | **decidida (16/11)**: 100% antecipado; falta retém o valor; sem estorno por desistência | `politica_pagamento` (texto versionado) |
| H8 | Pagamento que chega depois de a reserva expirar | **decidida (16/11)**: confirma se o horário estiver livre; senão, pendência administrativa (R14) | regra R14 |
| H9 | O barbeiro vê só a própria agenda ou a de todos? | **sem efeito no beta** (1 barbeiro); a regra "só a própria" fica no código para o template | autorização do módulo `admin` |
| H10 | Pacote raiz Java | **decidida (16/11)**: `io.github.k3vinrich4rd.altashoras` | ADR 0001 |
| H11 | Granularidade dos horários oferecidos | **decidida (16/11)**: 30 min | `configuracao_agenda.granularidade_min` |
| H12 | Até quantos dias à frente dá para agendar | **revogada (24/11, D197)**: sem limite; eram 30 dias | ~~`configuracao_agenda.horizonte_dias`~~ (coluna apagada na V14) |
| H13 | Retenção dos dados do guest e do histórico do sinal | contato do guest: anonimizar 90 dias após o atendimento; registro financeiro da cobrança: 5 anos (**pendente de fonte legal**) | `app.privacidade.retencao-guest-dias` e ADR 0009 |
| H14 | Limites de tentativas | **decidida (17/11)**: login 5 erros em 15 min por e-mail + IP e 20 por IP; cadastro 5 por hora por IP; reserva guest: 3 por hora por IP (Fatia 4) | `app.limites.*` |
| H15 | Validade das sessões | **decidida (17/11)**: acesso 15 min; refresh 7 dias, renovado a cada uso | `app.seguranca.jwt.*`, `app.sessao.*` |
| H16 | O guest pode cancelar pelo link? | **decidida (16/11)**: não (ninguém cancela pelo app, R18) | — |
| H17 | A barbearia vai usar o sistema de verdade? | **Fase 1**: deploy gratuito (Vercel + Render + Aiven) com gateway em sandbox; **Fase 2** (produção real e lojas): planejamento interno da equipe | decisão do Kevin |
| H18 | Tolerância para pagamento no limite do prazo | nenhuma: expira em `expira_em`; alternativa a decidir: alguns minutos de tolerância e prazo do Pix no gateway igual a `expira_em` (crítica do Gemini) | `configuracao_agenda` (se adotado) |
| H19 | Depois de quanto tempo um evento de webhook não processado é considerado preso (N) | 10 min desde `recebido_em` | `app.pagamento.webhook.reprocessamento.idade-minima` (variável de ambiente) |
| H20 | Quantas tentativas o job faz antes de mandar o evento para revisão manual no painel (M) | 5 tentativas | `app.pagamento.webhook.reprocessamento.max-tentativas` (variável de ambiente) |
| H21 | Limite da consulta pública de CEP (a spec da Fatia 6 a chama de "H15", número que já era da validade das sessões) | **proposta (17/11, Fatia 6A)**: 30 consultas por minuto por IP; passou, `429` com `Retry-After`. É a primeira rota pública que gasta serviço de terceiros (ViaCEP e BrasilAPI) | `app.limites.cep-max` e `app.limites.cep-janela` |
| H22 | Limite da exportação de dados do titular (a spec da Fatia 6 o chama de "H15") | **proposta (17/11, Fatia 6B)**: 5 exportações por hora por conta; passou, `429` com `Retry-After`. Conta as próprias solicitações gravadas (`solicitacao_titular`), sem memória | `app.limites.exportacao-max` e `app.limites.exportacao-janela` |
| H24 | Cada barbeiro tem uma carteira de clientes (nome e contato) além da reserva manual? (a H23 é da [Fatia 9](sdd/fatia-9-assistente-de-duvidas.md)) | **decidido (Kevin, 20/11): não; reserva manual só na própria agenda** | — |
| H25 | Validade do convite do barbeiro | **decidido (Kevin, 20/11): 48 horas** | `app.conta.convite.validade` |
| H26 | O dono pode mandar o link do convite pelo WhatsApp dele? | **decidido (Kevin, 20/11): pode ir pelo WhatsApp, uso único, 48 h, revogável** | — |
| H27 | O barbeiro edita a própria jornada e as próprias folgas? | **decidido (Kevin, 20/11): não; só o dono** | `SegurancaConfig` |
| H28 | O barbeiro cancela uma reserva própria? | **decidido (Kevin, 20/11): não (D124)** | `PainelDaAgenda` |
| H29 | O barbeiro vê preço e status do pagamento das próprias reservas? | **decidido (Kevin, 20/11): sim** | painel |
| H30 | Limite de aceite de convite | **decidido (Kevin, 20/11): 10 por hora por IP** | `app.limites.convite-*` |
| H31 | Reativar um barbeiro desligado | **decidido (Kevin, 20/11): exige convite novo** | — |
| H32 | E-mail do barbeiro igual ao de uma conta de cliente | **decidido (Kevin, 20/11): pode; uma conta com CLIENTE + BARBEIRO, papel só no aceite logado, desligar tira só o papel (ADR 0013, 4.1)** | — |
| H33 | Retenção da conta do barbeiro desligado | **revisada (20/11, ADR 0013, parecer jurídico)**: não segue a H13. Anonimizar no dia do desligamento, com "lápide" para a auditoria; `nome_exibicao` nas reservas por 5 anos depois da última reserva e depois pseudônimo; IP da auditoria 1 ano; backup 30 dias. Pendente de advogado: art. 10 da LGPD e tipo de vínculo (CLT ou Lei 13.352/2016) | — |

## 5. Requisitos não funcionais

Metas **propostas**, ainda **sem medição**. O "valor atual" só entra depois de uma execução real, com data e comando.

| # | Requisito | Meta proposta | Como medir |
|---|---|---|---|
| RNF1 | Latência da disponibilidade | p95 < 300 ms com a API acordada, 2 barbeiros e 30 dias de reservas | k6 local contra `docker compose` |
| RNF2 | Latência da criação de reserva | p95 < 400 ms com a API acordada | k6 local |
| RNF3 | Memória | a API sobe e roda o roteiro e2e dentro de 512 MB (`-XX:MaxRAMPercentage=75`) | teste da imagem no CI com `--memory=512m` |
| RNF4 | Conexões com o banco | pool máximo de 5 (Aiven free: 20, com 3 a 6 reservadas) | `spring.datasource.hikari.maximum-pool-size=5` + teste de configuração do perfil `prod` |
| RNF5 | Cold start | o front avisa "servidor acordando" e tenta de novo; a API fica pronta em até ~1 min (limite do Render free) | medição manual após o deploy |
| RNF6 | Integridade da agenda | zero reservas sobrepostas, inclusive com requisições simultâneas | teste de concorrência com Testcontainers |
| RNF7 | Integrações externas | CEP: conexão 1 s, leitura 2 s; gateway: conexão 2 s, leitura 5 s (proposta) | testes com servidor falso lento (WireMock) |
| RNF8 | Acessibilidade do front | contraste AA e navegação por teclado | Lighthouse / axe na fatia do front |
| RNF9 | Privacidade | nenhum CPF, telefone ou CEP em log | teste automatizado de log (R30) |

## 6. Personas e jornadas

| Persona | Objetivo | Dor | Jornada principal |
|---|---|---|---|
| Cliente com conta | marcar rápido sem pagar antes | não sabe o que está livre | cadastro → escolhe serviço, barbeiro e horário → `CONFIRMADO` → pode cancelar dentro da janela |
| Guest | marcar sem criar conta | não quer cadastro | escolhe horário → informa contato → CPF e Pix do sinal → paga em até 3 min → `CONFIRMADO` |
| Barbeiro | ver o dia e fechar atendimentos | caderno e WhatsApp | login → agenda do dia → marca `CONCLUIDO` ou `NAO_COMPARECEU` |
| Admin (dono) | operar a barbearia | ajustar preço e jornada sem desenvolvedor | login → serviços, barbeiros, jornada e folgas → sinais e devoluções → auditoria |
| Gateway de pagamento (sistema) | avisar o pagamento | reenvia avisos | webhook autenticado → `200` |

## 7. Riscos e ameaças

### 7.1 Riscos de produto e técnicos

| # | Risco | Prob. × impacto | Mitigação | Dono |
|---|---|---|---|---|
| RISC-01 | Regras reais diferentes das hipóteses | alta × média | tudo configurável; hipóteses marcadas; perguntas ao Kevin antes da fatia S03 | Kevin |
| RISC-02 | Cold start do Render (~1 min) assusta o cliente | alta × baixa | aviso "servidor acordando" no front; é portfólio | front |
| RISC-03 | Vercel Hobby proíbe uso comercial (pagamento de visitantes) | média × alta se virar uso real | beta só com sandbox; uso real exige decisão do Kevin (H17) | Kevin |
| RISC-04 | Aiven free desligado por inatividade | média × média | documentar como religar no `README_DEPLOY`; health check do Render não acorda o banco (não usar health com consulta pesada) | deploy |
| RISC-05 | Webhook do Asaas autenticado só por token estático | média × alta | token longo aleatório, comparação em tempo constante, HTTPS, e **reconsulta da cobrança na API do gateway** antes de confirmar (ADR 0007) | pagamento |
| RISC-06 | Job de expiração atrasado prende horário | baixa × média | disponibilidade ignora vencidas (R15) e expiração preguiçosa (R16) | agenda |
| RISC-07 | Claude Code indisponível atrasa as fatias de domínio difícil | média × média | reserva: Copilot `claude-sonnet-5.5` local; revisão de arquitetura | Kevin |

### 7.2 STRIDE leve por fluxo crítico

| Fluxo | Ameaça (STRIDE) | Mitigação | Teste que prova |
|---|---|---|---|
| Agendamento | **T**ampering: cliente manda `status` ou `sinalCentavos` no corpo | DTO por operação + `fail-on-unknown-properties=true` | campo extra → `400` e nada gravado |
| Agendamento | **D**enial of service: guest bot segura todos os horários | limite por IP (H14), expiração de 3 min | `429` após o limite |
| Agendamento | **E**levation: cliente lê reserva de outro | busca filtrada por dono, `404` | `ReservaBolaIT` |
| Pagamento | **S**poofing: webhook falso confirma reserva | autenticação do provedor + reconsulta na API do gateway | `401` com token errado; evento com cobrança inexistente não confirma |
| Pagamento | **R**epudiation: "não paguei duas vezes" | `evento_webhook` guarda id, tipo e data; `cobranca` guarda o id do provedor | consulta de auditoria |
| Pagamento | **I**nformation disclosure: CPF em log | CPF não é persistido (ADR 0009); `Cpf.toString()` mascara | `SemDadoPessoalNoLogIT` |
| Login | **S**poofing: força bruta na senha do admin | limite + `429` + BCrypt/Argon2 | `LimiteTentativasIT` |
| Login | **T**ampering: JWT forjado | HS256 com segredo ≥ 32 bytes por env; a API não sobe sem ele em `prod` | token adulterado → `401` |
| Login | **I**nformation disclosure: refresh roubado por XSS | refresh só em cookie `HttpOnly` no BFF, nunca no JS | teste do BFF (fatia do front) |
| Painel | **E**levation: cliente chama `/admin/**` | `SecurityFilterChain` por papel | `403` em toda rota `/admin/**` |

### 7.3 OWASP API Security Top 10 (2023)

| Item | Onde pega neste projeto | Mitigação | Teste |
|---|---|---|---|
| API1 BOLA | `/agendamentos/{id}`, `/agendamentos/guest/{id}`, `/admin/agenda?barbeiroId=` (barbeiro) | consulta já filtrada por dono; guest com token próprio | `404` para recurso alheio em toda rota com id |
| API2 Autenticação quebrada | login, refresh, admin | limite, refresh rotativo com detecção de reuso, segredo forte | R26, R27 |
| API3 BOPLA | todo `@RequestBody` e resposta | DTO por operação; resposta sem `senhaHash`, sem CPF | `jsonPath("$.senhaHash").doesNotExist()` |
| API4 Consumo sem limite | listagens, disponibilidade, guest | página máxima 50; disponibilidade por um dia; limite de taxa | página 500 → `400` |
| API5 Autorização por função | `/admin/**` | papéis na `SecurityFilterChain` | `403` para `CLIENTE` em cada rota |
| API6 Fluxo de negócio sensível | reserva guest (segurar horários), cobrança | limite por IP, expiração, idempotência | R10, R26 |
| API7 SSRF | clientes de CEP e gateway | URLs fixas por configuração, CEP validado com 8 dígitos antes da chamada | CEP com letras → `400` sem chamada externa |
| API8 Configuração insegura | perfis | `prod` sem Swagger, sem seed, sem defaults; CORS por env; health sem detalhes | teste por perfil |
| API9 Inventário | versões e rotas | contrato versionado + OpenAPI gerado; Swagger só em `dev` | Swagger `404` em `prod` |
| API10 Consumo inseguro de APIs | ViaCEP, BrasilAPI, gateway | timeouts, validação da resposta, reconsulta da cobrança no gateway | WireMock com resposta inválida |

## 8. LGPD: inventário de dados pessoais

| Dado | De quem | Finalidade | Base legal (proposta) | Onde fica | Quem acessa | Retenção | Proteção |
|---|---|---|---|---|---|---|---|
| Nome | cliente, guest, barbeiro | identificar no atendimento | execução de contrato (art. 7º, V) | `usuario`, `reserva_guest` | admin, barbeiro da reserva | enquanto a conta existir; guest: H13 | não vai para log |
| E-mail | cliente, guest, admin | login e contato | execução de contrato | `usuario`, `reserva_guest` | admin | idem | único, minúsculas |
| WhatsApp | cliente, guest | contato sobre o horário | execução de contrato | `usuario`, `reserva_guest` | admin, barbeiro da reserva | idem | sanitizado; mascarado no log (`Telefone.toString()`) |
| CEP, bairro, cidade, UF | cliente (opcional) | operação: endereço do cadastro. Marketing: **só com consentimento** | operação: execução de contrato; marketing: consentimento (art. 7º, I) | `usuario` | admin; painel de alcance só agregado k ≥ 5 (pós-beta) | enquanto a conta existir | nunca em log; agregação mínima de 5 |
| CPF | guest, no pagamento | gerar a cobrança no gateway | execução de contrato / procedimentos preliminares | **não persistido** pela API: repassado ao gateway na criação do cliente da cobrança (ADR 0009) | ninguém na barbearia | não guardado | nunca em log; `Cpf.toString()` mascara |
| Senha | cliente, barbeiro, admin | autenticação | execução de contrato | `usuario.senha_hash` (BCrypt ou Argon2) | ninguém | enquanto a conta existir | só hash |
| Consentimento de marketing | cliente | provar o consentimento | consentimento | `consentimento` | admin (auditoria) | enquanto a conta existir + prova | versão do texto e data |
| IP | todos | limite de tentativas e auditoria do admin | legítimo interesse (segurança) | memória (limitador) e `auditoria_admin` | admin | auditoria: 1 ano (proposta) | não vai para log de aplicação junto com dado pessoal |
**Direitos do titular (Fatia 6B):** `GET /me/dados` (exportar em JSON: conta, histórico de consentimentos e as próprias reservas; 5 por hora, H22), `DELETE /me` (só cliente: anonimiza a conta e cancela as reservas futuras **não pagas**; com reserva futura paga a exclusão é bloqueada, `409 EXCLUSAO_BLOQUEADA_POR_RESERVA`; o registro financeiro do sinal fica pelo prazo de H13). Consentimento revogável em `PUT /me/consentimentos/marketing`; texto e versão em `GET /politica-privacidade` (rascunho técnico, não é parecer jurídico). Retenção: contato do visitante 90 dias após o fim da reserva, IP da auditoria 1 ano (`RetencaoDeDados`, 3h).
**Direitos do titular:** `GET /me/dados` (exportar em JSON), `DELETE /me` (excluir: anonimiza conta e reservas futuras são canceladas; o registro financeiro do sinal fica pelo prazo de H13). Consentimento revogável em `PUT /me/consentimentos/marketing`.

## 9. Dependências externas

| Serviço | Para quê | Limite / SLA conhecido | Se cair |
|---|---|---|---|
| ViaCEP | endereço pelo CEP | sem SLA publicado | tenta a BrasilAPI |
| BrasilAPI | fallback de CEP | sem SLA publicado | salva o CEP sem bairro e cidade (R31) |
| Asaas (sandbox) | cobrança Pix do sinal e webhook | eventos guardados 14 dias; fila pausa após 15 falhas seguidas (fonte: verificação interna de 13/11/2026) | criar cobrança dá `503 GATEWAY_INDISPONIVEL`; a reserva continua até expirar |
| Aiven PostgreSQL (free) | banco | 1 GB, 20 conexões, pode desligar por inatividade | API fica fora (health `DOWN`) |
| Render (free) | API | 512 MB, hiberna após 15 min | front mostra "servidor acordando" |
| Vercel (Hobby) | front e BFF | uso não comercial | — |
| Google / Meta (pós-beta) | login social | — | flag desligada |

## 10. Custos

| Item | Custo | Fonte e data |
|---|---|---|
| Render web service free | US$ 0 (750 h/mês por workspace) | render.com/docs/free, 13/11/2026 |
| Aiven PostgreSQL free | US$ 0, sem cartão | aiven.io/docs/products/postgresql/concepts/pg-free-tier, 13/11/2026 |
| Vercel Hobby | US$ 0, uso não comercial | vercel.com/docs/plans/hobby, 13/11/2026 |
| Asaas sandbox | US$ 0, sem movimentar dinheiro | docs.asaas.com/docs/sandbox, 13/11/2026 |
| IAs | assinaturas que o Kevin já tem; nenhuma chave de API | skill de orquestração |

**Resumo:** custo zero no beta. Uso real com clientes pagando muda a conta (Vercel Pro e taxas do gateway): fica para decisão do Kevin (H17).

## 11. Métricas de sucesso

| Métrica | Meta | Valor atual |
|---|---|---|
| Tempo para um recrutador rodar o roteiro e2e do zero | ≤ 15 min | sem medição (sem código) |
| Testes de integração com PostgreSQL real verdes no CI | 100% | sem medição |
| Rotas com teste de 401/403/404 | 100% | sem medição |
| Reservas sobrepostas no teste de concorrência | 0 | sem medição |

## 12. Roadmap e débitos

- Roadmap em fatias: [README](../README.md#-roadmap-em-fatias)
- Decisões pequenas: [`planejamento/decisoes.md`](planejamento/decisoes.md)
