package io.github.k3vinrich4rd.altashoras.admin.agenda;

import java.util.List;
import java.util.UUID;

import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.Intervalo;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.JornadaDoBarbeiro;

/** O {@code MinhaJornada} do contrato: o barbeiro da conta e os intervalos de trabalho da semana. */
record MinhaJornada(UUID barbeiroId, String nomeExibicao, List<IntervaloDaJornada> intervalos) {

    static MinhaJornada de(JornadaDoBarbeiro jornada) {
        return new MinhaJornada(jornada.barbeiroId(), jornada.nomeExibicao(),
                jornada.intervalos().stream().map(IntervaloDaJornada::de).toList());
    }

    /** Um intervalo de trabalho: dia ISO (1 = segunda) e hora local "HH:mm". */
    record IntervaloDaJornada(int diaSemana, String horaInicio, String horaFim) {

        static IntervaloDaJornada de(Intervalo intervalo) {
            return new IntervaloDaJornada(intervalo.diaSemana(), intervalo.horaInicio().toString(),
                    intervalo.horaFim().toString());
        }
    }
}
