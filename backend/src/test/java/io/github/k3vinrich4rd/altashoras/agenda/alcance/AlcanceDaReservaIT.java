package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A64 e A65 da Fatia 6C, que continuam valendo na Fatia 11 (os outros critérios desta classe foram substituídos pelos da 11:
 * ver a spec da Fatia 6). Nenhuma chamada externa: os provedores falsos não têm expectativa.
 */
class AlcanceDaReservaIT extends AlcanceBase {

    @Test
    @DisplayName("A64: cep 123 (e outros formatos errados): 400 CEP_INVALIDO, nenhuma reserva criada e zero chamadas externas, com ou sem a caixinha de região")
    void cepComFormatoErradoEBadRequest() throws Exception {
        Catalogo c = catalogo();
        for (String errado : new String[] {"123", "0100100", "010010000", "abcdefgh", "01001/000"}) {
            reservarComoVisitante(c, "10:00", errado).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CEP_INVALIDO"));
            reservarAutorizando(c, "10:00", errado).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CEP_INVALIDO"));
        }
        assertThat(contar("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", c.barbeiro())).isZero();
        provedores.verificar();
    }

    @Test
    @DisplayName("A65: visitante sem cep: 201 como antes, sem chamada externa e sem região")
    void semCepComoAntes() throws Exception {
        Catalogo c = catalogo();
        reservarComoVisitante(c, "10:00", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO")).andExpect(jsonPath("$.regiaoAutorizada").value(false));
        assertThat(jdbc.queryForMap("SELECT regiao_cidade, regiao_uf FROM reserva WHERE barbeiro_id = ?", c.barbeiro()).values())
                .containsOnlyNulls();
        provedores.verificar();
    }
}
