package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A40, A41 e A43 da Fatia 14C: o calendário do mês do painel ({@code GET /admin/agenda/mes}). */
class AgendaDoMesIT extends AgendaNovaBase {

    @Test
    @DisplayName("A40: ADMIN com o barbeiroId do Eduardo: 31 dias em ordem; 01/12 com 3 reservas; 02/12 com folga; dom 06/12 e seg 07/12 sem expediente; 31/12 com 1 reserva e 01/01/2027 com 0 (dia civil no fuso, não o UTC)")
    void mesDoEduardo() throws Exception {
        MassaDaAgenda m = massaDaAgenda();
        String rota = "/admin/agenda/mes?mes=2026-12&barbeiroId=" + m.barbeiroEduardo();

        get(rota, m.eduardo()).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.mes").value("2026-12"));
        String corpo = corpo(rota, m.eduardo());

        List<String> datas = JsonPath.read(corpo, "$.dias[*].data");
        assertThat(datas).hasSize(31).isSorted();
        assertThat(datas.getFirst()).isEqualTo("2026-12-01");
        assertThat(datas.getLast()).isEqualTo("2026-12-31");
        assertThat(dia(corpo, "2026-12-01", "reservas")).isEqualTo(3); // 09:00, 10:00 e a aguardando no prazo
        assertThat(dia(corpo, "2026-12-01", "folga")).isEqualTo(false);
        assertThat(dia(corpo, "2026-12-01", "expediente")).isEqualTo(true);
        assertThat(dia(corpo, "2026-12-02", "folga")).isEqualTo(true);
        assertThat(dia(corpo, "2026-12-02", "reservas")).isEqualTo(0);
        assertThat(dia(corpo, "2026-12-06", "expediente")).isEqualTo(false);
        assertThat(dia(corpo, "2026-12-07", "expediente")).isEqualTo(false);
        assertThat(dia(corpo, "2026-12-31", "reservas")).isEqualTo(1);

        String janeiro = corpo("/admin/agenda/mes?mes=2027-01&barbeiroId=" + m.barbeiroEduardo(), m.eduardo());
        assertThat(JsonPath.<List<String>>read(janeiro, "$.dias[*].data")).hasSize(31);
        assertThat(dia(janeiro, "2027-01-01", "reservas")).isEqualTo(0);
    }

    @Test
    @DisplayName("A41: ADMIN sem barbeiroId: 01/12 com 4 reservas (Eduardo 3 + Bruno 1)")
    void mesDeTodos() throws Exception {
        MassaDaAgenda m = massaDaAgenda();
        String corpo = corpo("/admin/agenda/mes?mes=2026-12", m.eduardo());
        assertThat(dia(corpo, "2026-12-01", "reservas")).isEqualTo(4);
        assertThat(dia(corpo, "2026-12-02", "folga")).isEqualTo(true);
    }

    @Test
    @DisplayName("A43: mes=2026-13, mes=12-2026 e data=2026-02-30 dão 400 VALIDACAO_INVALIDA")
    void formatoInvalido() throws Exception {
        MassaDaAgenda m = massaDaAgenda();
        for (String rota : List.of("/admin/agenda/mes?mes=2026-13", "/admin/agenda/mes?mes=12-2026",
                "/admin/agenda/dia?data=2026-02-30")) {
            get(rota, m.eduardo()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        }
    }
}
