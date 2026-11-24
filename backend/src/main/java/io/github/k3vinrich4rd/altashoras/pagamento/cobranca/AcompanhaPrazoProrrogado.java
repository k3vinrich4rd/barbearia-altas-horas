package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.Clock;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.agenda.PrazoDaReservaProrrogado;

/**
 * O pagamento ouvindo a agenda por evento (ADR 0002, Fatia 14A, E6): o cliente acrescentou 1 minuto ao prazo da reserva e a
 * cobrança Pix pendente acompanha o prazo novo. O gateway NÃO é chamado: o Pix já gerado continua o mesmo (no Asaas o
 * vencimento é por dia, e o simulado não tem prazo), então o cliente não precisa de um Pix novo. Roda depois do commit da
 * prorrogação, numa transação própria; o UPDATE grava o valor absoluto, então reentregar o evento não muda nada.
 */
@Component
class AcompanhaPrazoProrrogado {

    private final TransicoesDeCobranca transicoes;
    private final Clock clock;

    AcompanhaPrazoProrrogado(TransicoesDeCobranca transicoes, Clock clock) {
        this.transicoes = transicoes;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void aoProrrogar(PrazoDaReservaProrrogado evento) {
        transicoes.acompanharPrazo(evento.reservaId(), evento.expiraEmNovo(), clock.instant());
    }
}
