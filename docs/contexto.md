# 🧭 Dossiê de contexto · Barbearia Altas Horas

| | |
|---|---|
| 🎯 **Para que serve** | o "por quê", "para quem" e "até onde" do projeto: a fonte do contrato, dos ADRs e do README |
| 👥 **Área responsável** | Kevin (regras de negócio) · coordenador (texto) · Alicerce (arquitetura) |
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

Cada regra vira pelo menos um teste que cita o número. "Origem" diz de onde veio: **P** = prompt aprovado pelo Kevin em 13/11/2026; **A** = arquitetura do Alicerce; **H** = hipótese (seção 4).

| # | Regra | Origem | Teste previsto (fatia) |
|---|---|---|---|
| R1 | Um barbeiro não pode ter duas reservas que se sobrepõem entre as que ocupam horário (`AGUARDANDO_PAGAMENTO`, `CONFIRMADO`). Garantido pelo banco (`EXCLUDE USING gist`), não só pelo código. | P, A | `ReservaConcorrenciaIT` (S01, S07) |
| R2 | O intervalo de uma reserva é meio aberto `[inicio, fim_ocupacao)`: uma reserva que termina às 10h00 não conflita com outra que começa às 10h00. | A | `ReservaConstraintIT` (S01) |
| R3 | `fim = inicio + duração do serviço`; `fim_ocupacao = fim + intervalo entre atendimentos` (configurável, **H4**). | P, H4 | `CalculoHorarioTest` (S03) |
| R4 | Só é possível reservar dentro da jornada do barbeiro, fora das folgas, começando em múltiplos da granularidade (**H11**). | P, H11 | `DisponibilidadeTest` (S03) |
| R5 | Só é possível reservar com antecedência mínima configurável (**H5**) e até um horizonte máximo configurável (**H12**). | H5, H12 | `AntecedenciaTest` (S07) |
| R6 | Cliente logado reserva direto em `CONFIRMADO`, sem sinal. | P | `AgendarClienteIT` (S07) |
| R7 | Guest reserva em `AGUARDANDO_PAGAMENTO`, com `expira_em = agora + tempo de expiração` (15 min, configurável). A reserva ocupa o horário enquanto não expira. | P | `AgendarGuestIT` (S11) |
| R8 | O sinal é **50%** (percentual configurável) do preço do serviço **no momento da reserva**, em centavos, arredondado para cima no centavo. Mudar o preço depois não muda o sinal. | P, A | `SinalTest` (S11) |
| R9 | O CPF é pedido **só** na criação da cobrança do guest, porque o gateway exige. Não é pedido no cadastro. | P | `CobrancaIT` (S12) |
| R10 | A criação de cobrança exige `X-Idempotency-Key`. Mesma chave e mesmo corpo devolvem a mesma cobrança; mesma chave e corpo diferente dão `422`. | P | `CobrancaIdempotenciaIT` (S12) |
| R11 | O webhook só é aceito com autenticação válida do provedor (ADR 0007); senão `401` e nada muda. | P | `WebhookAssinaturaIT` (S13) |
| R12 | O id do **evento** do provedor é único (`evento_webhook`). Evento repetido responde `200` sem efeito. | P | `WebhookIdempotenciaIT` (S13) |
| R13 | Pagamento confirmado de reserva ainda válida → reserva vai para `CONFIRMADO`. | P, A | `ConfirmacaoPagamentoIT` (S13) |
| R14 | Pagamento confirmado **depois** de a reserva expirar: a reserva **não** volta; a cobrança fica `PAGA_APOS_EXPIRACAO` e aparece no painel para devolução manual (**H8**). | A, H8 | `PagamentoTardioIT` (S13) |
| R15 | Reserva `AGUARDANDO_PAGAMENTO` com `expira_em` no passado não ocupa horário na disponibilidade, mesmo antes do job rodar. | P | `DisponibilidadeExpiracaoIT` (S03, S14) |
| R16 | Antes de inserir uma reserva, as reservas vencidas que conflitam com o horário são expiradas na mesma transação (expiração preguiçosa), para a constraint não recusar um horário que a tela mostrou livre. | A (ADR 0003) | `ExpiracaoPreguicosaIT` (S07) |
| R17 | Um job com ShedLock marca `EXPIRADO` as reservas vencidas a cada minuto (intervalo configurável) e publica `ReservaExpirada`. | P, A | `ExpiracaoJobIT` (S14) |
| R18 | O cliente cancela ou remarca a própria reserva só até a janela mínima antes do início (**H6**). Fora da janela: `422`. | P, H6 | `CancelamentoTest` (S08) |
| R19 | Remarcar é atômico: o horário novo passa pela mesma constraint; se ocupado, a reserva antiga fica como estava. | P | `RemarcacaoIT` (S08) |
| R20 | Transições válidas são só as da [máquina de estados](dominio/modelo-de-dominio.md#2-máquina-de-estados); qualquer outra dá `422 TRANSICAO_INVALIDA`. | P | `ReservaStatusTest` (S10) |
| R21 | `CONCLUIDO` e `NAO_COMPARECEU` só a partir do início da reserva; quem marca é ADMIN ou o BARBEIRO dono da agenda (**H9**). | P, H9 | `FechamentoAtendimentoIT` (S10) |
| R22 | Cancelamento pela barbearia exige motivo; o motivo e quem cancelou ficam registrados. | P | `CancelamentoAdminIT` (S10) |
| R23 | Quem não é dono de uma reserva recebe `404` ao acessá-la (não confirma que o id existe). | P | `ReservaBolaIT` (S08) |
| R24 | E-mail é único por conta, sem diferença de maiúsculas. | P | `CadastroIT` (S04) |
| R25 | O primeiro admin é criado na subida a partir de `ADMIN_EMAIL` e `ADMIN_SENHA` só se ainda não existir admin; a senha é gravada só como hash e nunca é sobrescrita por variável depois. | P | `AdminInicialIT` (S06) |
| R26 | Login (inclusive do admin), cadastro e reserva guest têm limite de tentativas (**H14**); passou do limite: `429` com `Retry-After`. | P, H14 | `LimiteTentativasIT` (S06, S11) |
| R27 | Refresh token é de uso único e rotativo; reuso de um refresh já usado revoga toda a família da sessão. | P, A | `RefreshRotativoIT` (S05) |
| R28 | Consentimento de marketing é opcional, nasce desmarcado, guarda versão do texto e data, e pode ser revogado a qualquer momento. | P | `ConsentimentoIT` (S16) |
| R29 | Dados de contato do guest são anonimizados depois do prazo de retenção (**H13**). | P | `RetencaoGuestIT` (S16) |
| R30 | CPF, telefone e CEP nunca aparecem em log. | P | `SemDadoPessoalNoLogIT` (S16 e em toda fatia) |
| R31 | A consulta de CEP nunca derruba o cadastro: com ViaCEP e BrasilAPI fora, o CEP é salvo e bairro e cidade ficam para completar. | P | `CepDegradacaoIT` (S15) |
| R32 | Rotas de login social respondem `404` enquanto a flag estiver desligada. | P | `SocialDesligadoIT` (pós-beta) |

## 4. Hipóteses em aberto

Perguntas enviadas ao Kevin (via Black) em 13/11/2026. Até a resposta, vale o **valor provisório**, sempre como dado configurável ou seed fictício do perfil `dev`.

| # | Pergunta | Valor provisório | Onde fica configurado |
|---|---|---|---|
| H1 | Dias e horário de funcionamento | terça a sábado, 9h às 19h (fictício) | `jornada` de cada barbeiro (painel) |
| H2 | Quantos barbeiros atendem | 2 barbeiros fictícios no seed `dev` | cadastro de barbeiros (painel) |
| H3 | Serviços, duração e preço | seed fictício: Corte 30 min R$ 40,00 · Barba 20 min R$ 30,00 · Corte + barba 50 min R$ 65,00 | cadastro de serviços (painel) |
| H4 | Intervalo entre atendimentos | 0 min | `configuracao_agenda.intervalo_entre_atendimentos_min` |
| H5 | Antecedência mínima para agendar | 60 min | `configuracao_agenda.antecedencia_minima_min` |
| H6 | Janela para cancelar ou remarcar | até 120 min antes | `configuracao_agenda.janela_cancelamento_min` |
| H7 | Política do sinal em falta ou cancelamento | falta: o sinal fica com a barbearia; cancelamento dentro da janela: devolução **manual** pelo painel; fora da janela: o sinal fica | `configuracao_agenda.politica_sinal` (texto exibido) + status da cobrança |
| H8 | Pagamento que chega depois de a reserva expirar | registrar, **não** reativar a reserva e marcar para devolução manual no painel | regra R14 |
| H9 | O barbeiro vê só a própria agenda ou a de todos? | **só a própria** (filtro por dono; `404` na de outro) | regra de autorização do módulo `admin` |
| H10 | Pacote raiz Java | `io.github.k3vinrich4rd.altashoras` | ADR 0001 |
| H11 | Granularidade dos horários oferecidos | 30 min | `configuracao_agenda.granularidade_min` |
| H12 | Até quantos dias à frente dá para agendar | 30 dias | `configuracao_agenda.horizonte_dias` |
| H13 | Retenção dos dados do guest e do histórico do sinal | contato do guest: anonimizar 90 dias após o atendimento; registro financeiro da cobrança: 5 anos (**pendente de fonte legal**) | `app.privacidade.retencao-guest-dias` e ADR 0009 |
| H14 | Limites de tentativas | login: 5 erros em 15 min por e-mail + IP; cadastro: 5 por hora por IP; reserva guest: 3 por hora por IP | `app.limites.*` |
| H15 | Validade das sessões | acesso: 15 min; refresh: 7 dias | `app.seguranca.jwt.*` |
| H16 | O guest pode cancelar pelo link? | não no beta (só pela barbearia) | roadmap |
| H17 | A barbearia vai usar o sistema de verdade (com clientes pagando)? | **não no beta**: portfólio com Pix de sandbox. Uso real exige rever a Vercel Hobby (RISC-03) e trocar o sandbox pela produção do gateway | decisão do Kevin |
| H18 | Tolerância para pagamento no limite do prazo | nenhuma: expira em `expira_em`; alternativa a decidir: alguns minutos de tolerância e prazo do Pix no gateway igual a `expira_em` (crítica do Gemini) | `configuracao_agenda` (se adotado) |

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
| Guest | marcar sem criar conta | não quer cadastro | escolhe horário → informa contato → CPF e Pix do sinal → paga em até 15 min → `CONFIRMADO` |
| Barbeiro | ver o dia e fechar atendimentos | caderno e WhatsApp | login → agenda do dia → marca `CONCLUIDO` ou `NAO_COMPARECEU` |
| Admin (dono) | operar a barbearia | ajustar preço e jornada sem desenvolvedor | login → serviços, barbeiros, jornada e folgas → sinais e devoluções → auditoria |
| Gateway de pagamento (sistema) | avisar o pagamento | reenvia avisos | webhook autenticado → `200` |

## 7. Riscos e ameaças

### 7.1 Riscos de produto e técnicos

| # | Risco | Prob. × impacto | Mitigação | Dono |
|---|---|---|---|---|
| RISC-01 | Regras reais diferentes das hipóteses | alta × média | tudo configurável; hipóteses marcadas; perguntas ao Kevin antes da fatia S03 | Kevin |
| RISC-02 | Cold start do Render (~1 min) assusta o cliente | alta × baixa | aviso "servidor acordando" no front; é portfólio | Decolagem / front |
| RISC-03 | Vercel Hobby proíbe uso comercial (pagamento de visitantes) | média × alta se virar uso real | beta só com sandbox; uso real exige decisão do Kevin (H17) | Kevin |
| RISC-04 | Aiven free desligado por inatividade | média × média | documentar como religar no `README_DEPLOY`; health check do Render não acorda o banco (não usar health com consulta pesada) | Decolagem |
| RISC-05 | Webhook do Asaas autenticado só por token estático | média × alta | token longo aleatório, comparação em tempo constante, HTTPS, e **reconsulta da cobrança na API do gateway** antes de confirmar (ADR 0007) | pagamento |
| RISC-06 | Job de expiração atrasado prende horário | baixa × média | disponibilidade ignora vencidas (R15) e expiração preguiçosa (R16) | agenda |
| RISC-07 | Claude Code indisponível atrasa as fatias de domínio difícil | média × média | reserva: Copilot `claude-sonnet-5.5` local; revisão do Alicerce | coordenador |

### 7.2 STRIDE leve por fluxo crítico

| Fluxo | Ameaça (STRIDE) | Mitigação | Teste que prova |
|---|---|---|---|
| Agendamento | **T**ampering: cliente manda `status` ou `sinalCentavos` no corpo | DTO por operação + `fail-on-unknown-properties=true` | campo extra → `400` e nada gravado |
| Agendamento | **D**enial of service: guest bot segura todos os horários | limite por IP (H14), expiração de 15 min | `429` após o limite |
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

**Direitos do titular:** `GET /me/dados` (exportar em JSON), `DELETE /me` (excluir: anonimiza conta e reservas futuras são canceladas; o registro financeiro do sinal fica pelo prazo de H13). Consentimento revogável em `PUT /me/consentimentos/marketing`.

## 9. Dependências externas

| Serviço | Para quê | Limite / SLA conhecido | Se cair |
|---|---|---|---|
| ViaCEP | endereço pelo CEP | sem SLA publicado | tenta a BrasilAPI |
| BrasilAPI | fallback de CEP | sem SLA publicado | salva o CEP sem bairro e cidade (R31) |
| Asaas (sandbox) | cobrança Pix do sinal e webhook | eventos guardados 14 dias; fila pausa após 15 falhas seguidas ([fonte](ia/verificacao-coordenador-2026-11-13.md)) | criar cobrança dá `503 GATEWAY_INDISPONIVEL`; a reserva continua até expirar |
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

- Plano de fatias: [`planejamento/plano-de-fatias.md`](planejamento/plano-de-fatias.md)
- Decisões pequenas: [`planejamento/decisoes.md`](planejamento/decisoes.md)
- Recados entre agentes: [`planejamento/recados.md`](planejamento/recados.md)
