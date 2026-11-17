package io.github.k3vinrich4rd.altashoras.pagamento.politica;

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
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/** A4: o texto que o cliente aceita antes de pagar é versionado; vale o de maior versão. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class PoliticaPagamentoIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("A4: GET /politica-pagamento devolve a política de maior versão")
    void politicaVigente() throws Exception {
        int nova = jdbc.queryForObject("SELECT max(versao) + 1 FROM politica_pagamento", Integer.class);
        jdbc.update("INSERT INTO politica_pagamento (versao, texto) VALUES (?, ?)", nova, "Texto da versão " + nova);

        mvc.perform(get("/politica-pagamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(nova))
                .andExpect(jsonPath("$.texto").value("Texto da versão " + nova))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
