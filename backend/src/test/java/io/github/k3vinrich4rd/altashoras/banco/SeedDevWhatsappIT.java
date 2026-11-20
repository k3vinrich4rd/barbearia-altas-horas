package io.github.k3vinrich4rd.altashoras.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/** A12 da Fatia 6D: no perfil dev o seed do Eduardo fica sem WhatsApp (o admin cadastra pelo painel; D122). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(PostgresTestcontainers.class)
class SeedDevWhatsappIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("A12: o seed dev não grava WhatsApp; o número do Eduardo é o comercial público que a V8 grava (D152), e GET /barbeiros o devolve")
    void seedSemWhatsapp() throws Exception {
        String seed = new String(new ClassPathResource("db/dev/R__seed_dev.sql").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(seed).doesNotContain("whatsapp");
        assertThat(jdbc.queryForObject("SELECT whatsapp FROM barbeiro WHERE nome_exibicao = 'Eduardo Candido'",
                String.class)).isEqualTo("11906941689");
        mvc.perform(get("/barbeiros")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nomeExibicao").value("Eduardo Candido"))
                .andExpect(jsonPath("$[0].whatsapp").value("11906941689"));
    }
}
