package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** A32: a política e o texto da caixa de marketing são públicos e versionados (E13). */
class PoliticaPrivacidadeIT extends PrivacidadeBase {

    @Test
    @DisplayName("A32: sem token, GET /politica-privacidade dá 200 com a versão vigente (a mesma do consentimento), os textos preenchidos, o canal de contato configurado e o prazo de 90 dias")
    void politicaPublica() throws Exception {
        String corpo = mvc.perform(MockMvcRequestBuilders.get("/politica-privacidade")).andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(Consentimentos.VERSAO_DO_TEXTO))
                .andExpect(jsonPath("$.versao").value("1.0"))
                .andExpect(jsonPath("$.textoConsentimento").isNotEmpty())
                .andExpect(jsonPath("$.politica").isNotEmpty())
                .andExpect(jsonPath("$.atualizadaEm").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String politica = JsonPath.read(corpo, "$.politica");
        assertThat(politica).contains("contato@exemplo.test").contains("90 dias").doesNotContain("{{");
        assertThat(politica).containsIgnoringCase("rascunho técnico").contains("não é parecer jurídico");
        // A estatística de alcance por região já está explicada (legítimo interesse, grupos pequenos escondidos).
        assertThat(politica).contains("legítimo interesse").contains("menos de 5");
        assertThat((String) JsonPath.read(corpo, "$.textoConsentimento")).doesNotContain("{{");
        assertThat((String) JsonPath.read(corpo, "$.atualizadaEm")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}-03:00");
    }
}
