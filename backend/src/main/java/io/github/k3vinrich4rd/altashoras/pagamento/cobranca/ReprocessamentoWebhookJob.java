package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Eventos presos (E26): gravados e nunca processados (a API caiu, o gateway ficou fora). A cada 5 minutos e na subida,
 * com ShedLock, pega os que esperam há mais de 10 minutos (H19), em lote com {@code FOR UPDATE SKIP LOCKED}. Cada
 * tentativa é contada ao reivindicar o evento, numa transação curta: o gateway é chamado FORA de transação (E27).
 * Na 5ª falha seguida (H20) o evento vira REVISAO_MANUAL e o job para de tentar. O erro gravado é só o tipo da
 * exceção, nunca a mensagem (R30).
 */
@Component
class ReprocessamentoWebhookJob {

    private static final Logger LOG = LoggerFactory.getLogger(ReprocessamentoWebhookJob.class);
    static final int TAMANHO_DO_LOTE = 50;
    static final int MAXIMO_DE_TENTATIVAS = 5;
    static final Duration ESPERA_MINIMA = Duration.ofMinutes(10);

    private final JdbcClient jdbc;
    private final ReceberWebhook receber;
    private final TransactionTemplate transacao;
    private final Clock clock;

    ReprocessamentoWebhookJob(JdbcClient jdbc, ReceberWebhook receber, PlatformTransactionManager gerenciador, Clock clock) {
        this.jdbc = jdbc;
        this.receber = receber;
        this.transacao = new TransactionTemplate(gerenciador);
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.jobs.reprocessamento.atraso-inicial:0}",
            fixedDelayString = "${app.jobs.reprocessamento.intervalo:300000}")
    @SchedulerLock(name = "reprocessamento-webhook", lockAtMostFor = "PT5M")
    public void executar() {
        List<ReceberWebhook.Evento> lote = transacao.execute(status -> reivindicar());
        int processados = 0;
        for (ReceberWebhook.Evento evento : lote) {
            try {
                if (receber.processar(evento) == ProcessarPagamento.Resultado.SEM_COBRANCA) {
                    registrarFalha(evento.id(), "SEM_COBRANCA");
                } else {
                    processados++;
                }
            } catch (RuntimeException falha) {
                registrarFalha(evento.id(), falha.getClass().getSimpleName());
            }
        }
        if (!lote.isEmpty()) {
            LOG.info("Reprocessamento de webhooks: {} no lote, {} processados", lote.size(), processados);
        }
    }

    private List<ReceberWebhook.Evento> reivindicar() {
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return jdbc.sql("""
                UPDATE evento_webhook SET tentativas = tentativas + 1, ultima_tentativa_em = :agora
                WHERE id IN (SELECT id FROM evento_webhook
                             WHERE processado_em IS NULL AND recebido_em <= :limite
                               AND resultado IS DISTINCT FROM 'REVISAO_MANUAL'
                             ORDER BY recebido_em LIMIT :tamanho FOR UPDATE SKIP LOCKED)
                RETURNING id, tipo, id_cobranca_provedor
                """)
                .param("agora", agora).param("limite", agora.minus(ESPERA_MINIMA)).param("tamanho", TAMANHO_DO_LOTE)
                .query((rs, n) -> new ReceberWebhook.Evento(rs.getObject("id", UUID.class), rs.getString("tipo"),
                        rs.getString("id_cobranca_provedor"), false))
                .list();
    }

    private void registrarFalha(UUID id, String codigo) {
        jdbc.sql("""
                UPDATE evento_webhook SET ultimo_erro = :erro,
                       resultado = CASE WHEN tentativas >= :maximo THEN 'REVISAO_MANUAL' ELSE resultado END
                WHERE id = :id
                """)
                .param("erro", codigo.length() > 60 ? codigo.substring(0, 60) : codigo)
                .param("maximo", MAXIMO_DE_TENTATIVAS).param("id", id).update();
    }
}
