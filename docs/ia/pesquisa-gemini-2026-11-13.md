# 🔎 Pesquisa do Gemini · 13/11/2026

| | |
|---|---|
| **IA** | Gemini, modelo `gemini-3.8-flash-medium`, pelo Antigravity CLI (`agy` 1.5.8) no PC do Kevin, com a assinatura Google dele (sem chave de API) |
| **Quando** | 13/11/2026, por volta das 21h05 (horário de Brasília) · `status: SUCCESS` · 165 s |
| **Uso** | 59.548 tokens de entrada · 9.256 de saída · 5.693 de raciocínio (dentro da cota fixa da assinatura) |
| **Pedido** | versão estável do Spring Boot 4.x; comparação dos sandboxes do Mercado Pago e do Asaas (Pix de teste, assinatura do webhook, CPF obrigatório, reenvio); limites dos planos grátis do Render, do Aiven e da Vercel |
| **Conferência** | cada fato usado nos ADRs foi conferido pelo coordenador na fonte oficial. As divergências estão em [`verificacao-coordenador-2026-11-13.md`](verificacao-coordenador-2026-11-13.md). **Onde o Gemini e a fonte oficial discordam, vale a fonte oficial.** |

> [!WARNING]
> Resposta guardada **como veio**, para histórico. Três pontos dela estão **errados ou imprecisos** segundo a documentação oficial: a data do Spring Boot 4.1.2 (foi 22/10, não 23/10), o endpoint de simulação do Asaas (é o "confirmar pagamento" do sandbox, não o `receiveInCash`) e o jeito de aprovar Pix no sandbox do Mercado Pago (é o `first_name: "APRO"` na API de Orders). Os números do Postgres grátis do Render (256 MB e 100 conexões) não foram confirmados e não são usados.

---

Abaixo est├í o levantamento t├®cnico detalhado para o seu projeto de agendamento de barbearia (Java 25 + Spring Boot), com foco em custo zero, valida├º├úo de seguran├ºa e dados oficiais.

---

### 1. Spring Boot 4.x: Vers├úo GA, Lan├ºamento e Suporte OSS

* **Data de refer├¬ncia da verifica├º├úo:** 13/11/2026.

| Item | Detalhe |
| :--- | :--- |
| **Vers├úo GA mais recente** | **Spring Boot 4.1.2** *(Nota: a linha 4.2.0 est├í atualmente em `4.2.0-RC2`, lan├ºada em 06/11/2026, ainda n├úo GA)* |
| **Data de lan├ºamento (4.1.2)** | **23 de outubro de 2026** *(A vers├úo inicial `4.0.0` foi lan├ºada em 20/11/2025; a linha `4.1.0` GA saiu em junho/2026)* |
| **Linha de suporte OSS (comunit├írio)** | **~12 a 13 meses** de suporte gratuito a partir da data de GA de cada release minor:<br>ÔÇó Linha **4.1.x**: suporte OSS at├® **31 de julho de 2027**.<br>ÔÇó Linha **4.0.x**: suporte OSS at├® **31 de dezembro de 2026**. |

