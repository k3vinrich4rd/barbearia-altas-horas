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

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** A17 a A21: renovar a sessão sem pedir senha, com rotação e detecção de reuso (R27). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class RefreshRotativoIT {

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

    private Sessao novaSessao() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        return ContaDeTeste.entrar(mvc, conta.email(), conta.senha());
    }

    private org.springframework.test.web.servlet.ResultActions renovar(String refresh) throws Exception {
        return mvc.perform(json(post("/auth/refresh"), corpoDeRefresh(refresh)));
    }

    @Test
    @DisplayName("A17: refresh válido devolve um par novo; o banco guarda só o hash de 64 hex, nunca o valor")
    void rotacao() throws Exception {
        Sessao sessao = novaSessao();
        String corpo = renovar(sessao.refresh()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String novo = JsonPath.read(corpo, "$.refreshToken");
        assertThat(novo).startsWith("r1_").isNotEqualTo(sessao.refresh());
        assertThat((String) JsonPath.read(corpo, "$.accessToken")).isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE hash IN (?, ?)", Integer.class,
                sessao.refresh(), novo)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE hash ~ '^[0-9a-f]{64}$'", Integer.class))
                .isEqualTo(jdbc.queryForObject("SELECT count(*) FROM refresh_token", Integer.class));
    }

    @Test
    @DisplayName("A18: reusar um refresh já usado dá 401 REFRESH_REUTILIZADO e derruba a família inteira")
    void reusoRevogaAFamilia() throws Exception {
        Sessao sessao = novaSessao();
        String novo = JsonPath.read(renovar(sessao.refresh()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.refreshToken");

        renovar(sessao.refresh()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_REUTILIZADO"));
        renovar(novo).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALIDO"));
    }

    @Test
    @DisplayName("A19: refresh desconhecido ou vencido (8 dias depois) dá 401 REFRESH_INVALIDO")
    void desconhecidoOuVencido() throws Exception {
        renovar("r1_isto-nao-existe").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALIDO"));
        Sessao sessao = novaSessao();
        relogio.avancar(Duration.ofDays(8));
        renovar(sessao.refresh()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALIDO"));
    }

    @Test
    @DisplayName("A20: usuário desativado depois do login não renova mais")
    void usuarioDesativado() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        Sessao sessao = ContaDeTeste.entrar(mvc, conta.email(), conta.senha());
        jdbc.update("UPDATE usuario SET ativo = false WHERE email = ?", conta.email());
        renovar(sessao.refresh()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALIDO"));
    }

    @Test
    @DisplayName("A21: o mesmo refresh em 8 requisições simultâneas: no máximo uma renova, as outras recebem 401")
    void corridaNoMesmoRefresh() throws Exception {
        Sessao sessao = novaSessao();
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> respostas = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                respostas.add(executor.submit(() -> {
                    largada.await(10, TimeUnit.SECONDS);
                    return renovar(sessao.refresh()).andReturn().getResponse().getStatus();
                }));
            }
            largada.countDown();
        }
        List<Integer> status = new ArrayList<>();
        for (Future<Integer> resposta : respostas) {
            status.add(resposta.get());
        }
        assertThat(status).containsOnly(200, 401);
        assertThat(status).filteredOn(s -> s == 200).hasSizeLessThanOrEqualTo(1);
    }
}
