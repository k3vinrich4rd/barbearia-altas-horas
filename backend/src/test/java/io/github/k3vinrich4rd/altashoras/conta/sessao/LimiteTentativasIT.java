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

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.ip;

/** A27, A28, A30, A31 e A43: limite de tentativas (R26). Cada teste usa um IP só dele. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class LimiteTentativasIT {

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

    private int login(String email, String senha, String deOnde) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpoDeLogin(email, senha)).with(ip(deOnde))).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("A27: depois de 5 erros em 15 min, o 6º login dá 429 com Retry-After, mesmo com a senha certa")
    void bloqueiaDepoisDeCincoErros() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        String meuIp = ContaDeTeste.ipNovo();
        for (int i = 0; i < 5; i++) {
            assertThat(login(conta.email(), "Senha-errada-999", meuIp)).isEqualTo(401);
        }
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeLogin(conta.email(), conta.senha())).with(ip(meuIp)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LIMITE_EXCEDIDO"))
                .andExpect(header().string("Retry-After", "900"));
    }

    @Test
    @DisplayName("A28: passados os 15 minutos, o login volta a funcionar")
    void liberaDepoisDaJanela() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        String meuIp = ContaDeTeste.ipNovo();
        for (int i = 0; i < 5; i++) {
            login(conta.email(), "Senha-errada-999", meuIp);
        }
        assertThat(login(conta.email(), conta.senha(), meuIp)).isEqualTo(429);
        relogio.avancar(Duration.ofMinutes(15));
        assertThat(login(conta.email(), conta.senha(), meuIp)).isEqualTo(200);
    }

    @Test
    @DisplayName("A30: o bloqueio é por e-mail + IP: outro e-mail do mesmo IP entra normalmente")
    void outroEmailNaoEBloqueado() throws Exception {
        Conta bloqueada = ContaDeTeste.cadastrar(mvc);
        Conta outra = ContaDeTeste.cadastrar(mvc);
        String meuIp = ContaDeTeste.ipNovo();
        for (int i = 0; i < 5; i++) {
            login(bloqueada.email(), "Senha-errada-999", meuIp);
        }
        assertThat(login(bloqueada.email(), bloqueada.senha(), meuIp)).isEqualTo(429);
        assertThat(login(outra.email(), outra.senha(), meuIp)).isEqualTo(200);
    }

    @Test
    @DisplayName("A31: o 6º cadastro do mesmo IP em 1 hora dá 429 com Retry-After")
    void limiteDeCadastroPorIp() throws Exception {
        String meuIp = ContaDeTeste.ipNovo();
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                    .content(corpoDeCadastro(ContaDeTeste.emailNovo(), ContaDeTeste.SENHA)).with(ip(meuIp)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeCadastro(ContaDeTeste.emailNovo(), ContaDeTeste.SENHA)).with(ip(meuIp)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LIMITE_EXCEDIDO"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("A43: 20 logins errados do mesmo IP com e-mails diferentes bloqueiam o IP")
    void limitePorIp() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        String meuIp = ContaDeTeste.ipNovo();
        for (int i = 0; i < 20; i++) {
            assertThat(login(ContaDeTeste.emailNovo(), "Senha-errada-999", meuIp)).isEqualTo(401);
        }
        assertThat(login(conta.email(), conta.senha(), meuIp)).isEqualTo(429);
    }
}
