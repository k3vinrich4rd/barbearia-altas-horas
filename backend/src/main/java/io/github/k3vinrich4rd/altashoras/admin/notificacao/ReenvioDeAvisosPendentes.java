package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.time.Clock;
import java.time.Duration;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.agenda.ReservaConfirmada;

/**
 * Republica os {@code ReservaConfirmada} que ficaram pendentes por falha passageira do canal (E6): o outbox não tenta de
 * novo sozinho depois que o app já subiu. Só republica o que tem mais de 2 minutos, para não competir com a primeira
 * entrega; o aviso do painel já está gravado e a entrega repetida não cria outro (UNIQUE e {@code push_enviado_em}). Uma
 * consulta indexada por rodada; a cada 5 minutos.
 */
@Component
class ReenvioDeAvisosPendentes {

    private static final Duration IDADE_MINIMA = Duration.ofMinutes(2);

    private final IncompleteEventPublications incompletas;
    private final Clock clock;

    ReenvioDeAvisosPendentes(IncompleteEventPublications incompletas, Clock clock) {
        this.incompletas = incompletas;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.notificacao.reenvio.atraso-inicial:120000}",
            fixedDelayString = "${app.notificacao.reenvio.intervalo:300000}")
    @SchedulerLock(name = "reenvio-de-avisos", lockAtMostFor = "PT5M")
    public void executar() {
        republicar();
    }

    void republicar() {
        var limite = clock.instant().minus(IDADE_MINIMA);
        incompletas.resubmitIncompletePublications(publicacao -> publicacao.getEvent() instanceof ReservaConfirmada
                && publicacao.getPublicationDate().isBefore(limite));
    }
}
