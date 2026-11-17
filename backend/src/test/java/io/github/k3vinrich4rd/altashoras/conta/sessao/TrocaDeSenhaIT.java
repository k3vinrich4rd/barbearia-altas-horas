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

/** A40 e A41: trocar a própria senha, logado. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class TrocaDeSenhaIT {

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

    private static String corpo(String atual, String nova) {
        return """
                { "senhaAtual": "%s", "novaSenha": "%s" }
                """.formatted(atual, nova);
    }

    @Test
    @DisplayName("A40: troca de senha: a antiga não entra, a nova entra, a versão sobe e os refresh antigos param")
    void trocaComSucesso() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        Sessao sessao = ContaDeTeste.entrar(mvc, conta.email(), conta.senha());
        String nova = "Nova-senha-forte-456";

        mvc.perform(json(put("/me/senha"), corpo(conta.senha(), nova)).header("Authorization", "Bearer " + sessao.acesso()))
                .andExpect(status().isNoContent());

        mvc.perform(json(post("/auth/login"), corpoDeLogin(conta.email(), conta.senha()))).andExpect(status().isUnauthorized());
        ContaDeTeste.entrar(mvc, conta.email(), nova);
        assertThat(jdbc.queryForObject("SELECT versao_credencial FROM usuario WHERE email = ?", Integer.class, conta.email()))
                .isEqualTo(1);
        mvc.perform(json(post("/auth/refresh"), corpoDeRefresh(sessao.refresh())))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_INVALIDO"));
    }

    @Test
    @DisplayName("A41: senha atual errada dá 401 e nova senha curta dá 400; a senha não muda")
    void trocaRecusada() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        Sessao sessao = ContaDeTeste.entrar(mvc, conta.email(), conta.senha());

        mvc.perform(json(put("/me/senha"), corpo("Senha-errada-999", "Nova-senha-forte-456"))
                        .header("Authorization", "Bearer " + sessao.acesso()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"));
        mvc.perform(json(put("/me/senha"), corpo(conta.senha(), "curta"))
                        .header("Authorization", "Bearer " + sessao.acesso()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("novaSenha"));
        mvc.perform(json(put("/me/senha"), corpo(conta.senha(), "Nova-senha-forte-456")))
                .andExpect(status().isUnauthorized());

        ContaDeTeste.entrar(mvc, conta.email(), conta.senha());
    }
}
