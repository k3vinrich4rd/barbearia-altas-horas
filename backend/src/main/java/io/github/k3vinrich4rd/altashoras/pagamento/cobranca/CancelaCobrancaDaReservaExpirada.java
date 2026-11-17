package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.Clock;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.agenda.ReservaExpirada;

/**
 * O pagamento ouvindo a agenda por evento, sem chamar o código dela (ADR 0002, E24): reserva expirada não pode
 * ficar com cobrança viva. Roda depois do commit da expiração, numa transação própria; o outbox marca o evento
 * como concluído só quando este método termina sem erro (senão ele é reenviado).
 */
@Component
class CancelaCobrancaDaReservaExpirada {

    private final TransicoesDeCobranca transicoes;
    private final Clock clock;

    CancelaCobrancaDaReservaExpirada(TransicoesDeCobranca transicoes, Clock clock) {
        this.transicoes = transicoes;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void aoExpirar(ReservaExpirada evento) {
        transicoes.aoExpirarReserva(evento.reservaId(), clock.instant());
    }
}
