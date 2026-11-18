package io.github.k3vinrich4rd.altashoras.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
    @DisplayName("A12: o seed dev não traz WhatsApp do Eduardo: a coluna fica nula e GET /barbeiros devolve whatsapp nulo para ele")
    void seedSemWhatsapp() throws Exception {
        assertThat(jdbc.queryForObject("SELECT whatsapp FROM barbeiro WHERE nome_exibicao = 'Carlos Eduardo Cândido'",
                String.class)).isNull();
        mvc.perform(get("/barbeiros")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nomeExibicao").value("Carlos Eduardo Cândido"))
                .andExpect(jsonPath("$[0].whatsapp").doesNotExist());
    }
}
