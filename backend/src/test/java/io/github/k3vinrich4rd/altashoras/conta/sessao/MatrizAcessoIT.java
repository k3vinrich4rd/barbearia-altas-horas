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

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/** A32 a A34: quem pode o quê. 401 sem login, 403 sem o papel, e rota nova nasce protegida. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class MatrizAcessoIT {

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
    PasswordEncoder codificador;

    private String tokenDeAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        String email = "admin." + id + "@exemplo.test";
        jdbc.update("INSERT INTO usuario (id, nome, email, senha_hash) VALUES (?, 'Admin Teste', ?, ?)", id, email,
                codificador.encode(ContaDeTeste.SENHA));
        jdbc.update("INSERT INTO usuario_papel (usuario_id, papel) VALUES (?, 'ADMIN')", id);
        return ContaDeTeste.entrar(mvc, email, ContaDeTeste.SENHA).acesso();
    }

    @Test
    @DisplayName("A32: as rotas públicas continuam abertas sem login")
    void rotasPublicas() throws Exception {
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        for (String rota : List.of("/servicos", "/barbeiros", "/politica-pagamento", "/actuator/health",
                "/disponibilidade?servicoId=" + servico + "&data=2026-12-01")) {
            mvc.perform(get(rota)).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("A33: /admin/** dá 401 para anônimo, 403 para CLIENTE e passa para ADMIN (404: a rota nasce na Fatia 5)")
    void painel() throws Exception {
        Conta cliente = ContaDeTeste.cadastrar(mvc);
        String tokenDoCliente = ContaDeTeste.entrar(mvc, cliente.email(), cliente.senha()).acesso();

        mvc.perform(get("/admin/qualquer-coisa")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));
        mvc.perform(get("/admin/qualquer-coisa").header("Authorization", "Bearer " + tokenDoCliente))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACESSO_NEGADO"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        mvc.perform(get("/admin/qualquer-coisa").header("Authorization", "Bearer " + tokenDeAdmin()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("A34: rota que ninguém declarou nasce protegida: 401 para anônimo, não 404")
    void negarPorPadrao() throws Exception {
        mvc.perform(get("/qualquer-rota-nova")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));
        mvc.perform(post("/servicos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
