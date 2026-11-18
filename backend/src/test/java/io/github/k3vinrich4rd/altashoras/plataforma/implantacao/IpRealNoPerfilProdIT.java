package io.github.k3vinrich4rd.altashoras.plataforma.implantacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/**
 * A16 (Fatia 8): com o perfil {@code prod} de verdade (o application-prod.yml, sem copiar a propriedade na mão como o
 * A34 da Fatia 5), o IP que a API enxerga atrás do proxy do Render é o do {@code X-Forwarded-For}. Prova pelo efeito: o
 * limite por IP da vitrine ({@code alcance-max=30} por minuto) conta por IP de cliente, não pelo IP do proxy.
 * Variáveis do banco e dos segredos são falsas; o banco real da conexão vem do Testcontainers.
 */
@SpringBootTest(properties = {
        "app.teste.contexto=perfil-prod",
        "DB_URL=jdbc:postgresql://nao-usado.exemplo.test:5432/nao_usado",
        "DB_USUARIO=nao-usado",
        "DB_SENHA=nao-usado",
        "IDEMPOTENCIA_HMAC_SEGREDO=idempotencia-falsa-so-do-teste"})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class IpRealNoPerfilProdIT {

    private static final String PROXY = "10.0.0.1";

    @Autowired
    MockMvc mvc;

    @Autowired
    Environment ambiente;

    @Test
    @DisplayName("A16: no perfil prod, o limite por IP usa o X-Forwarded-For e não o IP do proxy")
    void ipDoClienteAtrasDoProxy() throws Exception {
        assertThat(ambiente.getProperty("server.forward-headers-strategy")).isEqualTo("framework");

        // O proxy (10.0.0.1) é sempre o mesmo; só o X-Forwarded-For muda. 30 consultas do cliente A passam...
        for (int i = 0; i < 30; i++) {
            mvc.perform(consulta("198.51.100.77")).andExpect(status().isOk());
        }
        // ...a 31ª do mesmo cliente é barrada...
        mvc.perform(consulta("198.51.100.77")).andExpect(status().isTooManyRequests());
        // ...e o cliente B, atrás do mesmo proxy, tem a cota dele.
        mvc.perform(consulta("198.51.100.88")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A16: o perfil prod não carrega o seed de desenvolvimento nem o CORS de localhost")
    void perfilProdSemNadaDeDev() {
        assertThat(ambiente.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(ambiente.getProperty("app.seguranca.cors-origens")).doesNotContain("localhost");
    }

    private static org.springframework.test.web.servlet.RequestBuilder consulta(String ipDoCliente) {
        return MockMvcRequestBuilders.get("/alcance").header("X-Forwarded-For", ipDoCliente).with(req -> {
            req.setRemoteAddr(PROXY);
            return req;
        });
    }
}
