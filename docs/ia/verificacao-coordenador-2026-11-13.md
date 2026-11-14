# ✅ Verificação do coordenador nas fontes oficiais · 13/11/2026

O Gemini fez a pesquisa ([`pesquisa-gemini-2026-11-13.md`](pesquisa-gemini-2026-11-13.md)). O coordenador conferiu cada fato usado nos ADRs direto na fonte oficial, no mesmo dia. **Esta é a tabela que vale.** Consulta feita em 13/11/2026, entre 21h e 21h20 (horário de Brasília).

## 1. Spring Boot

| Fato | Valor confirmado | Fonte |
|---|---|---|
| Versão estável (GA) mais recente da linha 4.x | **4.1.2**, publicada em 22/10/2026 17:04 UTC (14:04 em Brasília) | API de releases do GitHub: `https://api.github.com/repos/spring-projects/spring-boot/releases` |
| Outras linhas | 4.0.9 (GA, mesma data); 4.2.0 ainda em **RC2** (05/11/2026), **não** é GA | idem |
| Divergência com o Gemini | o Gemini disse 23/10/2026 e RC2 em 06/11/2026. Os carimbos do GitHub, em UTC, são 22/10 e 05/11 | idem |
| Fim do suporte OSS da 4.1.x | o Gemini disse 31/07/2027. **Não conferido** pelo coordenador | `https://spring.io/projects/spring-boot#support` (conferir na fatia do esqueleto) |

**Decisão:** fixar `4.1.2` no `pom.xml` (ADR 0010). Se a 4.2.0 sair GA antes da fatia do esqueleto, a troca vira uma linha nova em `decisoes.md`.

## 2. Gateways de pagamento (sandbox)

| Critério | Mercado Pago | Asaas | Fontes |
|---|---|---|---|
| Conta de teste sem custo | sim (contas de teste no painel de desenvolvedor) | sim, conta Sandbox separada da produção, aprovada automaticamente com dados válidos | MP: `https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/integration-test/pix` · Asaas: `https://docs.asaas.com/docs/sandbox`, `https://docs.asaas.com/docs/faq-sandbox` |
| Simular o Pix pago | sem leitura real do QR Code; a order de teste é aprovada quando `payer.first_name = "APRO"` (API de Orders, `POST /v1/orders`) | endpoint **"(Apenas sandbox) Confirmar o pagamento"** ou botão no painel; o Asaas avisa para **não** usar `receiveInCash` para simular Pix | MP: link acima · Asaas: `https://docs.asaas.com/reference/confirmar-pagamento` |
| Autenticidade do webhook | header `x-signature` (`ts=…,v1=…`), HMAC-SHA256 do manifesto `id:<data.id>;request-id:<x-request-id>;ts:<ts>;` com o segredo do painel | token estático configurado no webhook, enviado no header `asaas-access-token`; não usar a API Key como token | MP: `https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks` · Asaas: `https://docs.asaas.com/docs/sobre-os-webhooks` |
| Id para idempotência | `data.id` é o id do **recurso** (pagamento), não do evento; a deduplicação usa `data.id` + tipo e consulta o recurso na API | campo `id` do **evento** (ex.: `evt_…`), indicado pela doc para evitar processamento duplicado; entrega "at least once" | idem |
| CPF do pagador | na API de Orders do sandbox, `payer.email` basta; na API de Payments com Pix o exemplo oficial envia `identification` (CPF) | a cobrança é ligada a um cliente (`customer`) criado com `cpfCnpj`. **Obrigatoriedade: a doc lida lista o campo, a busca e o Gemini dizem obrigatório; confirmar no primeiro teste da fatia de cobrança** | MP: `https://www.mercadopago.com.br/developers/en/docs/checkout-bricks/payment-brick/payment-submission/pix` · Asaas: `https://docs.asaas.com/reference/criar-novo-cliente` |
| Reenvio | espera 200/201 em até 22 s; reenvia a cada 15 min, depois espaça | reenvia; após 15 falhas seguidas a fila pode ser pausada; eventos guardados por 14 dias | MP e Asaas: links de webhook acima |

Divergências com o Gemini: o Gemini sugeriu `receiveInCash` para simular Pix no Asaas (a doc oficial diz o contrário) e o "simulador de notificações" do Mercado Pago (a doc atual de Pix usa o `APRO`).

## 3. Planos grátis

| Provedor | Limite confirmado | Fonte |
|---|---|---|
| Render (web service free) | 0,1 CPU e 512 MB; hiberna após **15 min** sem tráfego e leva **cerca de 1 min** para voltar; **750 horas** de instância grátis por mês por workspace; sistema de arquivos efêmero; sem SSH | `https://render.com/docs/free` |
| Render Postgres (free) | **expira 30 dias** após a criação (mais 14 dias de carência para pagar, depois apaga); 1 GB; sem backup; um por workspace | idem |
| Aiven for PostgreSQL (free) | 1 CPU, 1 GB de RAM, **1 GB de disco**, um nó, **`max_connections` = 20**, sem pool de conexões, sem escolha de região; pode ser desligado sem uso inicial ou por inatividade (com aviso), religável | `https://aiven.io/docs/products/postgresql/concepts/pg-free-tier` · `https://aiven.io/docs/products/postgresql/reference/pg-connection-limits` |
| Aiven: conexões reservadas | a Aiven reserva **3 a 6** conexões para gestão e monitoramento | `https://aiven.io/docs/products/postgresql/reference/pg-connection-limits` |
| Vercel Hobby | **só uso pessoal e não comercial**. A diretriz cita como comercial "qualquer forma de pedir ou processar pagamento dos visitantes do site" e "anunciar a venda de um produto ou serviço" | `https://vercel.com/docs/limits/fair-use-guidelines` (seção Commercial usage) · Termos, seção 4: `https://vercel.com/legal/terms` |
| Vercel Hobby: limites | 100 GB de Fast Data Transfer, 1 milhão de invocações de função, 4 h de CPU ativa, 360 GB-h de memória, 100 deploys por dia, função de até 300 s | `https://vercel.com/docs/plans/hobby` |

**Consequências para o desenho:** pool da API em **5 conexões** (cabe com folga nas 20 − 6 reservadas, mesmo com o Flyway e uma ferramenta de consulta abertos); nenhum estado em disco no Render; o primeiro acesso depois de 15 min parado demora cerca de 1 min (o front mostra "servidor acordando"); a Vercel Hobby serve ao **portfólio**, mas uma barbearia cobrando sinal de clientes reais é uso comercial: isso vira decisão do Kevin antes de qualquer uso real (risco RISC-03 no dossiê).
