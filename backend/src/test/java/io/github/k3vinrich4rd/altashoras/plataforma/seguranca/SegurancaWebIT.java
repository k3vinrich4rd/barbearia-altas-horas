package io.github.k3vinrich4rd.altashoras.plataforma.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/** A35 e A36: CORS só para a origem do front, e a API não sobe sem um segredo forte. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class SegurancaWebIT {

    @Autowired
    MockMvc mvc;

    private static final String LOGIN = """
            { "email": "ninguem@exemplo.test", "senha": "Senha-de-teste-123" }
            """;

    @Test
    @DisplayName("A44: requisição que altera dados chegando com cookie e sem token CSRF é barrada; sem cookie segue normal")
    void csrfOndeHaCookie() throws Exception {
        // Simula o ataque: um site forjando um POST que o navegador manda com cookie.
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN)
                        .header("Cookie", "sessao=qualquer"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        // O uso correto da API (sem cookie) não é afetado: chega à regra de login e recebe o 401 de credenciais.
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"));
        // Leitura com cookie não precisa de token: GET não altera nada.
        mvc.perform(get("/servicos").header("Cookie", "sessao=qualquer")).andExpect(status().isOk());
    }

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
