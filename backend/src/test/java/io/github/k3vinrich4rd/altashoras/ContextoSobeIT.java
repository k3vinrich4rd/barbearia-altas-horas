package io.github.k3vinrich4rd.altashoras;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** A1: a API sobe com o PostgreSQL real, o Flyway aplica a V1 e o health responde UP. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class ContextoSobeIT {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("A1: GET /actuator/health responde 200 UP com o banco conectado")
    void healthRespondeUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().exists("X-Trace-Id"));
    }
}
