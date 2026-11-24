package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_OSASCO;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;

/** A7 da Fatia 11A: GET /me e a exportação nunca trazem o CEP (que nem existe mais), e mostram a região e o consentimento. */
class PerfilSemCepIT extends AlcanceBase {

    @Test
    @DisplayName("A7: conta com consentimento de região: GET /me e GET /me/dados trazem endereco.cep nulo (sai da resposta), regiaoAutorizada = true, a localidade e o consentimento REGIAO_ALCANCE na exportação")
    void perfilEExportacaoSemCep() throws Exception {
        provedores.viaCep(CEP_OSASCO, json(VIACEP_OSASCO));
        String corpo = cadastrar(ContaDeTeste.emailNovo(), REGIAO_OSASCO + ", \"aceitaMarketing\": true").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID cliente = UUID.fromString(JsonPath.read(corpo, "$.id"));

        String perfil = comoCliente(MockMvcRequestBuilders.get("/me"), cliente, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.regiaoAutorizada").value(true)).andExpect(jsonPath("$.endereco.cep").doesNotExist())
                .andExpect(jsonPath("$.endereco.bairro").value("Centro")).andExpect(jsonPath("$.endereco.cidade").value("Osasco"))
                .andExpect(jsonPath("$.endereco.uf").value("SP")).andReturn().getResponse().getContentAsString();
        assertThat(perfil).doesNotContain("06010").doesNotContain("\"cep\":\"0");

        String exportacao = comoCliente(MockMvcRequestBuilders.get("/me/dados"), cliente, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.conta.endereco.cep").doesNotExist()).andExpect(jsonPath("$.conta.endereco.bairro").value("Centro"))
                .andExpect(jsonPath("$.conta.regiaoAutorizada").value(true))
                .andExpect(jsonPath("$.consentimentos[?(@.finalidade == 'REGIAO_ALCANCE')].aceito").value(true))
                .andExpect(jsonPath("$.consentimentos[?(@.finalidade == 'REGIAO_ALCANCE')].versaoTexto").value("1.2"))
                .andReturn().getResponse().getContentAsString();
        assertThat(exportacao).doesNotContain("06010000").doesNotContain("06010-000");
        provedores.verificar();
    }
}
