package io.github.k3vinrich4rd.altashoras.plataforma.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/** A2: todo erro sai em application/problem+json (RFC 9457) com code estável e traceId (ADR 0005). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class ErroPadraoTest {

    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("A2: rota inexistente dá 404 RECURSO_NAO_ENCONTRADO em problem+json, com o traceId da requisição")
    void rotaInexistente() throws Exception {
        mvc.perform(get("/rota-que-nao-existe").header("X-Trace-Id", TRACE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "https://github.com/k3vinrich4rd/barbearia-altas-horas/blob/main/docs/contrato/contrato-api.md#recurso_nao_encontrado"))
                .andExpect(jsonPath("$.title").value("Não encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/rota-que-nao-existe"))
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.traceId").value(TRACE))
                .andExpect(header().string("X-Trace-Id", TRACE));
    }

    @Test
    @DisplayName("traceId inválido no cabeçalho é trocado por um gerado (nada de texto livre no log)")
    void traceIdForaDoFormatoEhIgnorado() throws Exception {
        mvc.perform(get("/rota-que-nao-existe").header("X-Trace-Id", "abc\ninjetado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.traceId", matchesPattern("^[0-9a-f]{32}$")));
    }

    @Test
    @DisplayName("método não aceito na rota dá 405 METODO_NAO_PERMITIDO no mesmo formato")
    void metodoNaoPermitido() throws Exception {
        mvc.perform(delete("/actuator/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("METODO_NAO_PERMITIDO"));
    }
}
