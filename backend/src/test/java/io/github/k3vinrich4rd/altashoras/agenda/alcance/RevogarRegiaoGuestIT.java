package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_OSASCO;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;

/** A6 da Fatia 11A: o visitante retira a autorização de região da própria reserva, com o token da reserva (LGPD art. 8º, § 5º). */
class RevogarRegiaoGuestIT extends AlcanceBase {

    private record Reserva(String id, String token) {
    }

    private Reserva reservar(Catalogo c, String hora) throws Exception {
        String corpo = reservarAutorizando(c, hora, "06010-000").andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return new Reserva(JsonPath.read(corpo, "$.id"), JsonPath.read(corpo, "$.tokenGuest"));
    }

    private ResultActions retirar(String id, String token) throws Exception {
        var pedido = delete("/agendamentos/guest/" + id + "/regiao").with(ContaDeTeste.ip(ContaDeTeste.ipNovo()));
        return mvc.perform(token == null ? pedido : pedido.header("X-Guest-Token", token));
    }

    private boolean temRegiao(String id) {
        return jdbc.queryForObject("SELECT regiao_cidade IS NOT NULL FROM reserva WHERE id = ?::uuid", Boolean.class, id);
    }

    @Test
    @DisplayName("A6: DELETE /agendamentos/guest/{id}/regiao com o X-Guest-Token certo: 204 e região apagada; de novo: 204; token errado, sem token ou de outra reserva: 404 com o mesmo corpo; a reserva segue intacta")
    void visitanteRetiraARegiao() throws Exception {
        provedores.viaCep(CEP_OSASCO, json(VIACEP_OSASCO));
        Catalogo c = catalogo();
        Reserva minha = reservar(c, "10:00");
        Reserva dePessoaNaoRelacionada = reservar(c, "11:00");
        assertThat(temRegiao(minha.id())).isTrue();

        retirar(minha.id(), minha.token()).andExpect(status().isNoContent());
        assertThat(temRegiao(minha.id())).isFalse();
        assertThat(jdbc.queryForMap("""
                SELECT regiao_bairro, regiao_municipio_ibge, regiao_consentimento_versao FROM reserva WHERE id = ?::uuid
                """, minha.id()).values()).containsOnlyNulls();
        assertThat(jdbc.queryForObject("SELECT status FROM reserva WHERE id = ?::uuid", String.class, minha.id()))
                .isEqualTo("AGUARDANDO_PAGAMENTO");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/agendamentos/guest/" + minha.id())
                .header("X-Guest-Token", minha.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.regiaoAutorizada").value(false));

        retirar(minha.id(), minha.token()).andExpect(status().isNoContent()); // idempotente

        String semToken = retirar(dePessoaNaoRelacionada.id(), null).andExpect(status().isNotFound()).andReturn().getResponse()
                .getContentAsString();
        String tokenErrado = retirar(dePessoaNaoRelacionada.id(), "g1_errado").andExpect(status().isNotFound()).andReturn()
                .getResponse().getContentAsString();
        String deOutraReserva = retirar(dePessoaNaoRelacionada.id(), minha.token()).andExpect(status().isNotFound()).andReturn()
                .getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(semToken, "$.code")).isEqualTo("RECURSO_NAO_ENCONTRADO");
        for (String corpo : new String[] {tokenErrado, deOutraReserva}) {
            assertThat(JsonPath.<String>read(corpo, "$.code")).isEqualTo(JsonPath.<String>read(semToken, "$.code"));
            assertThat(JsonPath.<String>read(corpo, "$.detail")).isEqualTo(JsonPath.<String>read(semToken, "$.detail"));
            assertThat(String.valueOf((Object) JsonPath.read(corpo, "$.status")))
                    .isEqualTo(String.valueOf((Object) JsonPath.read(semToken, "$.status")));
        }
        assertThat(temRegiao(dePessoaNaoRelacionada.id())).isTrue(); // as tentativas falhas não apagaram nada
        provedores.verificar();
    }
}
