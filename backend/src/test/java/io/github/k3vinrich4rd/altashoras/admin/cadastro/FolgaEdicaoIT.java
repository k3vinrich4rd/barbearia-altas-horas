package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A5, A6 e A10: o PUT da folga (ADR 0015, seção 3) substitui o período, mexe na disponibilidade e respeita os conflitos. */
class FolgaEdicaoIT extends FeriasBase {

    private static final LocalDate SABADO = LocalDate.of(2026, 12, 5);

    private String folgaDas14As16() throws Exception {
        return idDe(criarFolga(corpo(dez(5, 14, 0), dez(5, 16, 0), "FOLGA", "Compromisso", null))
                .andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("A5: PUT da folga de 05/12 14:00–16:00 para 13:00–17:00, tipo FOLGA e motivo novo dá 200 com atualizadaEm preenchido; a disponibilidade de 05/12 não tem início que invada 13:00–17:00; auditoria FOLGA_ALTERADA")
    void aumentarOPeriodo() throws Exception {
        String id = folgaDas14As16();

        alterarFolga(id, corpo(dez(5, 13, 0), dez(5, 17, 0), "FOLGA", "Motivo novo", null)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.inicio").value("2026-12-05T13:00:00-03:00"))
                .andExpect(jsonPath("$.fim").value("2026-12-05T17:00:00-03:00"))
                .andExpect(jsonPath("$.motivo").value("Motivo novo"))
                .andExpect(jsonPath("$.tipo").value("FOLGA"))
                .andExpect(jsonPath("$.atualizadaEm").value("2026-12-01T10:00:00-03:00"))
                .andExpect(jsonPath("$.reservasMantidas").isEmpty());

        assertThat(horasDe(SABADO)).hasSize(16).contains("11:30", "17:00")
                .doesNotContain("13:00", "13:30", "14:00", "14:30", "15:00", "15:30", "16:00", "16:30");
        Map<String, Object> linha = jdbc.queryForMap("SELECT atualizado_por, atualizado_em FROM folga WHERE id = ?::uuid", id);
        assertThat(linha.get("atualizado_por")).isEqualTo(admin.id());
        assertThat(auditoria("FOLGA_ALTERADA")).singleElement().asString().contains("\"tipo\": \"FOLGA\"")
                .doesNotContain("Motivo novo");
        get(rotaDasFolgas(eduardo), admin).andExpect(jsonPath("$[0].atualizadaEm").value("2026-12-01T10:00:00-03:00"));
    }

    @Test
    @DisplayName("A6: PUT reduzindo a folga do A5 para 05/12 15:00–16:00 dá 200, e os inícios que não invadem 15:00–16:00 voltam a aparecer na disponibilidade")
    void reduzirOPeriodo() throws Exception {
        String id = folgaDas14As16();
        alterarFolga(id, corpo(dez(5, 13, 0), dez(5, 17, 0), "FOLGA", null, null)).andExpect(status().isOk());

        alterarFolga(id, corpo(dez(5, 15, 0), dez(5, 16, 0), "FOLGA", null, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value("2026-12-05T15:00:00-03:00"));

        assertThat(horasDe(SABADO)).hasSize(22).contains("13:00", "13:30", "14:00", "14:30", "16:00", "16:30")
                .doesNotContain("15:00", "15:30");
        assertThat(auditoria("FOLGA_ALTERADA")).hasSize(2);
    }

    @Test
    @DisplayName("A10: PUT que aumenta uma folga por cima das reservas do A8, sem manterReservas, dá 422 FOLGA_COM_RESERVAS com conflitos[] e a folga continua como era")
    void aumentarPorCimaDeReservas() throws Exception {
        UUID[] reservas = reservasDoA8();
        String id = idDe(criarFolga(corpo(dez(22, 10, 0), dez(22, 11, 0), "FOLGA", null, null))
                .andExpect(status().isCreated()));

        alterarFolga(id, corpo(dez(22, 10, 0), dez(22, 18, 0), "FERIAS", null, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FOLGA_COM_RESERVAS"))
                .andExpect(jsonPath("$.conflitos.length()").value(2))
                .andExpect(jsonPath("$.conflitos[0].reservaId").value(reservas[0].toString()))
                .andExpect(jsonPath("$.conflitos[1].reservaId").value(reservas[1].toString()));

        Map<String, Object> folga = jdbc.queryForMap("SELECT fim, tipo, atualizado_em FROM folga WHERE id = ?::uuid", id);
        assertThat(folga.get("tipo")).isEqualTo("FOLGA");
        assertThat(folga.get("atualizado_em")).isNull();
        assertThat(((java.time.OffsetDateTime) jdbc.queryForObject("SELECT fim FROM folga WHERE id = ?::uuid",
                java.time.OffsetDateTime.class, id)).toInstant()).isEqualTo(dez(22, 11, 0).toInstant());
        assertThat(auditoria("FOLGA_ALTERADA")).isEmpty();
    }
}
