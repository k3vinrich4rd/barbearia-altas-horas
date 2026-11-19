package io.github.k3vinrich4rd.altashoras.admin.agenda;

import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.Intervalo;

/** Um intervalo de trabalho em {@code GET /admin/minha-jornada}: dia ISO (1 = segunda) e hora local "HH:mm". */
record IntervaloDaJornada(int diaSemana, String horaInicio, String horaFim) {

    static IntervaloDaJornada de(Intervalo intervalo) {
        return new IntervaloDaJornada(intervalo.diaSemana(), intervalo.horaInicio().toString(),
                intervalo.horaFim().toString());
    }
}
