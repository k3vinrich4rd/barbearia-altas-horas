package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.em;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.expiraEm;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogadaEm;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.as;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpoGuest;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.reservarComoVisitante;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/** A12 da Fatia 14A (regra da Fatia 15: 120 s): o visitante pede mais tempo só com o X-Guest-Token, sem bearer. */
class ProrrogacaoDoVisitanteIT extends PainelBase {

    @Test
    @DisplayName("A12: reserva de visitante às 10:02:15, POST /agendamentos/guest/{id}/prorrogacao com o token certo e sem bearer: 201, expiraEm 10:05:00 (120 s, Fatia 15) e prorrogacao.usada = true; repetir com a mesma chave dá 200")
    void visitanteAcrescentaUmMinuto() throws Exception {
        relogio.definir(em(10, 0, 0));
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        String criada = reservarComoVisitante(mvc, ContaDeTeste.ipNovo(), corpoGuest(servico, barbeiro, as(15, 0)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(criada, "$.id");
        String token = JsonPath.read(criada, "$.tokenGuest");
        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 3, 0));

        String chave = UUID.randomUUID().toString();
        relogio.definir(em(10, 2, 15));
        prorrogarComoVisitante(mvc, id, token, chave).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.expiraEm").value("2026-12-01T10:05:00-03:00"))
                .andExpect(jsonPath("$.prorrogacao.usada").value(true))
                .andExpect(jsonPath("$.tokenGuest").doesNotExist());

        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 5, 0));
        assertThat(prorrogadaEm(jdbc, id)).isEqualTo(em(10, 2, 15));

        relogio.definir(em(10, 2, 20));
        prorrogarComoVisitante(mvc, id, token, chave).andExpect(status().isOk())
                .andExpect(jsonPath("$.expiraEm").value("2026-12-01T10:05:00-03:00"));
        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 5, 0));
    }
}
