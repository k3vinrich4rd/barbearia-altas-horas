package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A3: com as férias gravadas, ninguém marca dentro delas: nem cliente, nem visitante, nem a reserva manual. */
class FeriasReservaIT extends FeriasBase {

    @Test
    @DisplayName("A3: com as férias do A1, cliente, visitante e reserva manual em 22/12 15:00 recebem 422 FORA_DO_EXPEDIENTE e nada é gravado")
    void ninguemMarcaNasFerias() throws Exception {
        criarFolga(corpo(FERIAS_INICIO, FERIAS_FIM, "FERIAS", null, null)).andExpect(status().isCreated());

        reservar(conta("CLIENTE"), servico, eduardo, dez(22, 15, 0)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FORA_DO_EXPEDIENTE"));
        reservarComoVisitante(dez(22, 15, 0)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FORA_DO_EXPEDIENTE"));
        reservaManual(dez(22, 15, 0)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FORA_DO_EXPEDIENTE"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", Long.class, eduardo)).isZero();
    }
}