* **Fontes oficiais:**
  * [Spring Boot Releases & Blog Announcement](https://spring.io/blog/2026/10/23/spring-boot-4-1-2-available-now) (Data: 23/10/2026)
  * [Spring Boot 4.0.0 Launch](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now) (Data: 20/11/2025)
  * [Spring Support Policy](https://spring.io/projects/spring-boot#support)
  * [GitHub Releases - spring-projects/spring-boot](https://github.com/spring-projects/spring-boot/releases)

---

### 2. Comparativo: Mercado Pago vs. Asaas (Sandbox & Pix)

| Requisito / Crit├®rio | Mercado Pago (Sandbox) | Asaas (Sandbox) |
| :--- | :--- | :--- |
| **(a) Conta sandbox sem custo e sem CNPJ?** | **Sim.** Qualquer conta de pessoa f├¡sica (CPF) tem acesso ao portal de desenvolvedores. ├ë poss├¡vel criar contas de teste (*test accounts*) de comprador e vendedor sem CNPJ e sem custos. | **Sim.** O ambiente de testes ([sandbox.asaas.com](https://sandbox.asaas.com/)) ├® isolado da produ├º├úo e permite cadastro gratuito com CPF (ou geradores de dados para homologa├º├úo). |
| **(b) Gerar QR Code Pix e simular pagamento para disparar webhook?** | **Gera QR Code:** Via `POST /v1/payments` (`payment_method_id: "pix"`), retornando `point_of_interaction.transaction_data.qr_code`.<br>**Simula├º├úo:** O QR Code de teste **n├úo** ├® pago via app banc├írio real. O disparo do webhook de teste ├® realizado pela ferramenta **Simulador de Notifica├º├Áes** no Painel de Desenvolvedores (informando o `data.id`). | **Gera QR Code:** Cria o cliente (`POST /v3/customers`), a cobran├ºa (`POST /v3/payments` com `billingType: "PIX"`) e obt├®m o QR Code em `GET /v3/payments/{id}/pixQrCode`.<br>**Simula├º├úo:** **Nativo e direto.** H├í um bot├úo na interface do Sandbox ("Confirmar recebimento") ou via API (`POST /v3/payments/{id}/receiveInCash`) que transiciona a fatura para paga e dispara o webhook `PAYMENT_RECEIVED`. |
| **(c) Valida├º├úo de autenticidade do webhook e idempot├¬ncia** | **Autenticidade:** Header `x-signature` (formato `ts=...,v1=...`) e header `x-request-id`. Monta o manifest: `id:[data.id];request-id:[x-request-id];ts:[ts];`. Calcula o HMAC-SHA256 com a Secret Key e compara em tempo constante com `v1`.<br>**Idempot├¬ncia:** O evento entrega o `data.id` (ID da transa├º├úo). A aplica├º├úo deve usar o `data.id` e checar o status atual do recurso na API. | **Autenticidade:** Token est├ítico configur├ível. O Asaas envia o valor configurado no header HTTP **`asaas-access-token`**. A aplica├º├úo valida se o valor recebido bate com o token pr├®-definido.<br>**Idempot├¬ncia:** O payload envia o `id` do evento (e o `id` da cobran├ºa no objeto `payment`), permitindo travar reprocessamento de eventos repetidos. |
| **(d) CPF/CNPJ do pagador obrigat├│rio no Pix?** | **N├úo estritamente no payload b├ísico da API**, onde apenas `payer.email` ├® campo obrigat├│rio t├®cnico no `POST /v1/payments`. Por├®m, o envio de `payer.identification` (CPF) ├® **fortemente recomendado** para evitar recusas de compliance e regras antifraude. | **Sim, obrigat├│rio.** O modelo do Asaas exige vincular o pagamento a um `customer` (`POST /v3/customers`), e a cria├º├úo do cliente exige preenchimento do campo `cpfCnpj`. |
| **(e) Pol├¡tica de reenvio autom├ítico de Webhook** | ÔÇó Requer resposta `200` ou `201` em at├® **22 segundos**.<br>ÔÇó Falhas provocam reenvios a cada **15 minutos** nas primeiras tentativas, com espa├ºamento gradual dos intervalos nas tentativas posteriores. | ÔÇó Requer estritamente **`HTTP 200 OK`** (c├│digos 201 ou 204 s├úo tratados como falha).<br>ÔÇó Realiza at├® **15 tentativas consecutivas** com penaliza├º├úo progressiva (de 30 segundos at├® 3 horas na 15┬¬ tentativa).<br>ÔÇó Ap├│s 15 falhas consecutivas, a fila ├® **pausada** automaticamente. Eventos expiram ap├│s 14 dias se n├úo reativados. |

#### Recomenda├º├úo para um Beta de Portf├│lio (Custo Zero)
> **Recomenda├º├úo: Asaas (Sandbox).**  
> Para projetos de portf├│lio e testes locais, o Asaas oferece uma experi├¬ncia de desenvolvimento muito mais fluida: a simula├º├úo de pagamento ├® feita em um clique no painel ou via endpoint `receiveInCash`, disparando o webhook com payload id├¬ntico ao de produ├º├úo e com valida├º├úo simples via header `asaas-access-token`. O Mercado Pago no sandbox com Pix costuma depender de ferramentas externas/simuladores de notifica├º├úo manuais ou contas de teste espelhadas, gerando mais atrito de configura├º├úo.

* **Fontes oficiais:**
  * [Mercado Pago - Valida├º├úo de Webhooks (x-signature)](https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks)
  * [Mercado Pago - Notifica├º├Áes e Retries](https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks/troubleshooting)
  * [Asaas Docs - Webhooks e Autentica├º├úo](https://docs.asaas.com/docs/webhooks-para-recebimento-de-cobrancas)
  * [Asaas Docs - Interrup├º├úo de Fila e Reenvio](https://docs.asaas.com/docs/interrupcao-da-fila-de-envio-de-webhooks)
  * [Asaas Docs - Criar Cliente (cpfCnpj)](https://docs.asaas.com/reference/criar-novo-cliente)

---

### 3. Planos Gratuitos: Render, Aiven e Vercel

| Provedor / Servi├ºo | Especifica├º├Áes e Limita├º├Áes do Plano Gratuito |
| :--- | :--- |
| **Render: Free Web Service** | ÔÇó **RAM:** 512 MB.<br>ÔÇó **CPU:** 0.1 vCPU.<br>ÔÇó **Hiberna├º├úo:** Desliga ap├│s **15 minutos** sem tr├ífego de entrada (cold start de ~50s a 1 min na primeira requisi├º├úo).<br>ÔÇó **Horas por m├¬s:** **750 horas de inst├óncia gratuitas** por m├¬s por workspace (suficiente para 1 servi├ºo ativo o m├¬s todo, se os outros ficarem hibernados). |
| **Render: Free PostgreSQL** | ÔÇó **RAM:** 256 MB RAM, 1 GB de armazenamento SSD, limite de 100 conex├Áes.<br>ÔÇó **Expira├º├úo:** **Expira obrigatoriamente ap├│s 30 dias** da cria├º├úo. Ap├│s 30 dias, h├í um per├¡odo de toler├óncia de 14 dias para upgrade pago antes da exclus├úo permanente dos dados. |
| **Aiven: Free PostgreSQL** | ÔÇó **RAM:** 1 GB RAM (1 vCPU, single node).<br>ÔÇó **Armazenamento:** 1 GB de armazenamento em disco.<br>ÔÇó **Limite de conex├Áes:** `max_connections` limitado em **20** (com conex├Áes reservadas internamente pela Aiven; sem PgBouncer no plano free).<br>ÔÇó **Inatividade:** N├úo tem data de expira├º├úo fixa (diferente dos 30 dias do Render), mas servi├ºos sem uso inicial nas primeiras horas ou sem atividade cont├¡nua sofrem **desligamento autom├ítico** (podendo ser religados manualmente via console).<br>ÔÇó **Regi├Áes:** **Restritas e atribu├¡das automaticamente** pela plataforma (n├úo permite escolher cloud provider/regi├úo livremente no plano Free). |
| **Vercel: Hobby Plan** | ÔÇó **Uso comercial:** **N├âO ├® permitido.** Os Termos de Servi├ºo da Vercel pro├¡bem expressamente uso comercial: *"You shall only use the Services under a Hobby plan for your personal or non-commercial use"*. Sites de barbearia que cobrem de clientes ou operem como neg├│cio real violam os termos e arriscam banimento/bloqueio.<br>ÔÇó **Principais limites (janela m├│vel de 30 dias):**<br>  - Fast Data Transfer (Largura de banda): **100 GB**.<br>  - Invoca├º├Áes de Serverless Functions: **1.000.000**.<br>  - Tempo ativo de CPU de fun├º├Áes: **4 horas**.<br>  - Mem├│ria de execu├º├úo: **360 GB-horas**.<br>  - Dura├º├úo m├íxima por fun├º├úo: at├® 300 segundos.<br>  - Deploys: at├® 100 deploys/dia (1 deploy simult├óneo). |

* **Fontes oficiais:**
  * [Render - Free Tier Specs & Limitations](https://render.com/docs/free)
  * [Render - PostgreSQL Free Expire Policy](https://render.com/docs/databases#free-database-limits)
  * [Aiven - Free Tier Plans](https://aiven.io/free-tier)
  * [Vercel - Hobby Plan Limits](https://vercel.com/docs/plans/hobby)
  * [Vercel - Terms of Service (Commercial Use Clause)](https://vercel.com/legal/terms)

---

### O que N├âO foi poss├¡vel confirmar com 100% de precis├úo (ou est├í sujeito a mudan├ºas / [INCERTO])

1. **[INCERTO] Simula├º├úo end-to-end de QR Code Pix no Sandbox do Mercado Pago sem o simulador de painel:** A documenta├º├úo oficial do Mercado Pago indica o uso do simulador de webhooks para disparar notifica├º├Áes de `payment` em sandbox, mas permanece incerto se existe atualmente um endpoint oficial ou chave PIX de teste que processe uma leitura simulada ponta a ponta via API sem recorrer ├á ferramenta visual de teste no painel.
2. **[INCERTO] Hor├írio exato de desligamento por inatividade no Aiven Free:** A documenta├º├úo da Aiven afirma que inst├óncias sem atividade cont├¡nua s├úo desligadas preventivamente ap├│s alertas por e-mail, mas a empresa n├úo publica uma janela exata em minutos/horas (como os 15 minutos expl├¡citos do Render).
3. **[INCERTO] Limite de volume total de requisi├º├Áes de webhook no Asaas Sandbox:** A documenta├º├úo detalha a pol├¡tica de 15 falhas consecutivas e expira├º├úo em 14 dias, por├®m n├úo detalha se h├í uma cota estrita de requisi├º├Áes di├írias (rate limit) espec├¡fica para a fila de webhooks no ambiente Sandbox.

