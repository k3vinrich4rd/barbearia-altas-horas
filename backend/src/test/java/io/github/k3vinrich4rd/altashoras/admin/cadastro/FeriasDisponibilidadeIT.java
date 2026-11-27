package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A2: as férias do A1 tiram todos os horários do Eduardo do dia 22/12 (e só dos dias delas). */
class FeriasDisponibilidadeIT extends FeriasBase {

    @Test
    @DisplayName("A2: com as férias do A1 (21/12 a 03/01), GET /disponibilidade?data=2026-12-22 dá 200 sem nenhum horário para o Eduardo; 03/01 (o fim, meio aberto) não é dia de expediente e 05/01 volta a ter horários")
    void feriasSemHorarios() throws Exception {
        assertThat(horasDe(LocalDate.of(2026, 12, 22))).hasSize(24);
        criarFolga(corpo(FERIAS_INICIO, FERIAS_FIM, "FERIAS", null, null)).andExpect(status().isCreated());

        assertThat(horasDe(LocalDate.of(2026, 12, 22))).isEmpty();
        assertThat(horasDe(LocalDate.of(2026, 12, 21))).as("segunda: fora da jornada de qualquer jeito").isEmpty();
        assertThat(horasDe(LocalDate.of(2027, 1, 2))).as("sábado 02/01, ainda nas férias").isEmpty();
        assertThat(horasDe(LocalDate.of(2027, 1, 5))).as("terça depois das férias").hasSize(24);
    }
}
