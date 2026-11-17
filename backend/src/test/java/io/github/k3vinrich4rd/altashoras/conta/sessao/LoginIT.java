package io.github.k3vinrich4rd.altashoras.conta.sessao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.corpoDeCadastro;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.corpoDeLogin;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.corpoDeRefresh;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.json;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.ContaDeTeste.Conta;
import io.github.k3vinrich4rd.altashoras.ContaDeTeste.Sessao;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;

import java.time.Instant;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** A10 a A14: entrar. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class LoginIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelogioAjustavel relogio;

    @AfterEach
    void relogioNoLugar() {
        relogio.reiniciar();
    }

    @Autowired
    JwtDecoder decoder;

    @Test
    @DisplayName("A10: login certo devolve o par de tokens no formato do contrato")
    void loginCerto() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        mvc.perform(json(post("/auth/login"), corpoDeLogin(conta.email(), conta.senha())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEmSegundos").value(900))
                .andExpect(jsonPath("$.refreshToken", org.hamcrest.Matchers.startsWith("r1_")))
                .andExpect(jsonPath("$.refreshExpiraEm").value("2026-11-24T08:00:00-03:00"))
                .andExpect(jsonPath("$.usuario.id").value(conta.id()))
                .andExpect(jsonPath("$.usuario.papeis[0]").value("CLIENTE"))
                .andExpect(jsonPath("$.deveTrocarSenha").value(false));
    }

    @Test
    @DisplayName("A11: o JWT é HS256, com sub, papeis, ver e validade de 15 minutos")
    void conteudoDoToken() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        Sessao sessao = ContaDeTeste.entrar(mvc, conta.email(), conta.senha());
        Jwt jwt = decoder.decode(sessao.acesso());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo(conta.id());
        assertThat(jwt.getClaimAsStringList("papeis")).containsExactly("CLIENTE");
        assertThat(((Number) jwt.getClaim("ver")).intValue()).isZero();
        assertThat(jwt.getExpiresAt()).isEqualTo(RelogioFixoDeTeste.AGORA.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getIssuedAt()).isEqualTo(Instant.from(RelogioFixoDeTeste.AGORA));
    }

    @Test
    @DisplayName("A12: senha errada e e-mail inexistente dão o mesmo 401, com título e detalhe idênticos")
    void mesmaRespostaParaContaInexistente() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        String senhaErrada = mvc.perform(json(post("/auth/login"), corpoDeLogin(conta.email(), "Senha-errada-999")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"))
                .andReturn().getResponse().getContentAsString();
        String semConta = mvc.perform(json(post("/auth/login"), corpoDeLogin(ContaDeTeste.emailNovo(), "Senha-errada-999")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(semConta, "$.title")).isEqualTo(JsonPath.read(senhaErrada, "$.title"));
        assertThat((String) JsonPath.read(semConta, "$.detail")).isEqualTo(JsonPath.read(senhaErrada, "$.detail"));
    }

    @Test
    @DisplayName("A13: o e-mail do login não diferencia maiúsculas")
    void emailEmMaiusculas() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        mvc.perform(json(post("/auth/login"), corpoDeLogin(conta.email().toUpperCase(), conta.senha())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A14: conta desativada não entra, nem com a senha certa")
    void contaDesativada() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        jdbc.update("UPDATE usuario SET ativo = false WHERE email = ?", conta.email());
        mvc.perform(json(post("/auth/login"), corpoDeLogin(conta.email(), conta.senha())))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"));
    }
}
