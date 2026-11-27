package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Bloqueio;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Turno;

/**
 * O expediente de um barbeiro no dia consultado (1.12.0, ADR 0015, E16 a E19), em {@code barbeiros[].expediente} de
 * GET /disponibilidade. Sai dos mesmos dados que os horários já usam (jornada e bloqueios do dia), sem consulta nova.
 * O público nunca vê o tipo nem o motivo da folga: só {@code FECHADO}. Datas já como texto no formato do contrato.
 */
public record ExpedienteDoDia(boolean aberto, @Nullable String motivoFechado, @Nullable String inicio,
        @Nullable String fim, @Nullable Almoco almoco) {

    /** Sem intervalo na jornada neste dia da semana. */
    static final String FORA_DO_EXPEDIENTE = "FORA_DO_EXPEDIENTE";
    /** Folgas ou férias cobrem todo o expediente do dia. O tipo não é público (E19). */
    static final String FECHADO = "FECHADO";

    public record Almoco(String inicio, String fim) {
    }

    /**
     * E17: {@code inicio} é o começo do primeiro intervalo, {@code fim} o fim do último e {@code almoco} o vão entre os
     * dois primeiros ({@code null} com um intervalo só). E18: fecha o dia sem intervalo na jornada
     * ({@code FORA_DO_EXPEDIENTE}) ou quando as folgas cobrem todos os intervalos do dia ({@code FECHADO}). Folga
     * parcial não fecha o dia: só somem os horários que ela invade.
     */
    static ExpedienteDoDia calcular(LocalDate data, ZoneId fuso, List<Turno> turnos, List<Bloqueio> bloqueios,
            DateTimeFormatter formato) {
        if (turnos.isEmpty()) {
            return fechado(FORA_DO_EXPEDIENTE);
        }
        List<Turno> ordenados = turnos.stream().sorted(Comparator.comparing(Turno::inicio)).toList();
        List<Bloqueio> folgas = bloqueios.stream().filter(Bloqueio::folga)
                .sorted(Comparator.comparing(Bloqueio::inicio)).toList();
        boolean tudoCoberto = ordenados.stream().allMatch(turno ->
                coberto(instante(data, fuso, turno.inicio()), instante(data, fuso, turno.fim()), folgas));
        if (tudoCoberto) {
            return fechado(FECHADO);
        }
        Almoco almoco = null;
        if (ordenados.size() > 1 && ordenados.get(0).fim().isBefore(ordenados.get(1).inicio())) {
            almoco = new Almoco(texto(data, fuso, ordenados.get(0).fim(), formato),
                    texto(data, fuso, ordenados.get(1).inicio(), formato));
        }
        return new ExpedienteDoDia(true, null, texto(data, fuso, ordenados.getFirst().inicio(), formato),
                texto(data, fuso, ordenados.getLast().fim(), formato), almoco);
    }

    private static ExpedienteDoDia fechado(String motivo) {
        return new ExpedienteDoDia(false, motivo, null, null, null);
    }

    /** [inicio, fim) inteiro dentro da união das folgas (ordenadas por início). Encostar não cobre (R2). */
    private static boolean coberto(Instant inicio, Instant fim, List<Bloqueio> folgas) {
        Instant ate = inicio;
        for (Bloqueio folga : folgas) {
            if (folga.inicio().isAfter(ate)) {
                break;
            }
            if (folga.fim().isAfter(ate)) {
                ate = folga.fim();
            }
            if (!ate.isBefore(fim)) {
                return true;
            }
        }
        return !ate.isBefore(fim);
    }

    private static Instant instante(LocalDate data, ZoneId fuso, LocalTime hora) {
        return data.atTime(hora).atZone(fuso).toInstant();
    }

    private static String texto(LocalDate data, ZoneId fuso, LocalTime hora, DateTimeFormatter formato) {
        return formato.format(data.atTime(hora).atZone(fuso));
    }
}
