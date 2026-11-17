package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Job de expiração (R17, E10): a cada minuto e logo na subida (initialDelay 0), porque o Render free hiberna e a
 * API pode acordar com pendentes vencidas. O ShedLock garante uma instância por vez. O job só arruma o status: a
 * disponibilidade já ignora vencidas (R15) mesmo antes dele rodar.
 */
@Component
class ExpiracaoJob {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiracaoJob.class);
    static final int TAMANHO_DO_LOTE = 100;

    private final ExpiracaoDeReservas expiracao;
    private final TransactionTemplate transacao;
    private final Clock clock;

    ExpiracaoJob(ExpiracaoDeReservas expiracao, PlatformTransactionManager gerenciador, Clock clock) {
        this.expiracao = expiracao;
        this.transacao = new TransactionTemplate(gerenciador);
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.jobs.expiracao.atraso-inicial:0}",
            fixedDelayString = "${app.jobs.expiracao.intervalo:60000}")
    @SchedulerLock(name = "expiracao-reservas", lockAtMostFor = "PT5M")
    public void executar() {
        int total = 0;
        List<UUID> lote;
        do {
            // Um lote por transação: uma falha no meio não desfaz o que já expirou.
            lote = transacao.execute(status -> expiracao.lote(clock.instant(), TAMANHO_DO_LOTE));
            total += lote.size();
        } while (lote.size() == TAMANHO_DO_LOTE);
        if (total > 0) {
            LOG.info("Reservas expiradas pelo job: {}", total);
        }
    }
}
