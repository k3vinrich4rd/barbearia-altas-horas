# ⚔️ Ataque à arquitetura pelo Gemini · 13/11/2026

| | |
|---|---|
| **IA** | Gemini `gemini-3.8-flash-medium` via `agy` no PC do Kevin (assinatura, sem chave) · `status: SUCCESS` · 38 s · 12,5k tokens de entrada / 3,9k de saída |
| **Pedido** | "que requisito ou cenário quebra esta arquitetura?" (skill arquitetura sob medida, seção 3.4), com a proposta do Alicerce e o resumo do DDL |
| **Uso** | crítica registrada para a revisão do Alicerce; as respostas do coordenador estão na tabela abaixo |

## Resposta do coordenador a cada ataque

| # | Ataque | Procede? | O que mudou nos documentos |
|---|---|---|---|
| 1 | Webhook chega com a API hibernando (cold start ~1 min) e falha | **em parte**: a primeira tentativa acorda o Render e os gateways reenviam (Asaas e Mercado Pago, [fonte](verificacao-coordenador-2026-11-13.md)); mas o atraso existe | **conciliação ativa**: `GET /agendamentos/guest/{id}` reconsulta a cobrança no gateway quando ela está `PENDENTE` (com limite), então a tela do guest confirma mesmo sem o webhook. Ping externo para não hibernar fica **documentado como opção, não adotado** (gasta as 750 h e foge do espírito do plano grátis) |
| 2 | OOM com 512 MB | **procede** como risco | RNF3 já exige rodar o roteiro e2e com `--memory=512m` no CI; flags da JVM (`MaxRAMPercentage`, `SerialGC`, `MaxMetaspaceSize`) entram na fatia do Dockerfile com medição real |
| 3 | Chamada ao gateway segurando conexão do pool de 5 | **procede** | reforçado no ADR 0007: chamada HTTP sempre fora de `@Transactional`, inclusive no listener; a transação só abre para o `UPDATE` final |
| 4 | Deadlock na expiração preguiçosa | **em parte**: o `UPDATE` toca só reservas vencidas do mesmo barbeiro e intervalo, mas duas transações podem travar linhas em ordem diferente | ADR 0003: travar as vencidas em ordem (`SELECT ... ORDER BY id FOR UPDATE`) antes do `UPDATE` e repetir uma vez em `40P01`. A sugestão de tirar o `UPDATE` foi **recusada**: sem ele, a tela mostra o horário livre e a constraint recusa (409 confuso) até o job rodar |
| 5 | Pagamento no último segundo + job expira antes do evento | **procede**: é a HIPÓTESE H8 | registrado como alternativa para o Kevin decidir: tolerância de alguns minutos antes de expirar e prazo do Pix no gateway igual a `expira_em`. Estorno automático é pós-beta (prompt aprovado) |
| 6 | Não guardar CPF impede nota fiscal e prova em disputa | **divergência** | mantida a decisão do ADR 0009 (o beta não emite nota fiscal; o gateway guarda o CPF da cobrança), com a crítica registrada lá para o Alicerce e o Kevin. Se houver obrigação fiscal real, um ADR novo adota CPF cifrado |
| 7 | Token estático do webhook; flood de reconsultas | **procede** | ADR 0007: limite de taxa na rota do webhook e reconsulta só para eventos de tipos usados e cobranças existentes |
| 8 | Job com ShedLock não roda com a API hibernando | **procede**, sem quebrar regra | a disponibilidade ignora vencidas (R15) e a expiração preguiçosa cobre a inserção (R16); o job roda uma vez logo na subida (`initialDelay` curto). Cron externo fica como opção |

---

## Resposta original do Gemini (como veio)

Aqui est├í a an├ílise cr├¡tica da arquitetura, ordenada da maior para a menor gravidade:

### 1. Cold Start no Render vs. Timeout do Webhook do Asaas
* **Cen├írio:** O app hiberna ap├│s 15 min sem uso. O cliente paga o Pix e o Asaas dispara o webhook HTTP.
* **Por que quebra:** O cold start do Spring Boot na JVM com 512 MB leva de 40s a 90s. O Asaas possui timeout r├¡gido (5s a 15s) e desiste ap├│s poucas retentativas r├ípidas com erro 504/Connection Refused. O webhook ├® desativado ou descartado pelo gateway; o cliente paga e o agendamento nunca confirma.
* **Mitiga├º├úo:** Configurar cron externo (ex.: cron-job.org) pingando `/actuator/health` a cada 10 min para evitar hiberna├º├úo, ou polling no front-end para reconsultar status caso o webhook falhe.

