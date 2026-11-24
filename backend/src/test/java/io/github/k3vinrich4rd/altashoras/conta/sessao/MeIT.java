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

/** A15 e A16: quem sou eu, e o que acontece sem token válido. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class MeIT {

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

    @Test
    @DisplayName("A15: GET /me com token válido devolve o perfil no formato do cadastro")
    void meuPerfil() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        Sessao sessao = ContaDeTeste.entrar(mvc, conta.email(), conta.senha());
        mvc.perform(get("/me").header("Authorization", "Bearer " + sessao.acesso()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conta.id()))
                .andExpect(jsonPath("$.email").value(conta.email()))
                .andExpect(jsonPath("$.papeis[0]").value("CLIENTE"))
                .andExpect(jsonPath("$.endereco").doesNotExist())
                .andExpect(jsonPath("$.regiaoAutorizada").value(false))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    @DisplayName("A16: sem token, com assinatura errada ou com token vencido, 401 NAO_AUTENTICADO em problem+json")
    void semTokenValido() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        Sessao sessao = ContaDeTeste.entrar(mvc, conta.email(), conta.senha());
        String adulterado = sessao.acesso().substring(0, sessao.acesso().length() - 3) + "abc";

        for (String cabecalho : new String[] {null, "Bearer " + adulterado}) {
            var pedido = get("/me");
            if (cabecalho != null) {
                pedido.header("Authorization", cabecalho);
            }
            mvc.perform(pedido)
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        mvc.perform(get("/me").header("Authorization", "Bearer " + sessao.acesso())).andExpect(status().isOk());
        relogio.avancar(Duration.ofMinutes(16));
        mvc.perform(get("/me").header("Authorization", "Bearer " + sessao.acesso()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));
    }
}
