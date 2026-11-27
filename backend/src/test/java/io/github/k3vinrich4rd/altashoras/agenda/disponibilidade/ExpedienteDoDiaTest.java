package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Bloqueio;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Turno;

/** Bordas do cálculo do expediente (E17 e E18), sem banco. */
class ExpedienteDoDiaTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DIA = LocalDate.of(2026, 12, 5);
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final List<Turno> JORNADA = List.of(new Turno(LocalTime.of(13, 0), LocalTime.of(20, 0)),
            new Turno(LocalTime.of(7, 0), LocalTime.of(12, 0)));

    private static Instant as(int hora, int minuto) {
        return DIA.atTime(hora, minuto).atZone(SP).toInstant();
    }

    private static ExpedienteDoDia calcular(List<Bloqueio> bloqueios) {
        return ExpedienteDoDia.calcular(DIA, SP, JORNADA, bloqueios, FORMATO);
    }

    @Test
    @DisplayName("turnos fora de ordem: inicio do primeiro, fim do último e almoço entre os dois primeiros")
    void turnosForaDeOrdem() {
        ExpedienteDoDia expediente = calcular(List.of());
        assertThat(expediente.aberto()).isTrue();
        assertThat(expediente.inicio()).isEqualTo("2026-12-05T07:00:00-03:00");
        assertThat(expediente.fim()).isEqualTo("2026-12-05T20:00:00-03:00");
        assertThat(expediente.almoco()).isEqualTo(new ExpedienteDoDia.Almoco("2026-12-05T12:00:00-03:00",
                "2026-12-05T13:00:00-03:00"));
    }

    @Test
    @DisplayName("duas folgas que cobrem os dois turnos, sem cobrir o almoço, fecham o dia (FECHADO)")
    void folgasQueCobremOsTurnos() {
        ExpedienteDoDia expediente = calcular(List.of(new Bloqueio(as(13, 0), as(20, 0), true),
                new Bloqueio(as(6, 0), as(12, 0), true)));
        assertThat(expediente).isEqualTo(new ExpedienteDoDia(false, "FECHADO", null, null, null));
    }

    @Test
    @DisplayName("folgas encadeadas (07–10 e 10–12) cobrem o turno; faltar um minuto deixa o dia aberto")
    void folgasEncadeadas() {
        Bloqueio tarde = new Bloqueio(as(13, 0), as(20, 0), true);
        assertThat(calcular(List.of(new Bloqueio(as(7, 0), as(10, 0), true), new Bloqueio(as(10, 0), as(12, 0), true),
                tarde)).aberto()).isFalse();
        assertThat(calcular(List.of(new Bloqueio(as(7, 0), as(10, 0), true), new Bloqueio(as(10, 1), as(12, 0), true),
                tarde)).aberto()).isTrue();
    }

    @Test
    @DisplayName("reservas não fecham o dia, mesmo ocupando tudo: só folga ou férias fecham (E18)")
    void reservaNaoFecha() {
        ExpedienteDoDia expediente = calcular(List.of(new Bloqueio(as(7, 0), as(12, 0)), new Bloqueio(as(13, 0), as(20, 0))));
        assertThat(expediente.aberto()).isTrue();
        assertThat(expediente.motivoFechado()).isNull();
    }

    @Test
    @DisplayName("sem turno no dia: FORA_DO_EXPEDIENTE, mesmo com folga")
    void semTurno() {
        assertThat(ExpedienteDoDia.calcular(DIA, SP, List.of(), List.of(new Bloqueio(as(0, 0), as(23, 0), true)), FORMATO))
                .isEqualTo(new ExpedienteDoDia(false, "FORA_DO_EXPEDIENTE", null, null, null));
    }
}
