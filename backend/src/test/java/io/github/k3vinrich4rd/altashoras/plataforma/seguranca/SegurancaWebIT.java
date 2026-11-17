package io.github.k3vinrich4rd.altashoras.plataforma.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/** A35 e A36: CORS só para a origem do front, e a API não sobe sem um segredo forte. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class SegurancaWebIT {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("A35: preflight da origem configurada é aceito com a própria origem; de outra origem é recusado; nunca *")
    void cors() throws Exception {
        mvc.perform(options("/auth/login").header("Origin", "https://front.exemplo.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://front.exemplo.test"));
        mvc.perform(options("/auth/login").header("Origin", "https://site-malicioso.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("A36: sem JWT_SEGREDO, ou com menos de 32 bytes, a chave não é criada e a mensagem não mostra o segredo")
    void segredoObrigatorio() {
        assertThatThrownBy(() -> JwtConfig.chave(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SEGREDO");
        assertThatThrownBy(() -> JwtConfig.chave("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtConfig.chave("curto-demais")).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("curto-demais");
        assertThat(JwtConfig.chave("x".repeat(32)).getAlgorithm()).isEqualTo("HmacSHA256");
    }
}
