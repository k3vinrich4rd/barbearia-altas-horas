# 0007. Gateway de pagamento: Asaas sandbox

- Status: **proposta** (dono: Quarteto Dev; o Alicerce revisa só o impacto na arquitetura)
- Data: 2026-11-13
- Decisores: Quarteto Dev; consultados: Gemini (pesquisa) e documentação oficial (conferida pelo coordenador)
- Fontes: [`docs/ia/verificacao-coordenador-2026-11-13.md`](../ia/verificacao-coordenador-2026-11-13.md) (consulta em 13/11/2026)

## Contexto
O guest paga 50% de sinal por Pix. O beta é portfólio e **custo zero**: precisa de sandbox gratuito com Pix de teste, webhook que dê para disparar sem dinheiro real, e algo que justifique pedir o CPF só no pagamento.

## Opções consideradas
| Critério | Mercado Pago | Asaas |
|---|---|---|
| Conta de teste grátis | sim (contas de teste) | sim (conta Sandbox separada) |
| Simular Pix pago | API de Orders com `payer.first_name = "APRO"`; o QR Code não é pago de verdade | endpoint/botão **"(Apenas sandbox) Confirmar o pagamento"** da cobrança |
| Disparar o webhook no teste | sim, com o `APRO` | sim, ao confirmar a cobrança no sandbox |
| Autenticidade do webhook | **HMAC-SHA256** (`x-signature` + `x-request-id`) | **token estático** no header `asaas-access-token` |
| Id para idempotência | `data.id` é id do **recurso** (precisa deduplicar por `data.id` + tipo e reconsultar) | `id` do **evento** (`evt_…`), indicado pela doc para deduplicar |
| CPF do pagador | opcional na API de Orders do sandbox | cliente da cobrança criado com `cpfCnpj` (**a confirmar no primeiro teste**) |
| Reenvio | a cada 15 min, depois espaça | novas tentativas; fila pausa após 15 falhas; eventos por 14 dias |

## Decisão
**Asaas sandbox** no beta, atrás de uma interface do módulo `pagamento` (`GatewayPagamento` em `pagamento.internal.integracao`), para o Mercado Pago entrar como segundo adaptador sem mexer na regra.

Por quê:
1. O **id do evento** casa direto com a tabela `evento_webhook` (R12), sem truque de deduplicação.
2. O teste ponta a ponta é simples: criar cobrança → confirmar no sandbox → webhook real chega.
3. O modelo do Asaas (cliente com `cpfCnpj`) é o motivo de o CPF ser pedido **só** no pagamento (R9).

**Compensando o ponto fraco (token estático em vez de HMAC):**
- token longo e aleatório em `GATEWAY_SEGREDO_WEBHOOK` (nunca a API Key), comparado em **tempo constante**;
- só HTTPS (Render) e rota sem nenhum outro efeito além de gravar o evento;
- **reconsulta**: antes de marcar a cobrança como paga, a API busca a cobrança no gateway (`GET /v3/payments/{id}`) e confia no status de lá, não no corpo do webhook (OWASP API10);
- opcional: lista de IPs oficiais do Asaas, documentada e desligada por padrão (o Render não garante IP de origem estável nos cabeçalhos sem configuração).

**Contrato do webhook:** responde `200` sem corpo depois de gravar o evento (o processamento pesado vai para o evento assíncrono); `401` com token errado; evento repetido `200` sem efeito.

**Conciliação ativa (crítica 1 do Gemini):** se o webhook atrasar (ex.: API hibernando no Render), `GET /agendamentos/guest/{id}` reconsulta a cobrança `PENDENTE` no gateway (no máximo uma vez a cada 10 s por cobrança) e aplica o mesmo fluxo do webhook. O webhook continua sendo o caminho principal; a rota tem limite de taxa e só reconsulta eventos de tipos usados e cobranças que existem (crítica 7).

**Chamadas ao gateway:** fora de `@Transactional`, inclusive dentro dos listeners de eventos (a conexão do pool de 5 só é pega para o `UPDATE` final, crítica 3); timeout de conexão 2 s e leitura 5 s (proposta, RNF7); falha vira `503 GATEWAY_INDISPONIVEL`. A criação de cobrança usa a nossa `X-Idempotency-Key` (R10) para não criar duas cobranças no gateway se o front repetir.

## Consequências
- ✅ Fluxo de teste reproduzível por qualquer pessoa com conta sandbox, sem dinheiro.
- ⚠️ Autenticação do webhook mais fraca que HMAC: mitigada pela reconsulta (risco RISC-05).
- ⚠️ Produção real exige conta Asaas verificada, taxas e revisão da Vercel Hobby (H17). Fora do beta.

## Gatilhos para revisitar
- O Asaas passar a assinar webhooks com HMAC → usar.
- Exigência de CPF não se confirmar no sandbox → rever R9 com o Kevin.
- Uso real com clientes pagando.
