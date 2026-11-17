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

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** A37: nada pessoal nem secreto no log (R30). Captura a saída real do processo durante o fluxo inteiro. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
@ExtendWith(OutputCaptureExtension.class)
class SemDadoPessoalNoLogIT {

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
    @DisplayName("A37: cadastro, login errado, login certo, refresh e logout não deixam e-mail, telefone, CEP, senha nem token no log")
    void nadaNoLog(CapturedOutput saida) throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        mvc.perform(json(post("/auth/login"), corpoDeLogin(conta.email(), "Senha-errada-999")));
        mvc.perform(json(post("/auth/cadastro"), corpoDeCadastro(conta.email(), conta.senha())));
        Sessao sessao = ContaDeTeste.entrar(mvc, conta.email(), conta.senha());
        String novo = JsonPath.read(mvc.perform(json(post("/auth/refresh"), corpoDeRefresh(sessao.refresh())))
                .andReturn().getResponse().getContentAsString(), "$.refreshToken");
        mvc.perform(json(post("/auth/refresh"), corpoDeRefresh(sessao.refresh())));
        mvc.perform(json(post("/auth/logout"), corpoDeRefresh(novo)));
        mvc.perform(get("/me").header("Authorization", "Bearer token-invalido"));

        assertThat(saida.getAll())
                .doesNotContain(conta.email())
                .doesNotContain("11900000001").doesNotContain("90000-0001")
                .doesNotContain("01001000").doesNotContain("01001-000")
                .doesNotContain(conta.senha()).doesNotContain("Senha-errada-999")
                .doesNotContain(sessao.acesso()).doesNotContain(sessao.refresh()).doesNotContain(novo);
    }
}
