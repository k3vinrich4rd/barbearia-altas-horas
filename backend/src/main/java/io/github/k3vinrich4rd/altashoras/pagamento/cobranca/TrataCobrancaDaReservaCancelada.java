package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.Clock;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.agenda.ReservaCancelada;

/**
 * O pagamento ouvindo a agenda por evento (ADR 0002, E10): a barbearia cancelou a reserva. Cobrança {@code PAGA} vira
 * {@code DEVOLUCAO_PENDENTE} (o Eduardo devolve e registra no painel); cobrança {@code PENDENTE} vira {@code CANCELADA}
 * (nunca devolução: ninguém pagou). A troca fica em {@link TransicoesDeCobranca}, com o status de origem no WHERE:
 * qualquer outro estado não muda e a reentrega do evento não tem efeito. Roda depois do commit, numa transação
 * própria; o outbox reenvia se este método falhar.
 */
@Component
class TrataCobrancaDaReservaCancelada {

    private final TransicoesDeCobranca transicoes;
    private final Clock clock;

    TrataCobrancaDaReservaCancelada(TransicoesDeCobranca transicoes, Clock clock) {
        this.transicoes = transicoes;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void aoCancelar(ReservaCancelada evento) {
        transicoes.aoCancelarReserva(evento.reservaId(), clock.instant());
    }
}
