package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.Intervalo;

/**
 * Um intervalo de trabalho como o contrato o escreve (entrada e saída): dia ISO (1 = segunda ... 7 = domingo) e hora
 * local "HH:mm". Na entrada os campos podem faltar; quem valida é o {@link CadastroDeBarbeiros}, com o campo errado
 * apontado em {@code errors[]}.
 */
record IntervaloJornada(@Nullable Integer diaSemana, @Nullable String horaInicio, @Nullable String horaFim) {

    static IntervaloJornada de(Intervalo intervalo) {
        return new IntervaloJornada(intervalo.diaSemana(), intervalo.horaInicio().toString(),
                intervalo.horaFim().toString());
    }
}
