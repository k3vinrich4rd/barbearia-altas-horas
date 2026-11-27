package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Cobre o telefone mascarado no GET público da política, num contexto próprio, sem trocar o
 * {@code app.privacidade.contato=contato@exemplo.test} dos demais testes.
 */
@TestPropertySource(properties = "app.privacidade.contato=11900000001")
class PoliticaPrivacidadeTelefoneIT extends PrivacidadeBase {

    @Test
    @DisplayName("GET /politica-privacidade devolve o telefone já formatado no canal e no Markdown")
    void telefoneFormatadoNoGet() throws Exception {
        String corpo = mvc.perform(MockMvcRequestBuilders.get("/politica-privacidade")).andExpect(status().isOk())
                .andExpect(jsonPath("$.canalDeContato").value("(11) 90000-0001"))
                .andReturn().getResponse().getContentAsString();

        String politica = JsonPath.read(corpo, "$.politica");
        assertThat(politica).contains("(11) 90000-0001").doesNotContain("11900000001").doesNotContain("{{");
    }
}
