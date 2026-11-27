package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A18 a A20: o período e os campos são validados igual no POST, no PUT e na prévia (E14), e nada é gravado. */
class FolgaValidacaoIT extends FeriasBase {

    private String existente;

    @BeforeEach
    void folgaParaEditar() throws Exception {
        existente = idDe(criarFolga(corpo(dez(5, 10, 0), dez(5, 11, 0), "FOLGA", null, null)).andExpect(status().isCreated()));
        jdbc.update("DELETE FROM auditoria_admin");
    }

    private void nadaMudou() {
        assertThat(folgasGravadas()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT atualizado_em FROM folga WHERE id = ?::uuid", OffsetDateTime.class, existente))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_admin", Long.class)).isZero();
    }

    private void periodoInvalido(OffsetDateTime inicio, OffsetDateTime fim) throws Exception {
        String corpo = corpo(inicio, fim, "FOLGA", null, null);
        criarFolga(corpo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        alterarFolga(existente, corpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        conflitos(eduardo, admin, inicio, fim).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        nadaMudou();
    }

    @Test
    @DisplayName("A18: POST, PUT ou prévia com fim antes do início ou igual a ele dá 400 VALIDACAO_INVALIDA e nada é gravado")
    void fimAntesDoInicio() throws Exception {
        periodoInvalido(dez(5, 16, 0), dez(5, 14, 0));
        periodoInvalido(dez(5, 14, 0), dez(5, 14, 0));
    }

    @Test
    @DisplayName("A19: POST, PUT ou prévia com o período todo no passado (30/11 10:00 a 30/11 12:00) dá 400 VALIDACAO_INVALIDA e nada é gravado")
    void todoNoPassado() throws Exception {
        periodoInvalido(em(2026, 11, 30, 10, 0), em(2026, 11, 30, 12, 0));
    }

    @Test
    @DisplayName("A20: tipo VIAGEM ou motivo com 201 caracteres dão 400 no POST e no PUT, e nada é gravado")
    void tipoEMotivoInvalidos() throws Exception {
        String viagem = corpo(dez(5, 14, 0), dez(5, 16, 0), "VIAGEM", null, null);
        criarFolga(viagem).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("tipo"));
        alterarFolga(existente, viagem).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("tipo"));
        String longo = corpo(dez(5, 14, 0), dez(5, 16, 0), "FOLGA", "x".repeat(201), null);
        criarFolga(longo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("motivo"));
        alterarFolga(existente, longo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("motivo"));
        nadaMudou();
    }
}
