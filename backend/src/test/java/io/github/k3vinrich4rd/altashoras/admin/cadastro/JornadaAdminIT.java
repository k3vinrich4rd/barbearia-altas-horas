package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.CadastrosBase;

/** A52 a A56: a jornada semanal é substituída inteira e a disponibilidade a acompanha na hora. */
class JornadaAdminIT extends CadastrosBase {

    private static final LocalDate TERCA_DE_TESTE = LocalDate.of(2026, 12, 1);

    private UUID barbeiro;
    private UUID servico;
    private Conta admin;

    @BeforeEach
    void dados() {
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        admin = admin();
    }

    private static String intervalo(int dia, String inicio, String fim) {
        return """
                { "diaSemana": %d, "horaInicio": "%s", "horaFim": "%s" }""".formatted(dia, inicio, fim);
    }

    private String rota() {
        return "/admin/barbeiros/" + barbeiro + "/jornada";
    }

    private long intervalosGravados() {
        return jdbc.queryForObject("SELECT count(*) FROM jornada WHERE barbeiro_id = ?", Long.class, barbeiro);
    }

    @Test
    @DisplayName("A52: PUT da jornada de terça (08:00-12:00 e 13:00-18:00) devolve os dois intervalos e a disponibilidade de 01/12 cai de 24 para 18 horários, de 08:00 a 17:30")
    void substituiEAAgendaAcompanha() throws Exception {
        assertThat(horas(servico, barbeiro, TERCA_DE_TESTE)).hasSize(24);

        put(rota(), admin, "[" + intervalo(2, "08:00", "12:00") + "," + intervalo(2, "13:00", "18:00") + "]")
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].horaInicio").value("08:00")).andExpect(jsonPath("$[1].horaFim").value("18:00"));

        assertThat(horas(servico, barbeiro, TERCA_DE_TESTE)).hasSize(18).startsWith("08:00").endsWith("17:30");
        assertThat(intervalosGravados()).isEqualTo(2);
    }

    @Test
    @DisplayName("A53: intervalos do mesmo dia que se sobrepõem são 422 JORNADA_SOBREPOSTA e a jornada antiga fica intacta; encostar (12:00 com 12:00) vale")
    void sobreposicao() throws Exception {
        put(rota(), admin, "[" + intervalo(2, "08:00", "12:00") + "," + intervalo(2, "11:00", "14:00") + "]")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("JORNADA_SOBREPOSTA"));
        assertThat(intervalosGravados()).isEqualTo(10);

        put(rota(), admin, "[" + intervalo(2, "08:00", "12:00") + "," + intervalo(2, "12:00", "13:00") + "]")
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertThat(intervalosGravados()).isEqualTo(2);
    }

    @Test
    @DisplayName("A54: horaFim igual ou antes de horaInicio, 25:00, diaSemana 8, 29 intervalos e campo extra são 400 e a jornada antiga fica intacta")
    void corposInvalidos() throws Exception {
        List<String> invalidos = new ArrayList<>();
        invalidos.add("[" + intervalo(2, "10:00", "10:00") + "]");
        invalidos.add("[" + intervalo(2, "10:00", "09:00") + "]");
        invalidos.add("[" + intervalo(2, "08:00", "25:00") + "]");
        invalidos.add("[" + intervalo(8, "08:00", "12:00") + "]");
        invalidos.add("[{ \"diaSemana\": 2, \"horaInicio\": \"08:00\" }]");
        List<String> muitos = new ArrayList<>();
        for (int i = 0; i < 29; i++) {
            muitos.add(intervalo(i % 7 + 1, "0" + (i % 3) + ":00", "0" + (i % 3) + ":30"));
        }
        invalidos.add("[" + String.join(",", muitos) + "]");
        for (String corpo : invalidos) {
            put(rota(), admin, corpo).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        }
        put(rota(), admin, "[{ \"diaSemana\": 2, \"horaInicio\": \"08:00\", \"horaFim\": \"12:00\", \"extra\": 1 }]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));

        assertThat(intervalosGravados()).isEqualTo(10);
    }

    @Test
    @DisplayName("A55: PUT com lista vazia deixa 0 horários e PUT com jornada até 18:00 também, mas a reserva futura das 19:00 continua CONFIRMADO e na agenda")
    void jornadaNaoCancelaReserva() throws Exception {
        UUID reserva = reserva(barbeiro, servico, as(19, 0), "CONFIRMADO", null, cliente("Maria Souza Teste", "11988880001"),
                null);

        put(rota(), admin, "[]").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(horas(servico, barbeiro, TERCA_DE_TESTE)).isEmpty();
        assertThat(intervalosGravados()).isZero();
        assertThat(statusDaReserva(reserva)).isEqualTo("CONFIRMADO");
        get("/admin/agenda?data=2026-12-01", admin).andExpect(jsonPath("$[0].id").value(reserva.toString()));

        put(rota(), admin, "[" + intervalo(2, "07:00", "12:00") + "," + intervalo(2, "13:00", "18:00") + "]")
                .andExpect(status().isOk());
        assertThat(horas(servico, barbeiro, TERCA_DE_TESTE)).hasSize(20).endsWith("17:30").doesNotContain("19:00");
        assertThat(statusDaReserva(reserva)).isEqualTo("CONFIRMADO");
        get("/admin/agenda?data=2026-12-01", admin).andExpect(jsonPath("$[0].id").value(reserva.toString()));
    }

    @Test
    @DisplayName("A56: PUT da jornada de barbeiro inexistente é 404")
    void barbeiroInexistente() throws Exception {
        put("/admin/barbeiros/" + UUID.randomUUID() + "/jornada", admin, "[" + intervalo(2, "08:00", "12:00") + "]")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
    }
}
