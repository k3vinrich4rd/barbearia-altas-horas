package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/**
 * A15 (Fatia 8B, E10): com o banco fora (sem conexão no pool dentro do timeout), a rota pública responde 503
 * {@code BANCO_INDISPONIVEL} em problem+json, com Retry-After e sem nada do driver (URL, usuário): R30.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class BancoForaIT {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServicoRepository servicos;

    @Test
    @DisplayName("A15: banco sem conexão vira 503 BANCO_INDISPONIVEL em problem+json, com Retry-After e sem dado do driver")
    void bancoForaVira503Generico() throws Exception {
        when(servicos.listarAtivos()).thenThrow(new CannotGetJdbcConnectionException(
                "Failed to obtain JDBC Connection: jdbc:postgresql://host-secreto:5432/db user=usuario-secreto"));

        mvc.perform(MockMvcRequestBuilders.get("/servicos"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BANCO_INDISPONIVEL"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(content().string(not(containsString("host-secreto"))))
                .andExpect(content().string(not(containsString("usuario-secreto"))))
                .andExpect(content().string(not(containsString("jdbc"))));
    }
}