### 2. OOMKill Fatal por Limite de 512 MB de RAM
* **Cen├írio:** Inicializa├º├úo da aplica├º├úo ou concorr├¬ncia simult├ónea de requisi├º├Áes, GC e serializa├º├úo Jackson.
* **Por que quebra:** JVM moderna com Metaspace, Spring Modulith, Hibernate e Virtual Threads ultrapassa facilmente 512 MB de mem├│ria nativa total (RSS). O Render aplica `kill -9` silencioso (exit code 137), gerando reinicializa├º├Áes em loop sem deixar rastro no log da aplica├º├úo.
* **Mitiga├º├úo:** Parametrizar a JVM com limites estritos (`-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m`) ou compilar nativamente com GraalVM.

### 3. Exaust├úo de Conex├Áes (Pool 5) por I/O Bloqueante
* **Cen├írio:** Reconsulta da cobran├ºa na API do Asaas executada dentro do fluxo ass├¡ncrono do listener do Modulith (`@ApplicationModuleListener`).
* **Por que quebra:** Se a chamada externa ao Asaas demorar por oscila├º├úo de rede mantendo a transa├º├úo/conex├úo JDBC aberta, as 5 conex├Áes do pool esgotam em segundos. O mon├│lito inteiro trava, negando novas reservas e derrubando health checks.
* **Mitiga├º├úo:** Realizar a chamada HTTP ao gateway estritamente **fora** de qualquer contexto transacional; usar timeout de conex├úo curto (3s) e reservar conex├úo JDBC apenas no UPDATE final.

### 4. Deadlock na Expira├º├úo Pregui├ºosa no INSERT
* **Cen├írio:** Dois clientes tentam reservar hor├írios adjacentes enquanto h├í slots vencidos sobrepostos.
* **Por que quebra:** O `UPDATE` de expira├º├úo na mesma transa├º├úo locka linhas com `AGUARDANDO_PAGAMENTO`. Leituras e grava├º├Áes concorrentes no ├¡ndice GiST com predicados sobrepostos geram `deadlock detected (40P01)` ou bloqueio m├║tuo no PostgreSQL.
* **Mitiga├º├úo:** Remover o `UPDATE` da transa├º├úo do `INSERT`. Considerar reservas vencidas liberadas logicamente apenas no c├ílculo de disponibilidade (`NOW() > expira_em`) e delegar a escrita ao job em lote.

### 5. Inconsist├¬ncia de Estado: Corrida entre Webhook e Expira├º├úo
* **Cen├írio:** O cliente paga no ├║ltimo minuto do Pix. O webhook chega durante a hiberna├º├úo ou fila de execu├º├úo do Modulith.
* **Por que quebra:** O job expira a reserva antes do processamento ass├¡ncrono do evento `PagamentoConfirmado`. O slot ├® imediatamente liberado, tomado por outro cliente, e a proposta delega estorno a "devolu├º├úo manual". Se o outro cliente pagar, cria-se atrito comercial e risco de chargeback.
* **Mitiga├º├úo:** Adicionar *grace period* de 5 minutos antes de expirar a reserva e implementar estorno autom├ítico via API Pix do Asaas caso o webhook chegue com status expirado.

### 6. N├úo Persist├¬ncia de CPF vs. LGPD e Obriga├º├úo Fiscal
* **Cen├írio:** Contesta├º├úo de pagamento, fiscaliza├º├úo tribut├íria municipal (NFS-e de servi├ºos) ou pedido de exclus├úo pelo titular (`DELETE /me`).
* **Por que quebra:** O Art. 16, I da LGPD e o C├│digo Tribut├írio exigem reten├º├úo de dados cadastrais/fiscais por 5 anos. Repassar o CPF apenas ao gateway impede a barbearia de emitir nota fiscal legal e comprovar titularidade em disputas judiciais.
* **Mitiga├º├úo:** Armazenar o CPF criptografado (AES-256) com hash determin├¡stico (HMAC-SHA256) para busca, sob a base legal de cumprimento de obriga├º├úo legal/fiscal (Art. 7┬║, II, LGPD), segregado dos dados de marketing.

### 7. Inseguran├ºa do Webhook por Token Est├ítico
* **Cen├írio:** Vazamento do token `asaas-access-token` ou ataque de repeti├º├úo/falsifica├º├úo.
* **Por que quebra:** Header est├ítico n├úo garante integridade do payload nem autenticidade da origem. Um atacante pode forjar requisi├º├Áes em massa, provocando reconsultas na API do Asaas at├® estourar rate limit e travar o pool.
* **Mitiga├º├úo:** Validar range de IPs de sa├¡da do Asaas e implementar rate limit severo no endpoint do webhook.

### 8. Starvation do ShedLock na Hiberna├º├úo
* **Cen├írio:** Sem tr├ífego, o Render hiberna durante horas da madrugada ou intervalos de atendimento.
* **Por que quebra:** O `@Scheduled` com ShedLock n├úo roda em background com a JVM parada; ele n├úo recupera disparos passados ao acordar. Reservas abandonadas continuam ocupando o status at├® que um novo cliente force um wake-up.
* **Mitiga├º├úo:** Acionar a rotina de limpeza por webhook disparado por cron externo (ex.: Vercel Cron).

