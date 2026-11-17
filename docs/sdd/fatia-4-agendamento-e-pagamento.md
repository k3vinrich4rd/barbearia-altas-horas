# 📐 Spec · Fatia 4 · Agendamento e pagamento

| | |
|---|---|
| **Status** | 📝 **aguarda aprovação do Kevin** (escrita em 17/11/2026) |
| **Fatias do plano** | S07 (reserva do cliente) + S08 sem cancelar/remarcar (leitura) + S11 (reserva do visitante) + S12 (cobrança) + S13 (webhook e pagamento tardio) + S14 (job de expiração) |
| **Entrega em duas PRs** | **4A** · reserva e expiração (`feat/fatia-4a-reserva`) · **4B** · cobrança, webhook e conciliação (`feat/fatia-4b-pagamento`). A 4B só começa com a 4A na `main` |
| **Regras** | [R1, R2](../contexto.md#3-regras-de-negócio) (constraint), R5 (antecedência e horizonte), R6 a R8 (todo agendamento paga 100% antes), R9 e R10 (CPF e idempotência), R11 a R14 (webhook e pagamento tardio), R15 a R17 (expiração), R18 (sem cancelar ou remarcar, aceite obrigatório), R23 (recurso alheio → 404), R26 (limite do visitante), R30 (nada pessoal em log) |
| **ADRs** | [0002](../adr/0002-comunicacao-entre-modulos.md) (eventos), [0003](../adr/0003-anti-double-booking-no-banco.md) (constraint e expiração preguiçosa), [0007](../adr/0007-gateway-de-pagamento-sandbox.md) (Asaas sandbox, webhook), [0009](../adr/0009-lgpd-marketing-cpf-e-retencao.md) (CPF só em trânsito), [0011](../adr/0011-hexagonal-seletivo-no-monolito-modular.md) (porta do gateway) |
| **Não cobre** | painel do barbeiro e as rotas `/admin/**` (Fatia 5) · CEP e direitos do titular (Fatia 6) · telas (Fatia 7) · gateway em produção (Fase 2) · estorno automático (pós-beta) |

## 1. Objetivo

O cliente, com conta ou sem conta, escolhe um horário livre, o horário fica **travado por 15 minutos**, ele aceita a política e **paga 100%** por Pix ou cartão, e a reserva vira `CONFIRMADO` quando o gateway avisa. Quem não paga libera o horário sozinho. É o coração do produto: depois desta fatia, o Eduardo já poderia receber agendamentos pagos (no sandbox).

## 2. Contrato

### 4A · reserva e expiração

| Método | Rota | Quem | Sucesso | Erros |
|---|---|---|---|---|
| `POST` | `/agendamentos` | cliente logado | `201` `AGUARDANDO_PAGAMENTO` + `expiraEm` | `400` · `401` · `404` (serviço ou barbeiro) · `409 HORARIO_INDISPONIVEL` · `409 CONFLITO_CONCORRENTE` · `422 FORA_DO_EXPEDIENTE` · `422 ANTECEDENCIA_INSUFICIENTE` · `422 FORA_DO_HORIZONTE` · `422 SERVICO_INATIVO` |
| `POST` | `/agendamentos/guest` | público (limite por IP) | `201` + `tokenGuest` (mostrado uma vez) | os mesmos da linha acima, sem `401` · `429 LIMITE_EXCEDIDO` |
| `GET` | `/agendamentos/guest/{id}` | quem tem o `X-Guest-Token` | `200` | `404` (inclusive token errado) |
| `GET` | `/me/agendamentos` | cliente logado | `200` paginado | `401` |
| `GET` | `/agendamentos/{id}` | dono | `200` | `401` · `404` (de outro dono, R23) |

### 4B · cobrança e webhook

| Método | Rota | Quem | Sucesso | Erros |
|---|---|---|---|---|
| `POST` | `/pagamentos/cobrancas` | dono da reserva (bearer ou `X-Guest-Token`) | `201` (`200` na repetição) | `400 CPF_INVALIDO` · `400 IDEMPOTENCY_KEY_AUSENTE` · `401` · `404` · `409 COBRANCA_EM_ANDAMENTO` · `422 ACEITE_POLITICA_OBRIGATORIO` · `422 RESERVA_EXPIRADA` · `422 IDEMPOTENCIA_CONFLITO` · `429` · `503 GATEWAY_INDISPONIVEL` |
| `POST` | `/pagamentos/webhook` | o gateway (`asaas-access-token`) | `200` | `401 WEBHOOK_NAO_AUTENTICADO` · `5xx` (para o gateway reenviar) |

**Ajustes no contrato (v1.3.0), aplicados depois da aprovação:** código novo `409 COBRANCA_EM_ANDAMENTO` (E21); `GET /agendamentos/guest/{id}` passa a conciliar com o gateway (E25); `POST /agendamentos/guest` documenta o limite de 3 por hora por IP.

## 3. Decisões de desenho

### 4A · reserva e expiração

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E1 | Como validar o horário pedido | o mesmo `CalculadoraDeHorarios` da Fatia 2, **sem os bloqueios de reservas**: o `inicio` precisa estar na grade, caber na jornada, respeitar antecedência e horizonte e não cair em folga. Se cair numa reserva, quem responde é a constraint | a tela e a reserva usam a mesma regra; a disputa de horário fica com o banco, que é o único que vence corrida |
| E2 | Qual erro para cada falha | fora da grade, da jornada ou em folga → `422 FORA_DO_EXPEDIENTE`; antes de agora + 2 h → `422 ANTECEDENCIA_INSUFICIENTE`; fora de hoje..hoje+30 → `422 FORA_DO_HORIZONTE`; ocupado → `409 HORARIO_INDISPONIVEL` | o front decide pelo `code` (esconder o horário só no 409) |
| E3 | Expiração preguiçosa (R16) | na mesma transação do `INSERT`: `UPDATE reserva SET status='EXPIRADO' WHERE id IN (SELECT id ... vencidas que sobrepõem o horário ORDER BY id FOR UPDATE)` | sem isso, uma pendente vencida que o job ainda não expirou faria a constraint recusar um horário que a tela mostrou livre |
| E4 | Deadlock (`40P01`) | a transação inteira é refeita **uma vez**; persistiu → `409 CONFLITO_CONCORRENTE` (D27) | o teste de concorrência da Fatia 2 já mostrou deadlock sob carga |
| E5 | Entidade `Reserva` | entidade JPA com `@Version` e uma máquina de estados **sem framework** (`StatusReserva.podeIrPara`), testada em unidade | regra difícil isolada (ADR 0011); `TRANSICAO_INVALIDA` nasce aqui |
| E6 | Preço | `preco_centavos` copiado do serviço na reserva | mudar o preço depois não muda o que o cliente vai pagar (R8) |
| E7 | Token do visitante | 32 bytes aleatórios (`g1_` + base64url), guardado só como SHA-256 em `reserva_guest.token_hash`; comparado em tempo constante; token errado ou ausente → `404` | vazou o banco, não vazou o acesso; `404` não confirma que a reserva existe |
| E8 | Dados do visitante | `nome`, `email`, `whatsapp` em `reserva_guest` (tabela separada da reserva); **sem CPF aqui** (R9) | facilita anonimizar depois (Fatia 6) e mantém a reserva sem dado pessoal |
| E9 | Limite do visitante (R26) | 3 reservas por hora por IP, pela mesma porta `LimitadorDeTentativas` da Fatia 3 | evita alguém travar a agenda inteira com reservas sem pagar |
| E10 | Job de expiração (R17) | `@Scheduled` a cada 1 min **e na subida**, com **ShedLock** (`shedlock` já existe na V1), lotes de 100 com `FOR UPDATE SKIP LOCKED` | o Render free hiberna; a disponibilidade já ignora vencidas (R15), então o job só arruma o status |
| E11 | Eventos entre módulos | `ReservaExpirada` publicado pela agenda; **outbox do Spring Modulith** (`spring-modulith-starter-jdbc`) com a tabela `event_publication` numa migração `V3`, usando o schema oficial da versão 2.1.2 (D41) | o evento fica gravado na mesma transação da mudança: se a API cair entre o commit e o listener, ele é reenviado |
| E12 | Leitura dos meus agendamentos | `GET /me/agendamentos` paginado (`pagina`, `tamanho` até 50), mais recentes primeiro, com **projeção** (sem N+1), filtrado pelo `sub` do token | o id do dono nunca vem de parâmetro |

### 4B · cobrança e webhook

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E13 | Porta do gateway | interface `GatewayDePagamento` no módulo `pagamento` (criar cobrança, consultar cobrança); adaptador `AsaasGateway` com `RestClient`; nos testes, um adaptador falso em memória e um teste do adaptador real com `MockRestServiceServer` | trocar Asaas por Mercado Pago é escrever outro adaptador (ADR 0007, ADR 0011); nenhuma dependência nova de teste |
| E14 | Ordem na criação (D21) | 1) grava `cobranca PENDENTE` com o hash da `X-Idempotency-Key`, o HMAC do corpo e o id do provedor nulo; 2) chama o Asaas **fora de transação** com `externalReference = cobranca.id`; 3) atualiza a mesma linha com o id do Asaas, o Pix ou a `urlCheckout` | a chave de idempotência é nossa; se a API cair entre 2 e 3, a cobrança é achada pelo `externalReference` |
| E15 | Pix e cartão | Pix: cobrança `billingType=PIX` e QR Code (`/payments/{id}/pixQrCode`); cartão: `billingType=CREDIT_CARD` e o link da fatura (`invoiceUrl`) vira `urlCheckout`. **O número do cartão nunca passa pela API** (D32) | fora do escopo pesado do PCI DSS |
| E16 | CPF | validado pelos dígitos (`Cpf` em `comum`, mascarado no `toString`), enviado ao Asaas na criação do cliente e **descartado**; só o `cpfMascarado` aparece no `201` | ADR 0009, R9 |
| E17 | Idempotência (R10) | mesma chave e mesmo corpo → `200` com a mesma cobrança, sem chamar o gateway; mesma chave e corpo diferente → `422 IDEMPOTENCIA_CONFLITO`; comparação por **HMAC-SHA256** com `IDEMPOTENCIA_HMAC_SEGREDO` | o CPF entra no corpo; hash sem segredo seria revertido por força bruta (D17) |
| E18 | Aceite (R18) | `aceitePolitica: true` e `versaoPolitica` igual à vigente; grava versão e data **na reserva**, pela API pública da agenda | prova do aceite em caso de contestação |
| E19 | Webhook autenticado | token do cabeçalho `asaas-access-token` comparado em tempo constante com `GATEWAY_SEGREDO_WEBHOOK`; errado → `401` sem gravar nada | ADR 0007 |
| E20 | Webhook idempotente (R12) | `INSERT evento_webhook ... ON CONFLICT DO NOTHING`; evento já processado → `200` sem efeito; evento gravado e não processado → reprocessa; **reconsulta a cobrança** no gateway antes de marcar `PAGA` | o gateway entrega "pelo menos uma vez" e o corpo pode ser forjado (OWASP API10) |
| E21 | Duas cobranças para a mesma reserva | já existe cobrança `PENDENTE` com **outra** chave → `409 COBRANCA_EM_ANDAMENTO` com o id da existente | uma cobrança viva por reserva (`cobranca_ativa_por_reserva_uk`), sem o cliente pagar duas vezes |
| E22 | Pagamento confirmado (R13) | `PagamentoConfirmado` (outbox) → a agenda confirma a reserva se ela ainda está `AGUARDANDO_PAGAMENTO` | módulos conversam por evento (ADR 0002) |
| E23 | Pagamento tardio (R14, D35) | reserva já `EXPIRADO`: tenta `EXPIRADO → CONFIRMADO` passando pela constraint; horário ocupado (`23P01`) → a reserva fica `EXPIRADO` e a cobrança vira `PAGA_APOS_EXPIRACAO` (pendência para o painel da Fatia 5) | quem pagou não perde o horário se ele ainda estiver livre |
| E24 | Reserva expirada antes de pagar | `ReservaExpirada` → cobrança `PENDENTE` vira `CANCELADA` localmente | não fica cobrança viva para reserva morta |
| E25 | Conciliação | `GET /agendamentos/guest/{id}` e `GET /agendamentos/{id}` com cobrança `PENDENTE` reconsultam o gateway **no máximo a cada 10 s** por cobrança e aplicam o mesmo fluxo do webhook | se o webhook atrasar (API hibernando), a tela do cliente ainda vira "Confirmado" |
| E26 | Eventos presos | job com ShedLock a cada 5 min e na subida: `processado_em IS NULL` há mais de 10 min (H19), lotes com `SKIP LOCKED`, até 5 tentativas (H20) e depois `REVISAO_MANUAL` | ADR 0007, D28 |
| E27 | Chamadas externas | sempre fora de `@Transactional`; timeout de conexão 2 s e de leitura 5 s; falha → `503 GATEWAY_INDISPONIVEL` | pool de 5 conexões (D12) não fica preso esperando a rede |
| E28 | Configuração | `GATEWAY_URL_BASE`, `GATEWAY_TOKEN_SANDBOX`, `GATEWAY_SEGREDO_WEBHOOK`, `IDEMPOTENCIA_HMAC_SEGREDO` só por ambiente; em `dev` sem token, o adaptador **falso** fica ativo e um endpoint só do perfil `dev` simula o pagamento | dá para testar ponta a ponta sem conta no Asaas; com a conta, troca-se para o sandbox real |

