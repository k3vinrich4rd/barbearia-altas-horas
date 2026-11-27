package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.admin.cadastro.FeriasBase;

/** A22 e A23: a agenda não tem horizonte (D197); a única regra de data é não ser antes de hoje. Hoje = 01/12/2026. */
class AgendaSemLimiteIT extends FeriasBase {

    @Test
    @DisplayName("A22: hoje 01/12/2026, GET /disponibilidade?data=2028-01-05 dá 200 com horários e a reserva de cliente para 05/01/2028 10:00 dá 201: nenhuma data futura é recusada")
    void dataDistante() throws Exception {
        assertThat(horasDe(LocalDate.of(2028, 1, 5))).hasSize(24).contains("10:00");

        reservar(conta("CLIENTE"), servico, eduardo, em(2028, 1, 5, 10, 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inicio").value("2028-01-05T10:00:00-03:00"));
    }

    @Test
    @DisplayName("A23: hoje 01/12/2026, GET /disponibilidade?data=2026-11-30 continua 422 ANTECEDENCIA_INSUFICIENTE")
    void ontem() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/disponibilidade").param("servicoId", servico.toString())
                        .param("data", "2026-11-30"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ANTECEDENCIA_INSUFICIENTE"));
    }
}
