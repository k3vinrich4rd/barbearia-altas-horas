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

/** A22: sair. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class LogoutIT {

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
    @DisplayName("A22: logout responde 204, a sessão para de renovar e repetir (ou mandar token desconhecido) também dá 204")
    void sair() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        Sessao sessao = ContaDeTeste.entrar(mvc, conta.email(), conta.senha());

        mvc.perform(json(post("/auth/logout"), corpoDeRefresh(sessao.refresh()))).andExpect(status().isNoContent());
        mvc.perform(json(post("/auth/refresh"), corpoDeRefresh(sessao.refresh())))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_INVALIDO"));
        mvc.perform(json(post("/auth/logout"), corpoDeRefresh(sessao.refresh()))).andExpect(status().isNoContent());
        mvc.perform(json(post("/auth/logout"), corpoDeRefresh("r1_nunca-existiu"))).andExpect(status().isNoContent());
    }
}
