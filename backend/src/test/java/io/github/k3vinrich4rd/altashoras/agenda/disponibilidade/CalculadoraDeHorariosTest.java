package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Bloqueio;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Regras;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Turno;

/** A regra dos horários livres, sem Spring e sem banco: dezenas de casos em milissegundos (E1 da spec). */
class CalculadoraDeHorariosTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TERCA = LocalDate.of(2026, 12, 1);
    private static final List<Turno> JORNADA = List.of(
            new Turno(LocalTime.of(7, 0), LocalTime.of(12, 0)), new Turno(LocalTime.of(13, 0), LocalTime.of(20, 0)));
    /** Regras reais: sem intervalo, slots de 30 min, 2 h de antecedência. */
    private static final Regras REGRAS = new Regras(0, 30, 120);
    private static final Instant DIAS_ANTES = em(2026, 11, 17, 8, 0);

    private static Instant em(int ano, int mes, int dia, int hora, int minuto) {
        return ZonedDateTime.of(ano, mes, dia, hora, minuto, 0, 0, FUSO).toInstant();
    }

    private static Instant as(int hora, int minuto) {
        return em(2026, 12, 1, hora, minuto);
    }

    private static List<String> horas(List<ZonedDateTime> horarios) {
        return horarios.stream().map(h -> h.toLocalTime().toString()).toList();
    }

    private static List<String> calcular(int duracaoMin, Instant agora, Bloqueio... bloqueios) {
        return horas(CalculadoraDeHorarios.calcular(TERCA, FUSO, JORNADA, List.of(bloqueios), duracaoMin, REGRAS, agora));
    }

    @Test
    @DisplayName("A6: terça livre, serviço de 30 min: 24 horários, de 07:00 a 11:30 e de 13:00 a 19:30, sem almoço")
    void tercaLivre30() {
        List<String> horarios = calcular(30, DIAS_ANTES);
        assertThat(horarios).hasSize(24).startsWith("07:00", "07:30").endsWith("19:00", "19:30")
                .contains("11:30", "13:00").doesNotContain("12:00", "12:30", "20:00");
    }

    @Test
    @DisplayName("A7: serviço de 60 min: 22 horários, sem 11:30 (invade o almoço) e sem 19:30 (passa das 20h)")
    void tercaLivre60() {
        List<String> horarios = calcular(60, DIAS_ANTES);
        assertThat(horarios).hasSize(22).contains("11:00", "19:00").doesNotContain("11:30", "19:30", "12:00", "12:30");
    }

    @Test
    @DisplayName("A9: às 09:10 do próprio dia, o primeiro horário é 11:30 (09:10 + 2 h = 11:10, próximo da grade)")
    void antecedenciaArredondaParaAGrade() {
        assertThat(calcular(30, as(9, 10))).startsWith("11:30", "13:00");
    }

    @Test
    @DisplayName("A10: antecedência inclusiva: às 09:00, o horário das 11:00 aparece")
    void antecedenciaInclusiva() {
        assertThat(calcular(30, as(9, 0))).startsWith("11:00", "11:30");
    }

    @Test
    @DisplayName("A11: às 09:10 com serviço de 60 min, o primeiro horário é 13:00 (11:30 invadiria o almoço)")
    void antecedenciaComServicoLongo() {
        assertThat(calcular(60, as(9, 10))).startsWith("13:00");
    }

    @Test
    @DisplayName("A17: folga 14:00-16:00 tira 14:00 a 15:30 do serviço de 30 min e também 13:30 do de 60 min")
    void folgaParcial() {
        Bloqueio folga = new Bloqueio(as(14, 0), as(16, 0));
        assertThat(calcular(30, DIAS_ANTES, folga)).contains("13:30", "16:00")
                .doesNotContain("14:00", "14:30", "15:00", "15:30");
        assertThat(calcular(60, DIAS_ANTES, folga)).contains("13:00", "16:00").doesNotContain("13:30", "15:30");
    }

    @Test
    @DisplayName("A18: folga cobrindo o dia todo não deixa horário nenhum")
    void folgaODiaTodo() {
        assertThat(calcular(30, DIAS_ANTES, new Bloqueio(as(0, 0), em(2026, 12, 2, 0, 0)))).isEmpty();
    }

    @Test
    @DisplayName("Reserva encostada não bloqueia: ocupação 10:00-10:30 deixa 09:30 e 10:30 livres (intervalo meio aberto)")
    void encostadaNaoBloqueia() {
        assertThat(calcular(30, DIAS_ANTES, new Bloqueio(as(10, 0), as(10, 30))))
                .contains("09:30", "10:30").doesNotContain("10:00");
    }

    @Test
    @DisplayName("Sem jornada no dia (segunda ou domingo), lista vazia")
    void semJornada() {
        assertThat(CalculadoraDeHorarios.calcular(TERCA, FUSO, List.of(), List.of(), 30, REGRAS, DIAS_ANTES)).isEmpty();
    }

    @Test
    @DisplayName("Intervalo entre atendimentos conta: com 10 min, o serviço de 30 não cabe às 11:30 nem às 19:30")
    void intervaloEntreAtendimentos() {
        List<ZonedDateTime> horarios = CalculadoraDeHorarios.calcular(TERCA, FUSO, JORNADA, List.of(), 30,
                new Regras(10, 30, 120), DIAS_ANTES);
        assertThat(horas(horarios)).contains("11:00", "19:00").doesNotContain("11:30", "19:30");
    }

    @Test
    @DisplayName("Dia já passado em relação a agora + antecedência: nada é oferecido")
    void diaPassado() {
        assertThat(calcular(30, em(2026, 12, 1, 18, 0))).isEmpty();
    }

    @Test
    @DisplayName("Turnos fora de ordem saem em ordem crescente")
    void ordemCrescente() {
        List<Turno> invertida = List.of(JORNADA.get(1), JORNADA.get(0));
        List<String> horarios = horas(CalculadoraDeHorarios.calcular(TERCA, FUSO, invertida, List.of(), 30, REGRAS, DIAS_ANTES));
        assertThat(horarios).isSorted().hasSize(24);
    }
}