## 4. Critérios de aceite

Relógio fixo: terça, 17/11/2026, 08:00 (-03:00). Dia de teste: terça, 01/12/2026.

### 4A · reserva

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | cliente logado, horário livre | `POST /agendamentos` | `201`, `Location`, `AGUARDANDO_PAGAMENTO`, `expiraEm` = agora + 15 min, `precoCentavos` do serviço | `ReservaClienteIT` |
| A2 | horário já reservado | reservar o mesmo | `409 HORARIO_INDISPONIVEL` | `ReservaClienteIT` |
| A3 | 10:15 (fora da grade) · 12:00 (almoço) · segunda · dentro de uma folga | reservar | `422 FORA_DO_EXPEDIENTE` | `ReservaClienteIT` |
| A4 | agora + 1h50 · data 18/12 | reservar | `422 ANTECEDENCIA_INSUFICIENTE` · `422 FORA_DO_HORIZONTE` | `ReservaClienteIT` |
| A5 | serviço de 60 min às 11:30 | reservar | `422 FORA_DO_EXPEDIENTE` (invadiria o almoço) | `ReservaClienteIT` |
| A6 | serviço inativo · serviço inexistente | reservar | `422 SERVICO_INATIVO` · `404` | `ReservaClienteIT` |
| A7 | sem token · corpo com `status` ou `precoCentavos` | reservar | `401` · `400 CAMPO_DESCONHECIDO` | `ReservaClienteIT` |
| A8 | pendente **vencida** que o job ainda não expirou, no mesmo horário | reservar | `201`; a vencida vira `EXPIRADO` na mesma transação (R16) | `ExpiracaoPreguicosaIT` |
| A9 | 10 clientes disputando o mesmo horário ao mesmo tempo | reservar | exatamente um `201`; os outros `409` (`HORARIO_INDISPONIVEL` ou `CONFLITO_CONCORRENTE`), nunca `500` | `ReservaConcorrenteIT` |
| A10 | máquina de estados | todas as transições | só as do [modelo](../dominio/modelo-de-dominio.md#2-máquina-de-estados) passam; qualquer outra → `TRANSICAO_INVALIDA` | `StatusReservaTest` |
| A11 | visitante, horário livre | `POST /agendamentos/guest` com nome, e-mail e WhatsApp | `201`, `AGUARDANDO_PAGAMENTO`, `tokenGuest` começando com `g1_`; no banco só o SHA-256 do token; **nenhum CPF pedido** | `ReservaGuestIT` |
| A12 | reserva do visitante | `GET /agendamentos/guest/{id}` com o token · com token errado · sem token | `200` · `404` · `404` | `ReservaGuestIT` |
| A13 | 3 reservas de visitante do mesmo IP em 1 hora | 4ª | `429 LIMITE_EXCEDIDO` com `Retry-After` | `ReservaGuestIT` |
| A14 | cliente A com duas reservas | `GET /me/agendamentos` | `200`, as duas, mais recentes primeiro, paginado | `MeusAgendamentosIT` |
| A15 | reserva do cliente A | cliente B pede `GET /agendamentos/{id}` | `404` (R23) | `MeusAgendamentosIT` |
| A16 | cliente A | `POST /agendamentos/{id}/cancelamento` · `/remarcacao` | `404`: as rotas não existem (R18) | `MeusAgendamentosIT` |
| A17 | 3 consultas de "meus agendamentos" com 2 e com 20 reservas | contar SQL | o número de consultas não cresce (sem N+1) | `MeusAgendamentosIT` |

### 4A · expiração

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A18 | 3 reservas pendentes, relógio adiantado 16 min | job de expiração | as 3 viram `EXPIRADO`; o horário volta na disponibilidade | `ExpiracaoJobIT` |
| A19 | duas instâncias do job ao mesmo tempo | rodar | o ShedLock deixa só uma processar | `ExpiracaoJobIT` |
| A20 | reserva expirada pelo job | olhar `event_publication` | um `ReservaExpirada` gravado e concluído | `ExpiracaoJobIT` |
| A21 | API subindo com pendentes já vencidas | subir | o job roda uma vez na subida | `ExpiracaoJobIT` |

### 4B · cobrança

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A22 | reserva pendente, aceite, `metodo: PIX`, CPF válido | criar cobrança | `201`, `valorCentavos` = preço, `pix.copiaECola`, `pix.qrCodeBase64`, `cpfMascarado`; no banco **nenhum CPF** | `CobrancaIT` |
| A23 | o mesmo pedido, com `metodo: CARTAO_CREDITO` em outra reserva | criar | `201` com `cartao.urlCheckout` e `pix: null`; nada de dado de cartão no corpo | `CobrancaIT` |
| A24 | sem `aceitePolitica` · `false` · `versaoPolitica` antiga | criar | `422 ACEITE_POLITICA_OBRIGATORIO`; nenhuma cobrança | `CobrancaIT` |
| A25 | CPF com dígito errado · sem `X-Idempotency-Key` | criar | `400 CPF_INVALIDO` · `400 IDEMPOTENCY_KEY_AUSENTE` | `CobrancaIT` |
| A26 | mesma chave e mesmo corpo | repetir | `200`, a mesma cobrança, **sem** nova chamada ao gateway, sem `cpfMascarado` | `CobrancaIdempotenciaIT` |
| A27 | mesma chave e corpo diferente | repetir | `422 IDEMPOTENCIA_CONFLITO` | `CobrancaIdempotenciaIT` |
| A28 | cobrança pendente com outra chave | criar de novo | `409 COBRANCA_EM_ANDAMENTO` | `CobrancaIdempotenciaIT` |
| A29 | reserva expirada · de outro dono · token de visitante errado | criar | `422 RESERVA_EXPIRADA` · `404` · `404` | `CobrancaIT` |
| A30 | gateway com timeout | criar | `503 GATEWAY_INDISPONIVEL`; a cobrança fica `PENDENTE` sem id do provedor; repetir com a mesma chave procura pelo `externalReference` antes de criar outra | `CobrancaGatewayForaIT` |
| A31 | o aceite | depois da cobrança | a reserva tem `aceite_politica_versao` e `aceite_politica_em` | `CobrancaIT` |
| A32 | adaptador do Asaas | criar e consultar | as requisições têm o formato documentado (`customer`, `billingType`, `value`, `externalReference`) e o cabeçalho de autenticação; respostas viram o modelo interno | `AsaasGatewayTest` (`MockRestServiceServer`) |

### 4B · webhook e confirmação

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A33 | cobrança Pix pendente | webhook `PAYMENT_RECEIVED` com o token certo | `200`; reconsulta o gateway; cobrança `PAGA`; reserva `CONFIRMADO` | `WebhookIT` |
| A34 | o mesmo evento de novo | webhook | `200`, nada muda, nenhuma nova reconsulta | `WebhookIT` |
| A35 | token errado ou ausente | webhook | `401 WEBHOOK_NAO_AUTENTICADO`; nada gravado | `WebhookIT` |
| A36 | corpo dizendo "pago", mas o gateway diz "pendente" | webhook | cobrança continua `PENDENTE` (o corpo não manda) | `WebhookIT` |
| A37 | falha ao processar depois de gravar o evento | webhook | `5xx`; o reenvio do mesmo evento processa | `WebhookIT` |
| A38 | reserva expirada, horário ainda livre | pagamento confirmado | reserva `EXPIRADO → CONFIRMADO` (R14) | `PagamentoTardioIT` |
| A39 | reserva expirada e o horário já é de outro cliente | pagamento confirmado | reserva continua `EXPIRADO`; cobrança `PAGA_APOS_EXPIRACAO` | `PagamentoTardioIT` |
| A40 | reserva expira com cobrança pendente | job de expiração | cobrança `CANCELADA` | `PagamentoTardioIT` |
| A41 | webhook atrasado, cobrança paga no gateway | `GET /agendamentos/guest/{id}` duas vezes em 5 s | a primeira reconsulta e confirma; a segunda não reconsulta (limite de 10 s) | `ConciliacaoIT` |
| A42 | evento gravado sem processar há 11 min | job de reprocessamento | processa; com 5 falhas seguidas vira `REVISAO_MANUAL` | `ReprocessamentoWebhookIT` |

### Transversais

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A43 | fluxo inteiro (reservar, cobrar com CPF, webhook) | ler o log | nenhum CPF, e-mail, WhatsApp ou token no log (R30) | `SemDadoPessoalNoLogPagamentoIT` |
| A44 | módulos | `./mvnw verify` | `agenda` e `pagamento` só se falam pela API pública e por eventos; `GatewayDePagamento` e o núcleo de estados sem framework | `ModulithTest`, `ArquiteturaTest` |
| A45 | perfil `dev` sem conta no Asaas | reservar, cobrar e simular o pagamento pela rota de desenvolvimento | reserva `CONFIRMADO`; a rota de simulação **não existe** fora do perfil `dev` (`404`) | `SimulacaoDevIT` |

## 5. Entregas

| Módulo | 4A | 4B |
|---|---|---|
| `agenda` | `Reserva` (+ `StatusReserva`), `ReservaGuest`, `ReservarHorario` (caso de uso com E1 a E4), `AgendamentosController`, `GuestController`, `ExpiracaoJob`, API pública `Reservas` (dados para cobrança, registrar aceite, confirmar), evento `ReservaExpirada`, listener de `PagamentoConfirmado` | — |
| `pagamento` | — | `Cobranca`, `EventoWebhook`, porta `GatewayDePagamento` + `AsaasGateway` + `GatewayFalso` (dev), `CriarCobranca`, `WebhookController`, `ProcessarPagamento` (webhook, conciliação e job), `ReprocessamentoJob`, evento `PagamentoConfirmado`, listener de `ReservaExpirada` |
| `comum` | — | `Cpf` (valida e mascara) |
| banco | `V3__eventos.sql` (`event_publication` oficial do Modulith 2.1.2) | — |
| dependências | `spring-modulith-starter-jdbc`, `shedlock-spring` + `shedlock-provider-jdbc-template` 7.10.1 | — |
| docs | contrato 1.3.0 (`COBRANCA_EM_ANDAMENTO`) · guia de testes: visitante V4 a V9 e cliente C8 a C12 com saída real · roteiro privado com o sandbox · README e "como foi construído" |

## 6. Riscos e cuidados

- **Formato real do Asaas:** os campos da seção E15 seguem a documentação lida em 13/11/2026; no começo da 4B, o adaptador é conferido contra o sandbox de verdade antes de fechar os testes do `MockRestServiceServer`.
- **Conta no sandbox:** precisa ser criada por você (é cadastro de conta). Sem ela, tudo funciona com o adaptador falso, e só o teste manual contra o Asaas fica pendente.
- **Outbox:** o schema de `event_publication` muda entre versões do Modulith; a V3 copia o da 2.1.2 e o `ContextoSobeIT` com `ddl-auto: validate` quebra se não bater.
- **Webhook em `localhost`:** o Asaas não alcança a sua máquina. Para o teste manual: túnel temporário (ex.: o recurso de webhook do Postman ou `cloudflared`), documentado no roteiro privado.
- **Deadlock entre job e reserva:** coberto por E4 e pelo `SKIP LOCKED` do job.

## 7. Dúvidas para o Kevin

| # | Dúvida | Minha proposta |
|---|---|---|
| D-1 | Dividir a fatia em duas PRs (4A reserva e expiração; 4B pagamento)? | sim: cada uma fecha um pedaço testável, e a 4A já entrega valor sozinha (reservar e expirar) |
| D-2 | Conta no sandbox do Asaas | você cria a conta e anota o token em `.equipe/credenciais-sandbox.md`; até lá, o adaptador falso cobre os testes |
| D-3 | Rota de simulação de pagamento no perfil `dev` (E28) | sim, só no perfil `dev`, para testar ponta a ponta sem conta e sem túnel |

## 8. Pronto quando

- [ ] A1 a A21 verdes na PR 4A e A22 a A45 verdes na PR 4B (`sh scripts/conferir-spec.sh 4 --exigir` na 4B)
- [ ] `redocly lint` com 0 erros no contrato 1.3.0
- [ ] fluxo rodado à mão no perfil `dev` (com o adaptador falso) e, com a conta criada, contra o sandbox do Asaas
- [ ] [checklist da PR](checklist-pr.md) completo
