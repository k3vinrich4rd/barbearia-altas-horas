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
                .andExpect(jsonPath("$.versao").value("1.2"))
                .andExpect(jsonPath("$.textoConsentimento").isNotEmpty())
                .andExpect(jsonPath("$.politica").isNotEmpty())
                .andExpect(jsonPath("$.atualizadaEm").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String politica = JsonPath.read(corpo, "$.politica");
        assertThat(politica).contains("contato@exemplo.test").contains("90 dias").doesNotContain("{{");
        assertThat(politica).containsIgnoringCase("rascunho técnico").contains("não é parecer jurídico");
        // A estatística de alcance (versão 1.2, D149): a página mostra cidades e bairros com a quantidade de atendimentos,
        // sem nome nem contato, e o texto não promete mais esconder grupos pequenos.
        assertThat(politica).contains("legítimo interesse").contains("quantidade de atendimentos")
                .contains("sem nome nem contato").contains("1 cliente daquele bairro")
                .doesNotContain("menos de 5").doesNotContain("Outros bairros");
        assertThat((String) JsonPath.read(corpo, "$.textoConsentimento")).doesNotContain("{{");
        assertThat((String) JsonPath.read(corpo, "$.atualizadaEm")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}-03:00");
    }

    @Test
    @DisplayName("A20: a política vigente é a 1.2 (a mesma do consentimento): o texto sobre grupos com menos de 5 saiu e entrou que a página mostra cidades e bairros com a quantidade de atendimentos, sem nome nem contato, com o risco de 1 atendimento dito com clareza; os arquivos da 1.1 continuam guardados")
    void politicaVersao12() throws Exception {
        String corpo = mvc.perform(MockMvcRequestBuilders.get("/politica-privacidade")).andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value("1.2"))
                .andReturn().getResponse().getContentAsString();

        String politica = JsonPath.read(corpo, "$.politica");
        assertThat(politica).startsWith("# Política de privacidade · versão 1.2")
                .contains("cidades e os bairros").contains("quantidade de atendimentos").contains("sem nome nem contato")
                .contains("1 cliente daquele bairro").contains("sem data")
                .doesNotContain("menos de 5").doesNotContain("Outros bairros").doesNotContain("só **nomes**");
        assertThat(new org.springframework.core.io.ClassPathResource("privacidade/politica-1.1.md").exists()).isTrue();
        assertThat(new org.springframework.core.io.ClassPathResource("privacidade/consentimento-1.1.md").exists()).isTrue();
        assertThat(new org.springframework.core.io.ClassPathResource("privacidade/consentimento-1.2.md").exists()).isTrue();
    }
}
