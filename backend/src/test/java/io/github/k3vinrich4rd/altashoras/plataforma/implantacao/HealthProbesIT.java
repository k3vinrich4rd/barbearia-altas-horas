package io.github.k3vinrich4rd.altashoras.plataforma.implantacao;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/**
 * A13 (Fatia 8B): liveness e readiness são públicos (sem token), devolvem só {@code {"status":"UP"}} (sem components,
 * sem detalhes: API8 do dossiê) e o health completo segue sem detalhes. O Render usa o liveness (E7).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class HealthProbesIT {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("A13: /actuator/health/liveness e /readiness respondem 200 sem token, com só o status UP")
    void livenessEReadinessPublicosESemDetalhes() throws Exception {
        for (String sonda : new String[] {"liveness", "readiness"}) {
            mvc.perform(MockMvcRequestBuilders.get("/actuator/health/" + sonda))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"status\":\"UP\"}", true));
        }
    }

    @Test
    @DisplayName("A13: o health completo continua público, UP e sem components nem detalhes")
    void healthCompletoSemDetalhes() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }
}
