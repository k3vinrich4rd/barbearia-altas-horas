package io.github.k3vinrich4rd.altashoras.conta.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.corpoDeLogin;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.json;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/**
 * A23 a A26 e A42: o primeiro admin (R25). Contexto próprio (banco novo, sem nenhum admin), e os testes rodam em
 * ordem porque contam a história: sem variáveis, senha fraca, criação, segunda subida, troca obrigatória.
 * A senha é gerada no teste: nenhuma senha de admin existe no repositório.
 */
@SpringBootTest(properties = "app.teste.contexto=admin-inicial")
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(OutputCaptureExtension.class)
class AdminInicialIT {

    private static final String EMAIL = "Admin.Inicial@exemplo.test";
    private static final String SENHA = "Inicial-" + UUID.randomUUID();
    private static final String NOVA = "Trocada-" + UUID.randomUUID();
    private static final UUID BARBEIRO = UUID.randomUUID();

    @Autowired
    AdminInicial adminInicial;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private int admins() {
        return jdbc.queryForObject("SELECT count(*) FROM usuario_papel WHERE papel = 'ADMIN'", Integer.class);
    }

    @Test
    @Order(1)
    @DisplayName("A25: sem as variáveis, a API sobe sem admin e o aviso não mostra e-mail nem senha")
    void semVariaveis(CapturedOutput saida) {
        assertThat(admins()).isZero();
        assertThat(adminInicial.garantir("", null)).isFalse();
        assertThat(admins()).isZero();
        assertThat(saida.getAll()).contains("Nenhum admin cadastrado").doesNotContain("@exemplo.test").doesNotContain(SENHA);
    }

    @Test
    @Order(2)
    @DisplayName("A26: ADMIN_SENHA curta faz a criação falhar, sem mostrar a senha na mensagem")
    void senhaCurta() {
        assertThatThrownBy(() -> adminInicial.garantir(EMAIL, "12345"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_SENHA")
                .hasMessageNotContaining("12345");
        assertThat(admins()).isZero();
    }

    @Test
    @Order(3)
    @DisplayName("A23: com as variáveis, cria o admin com ADMIN e BARBEIRO, senha só em hash, vinculado ao único barbeiro")
    void criaOAdmin() throws Exception {
        jdbc.update("INSERT INTO barbeiro (id, nome_exibicao) VALUES (?, 'Carlos Eduardo Cândido')", BARBEIRO);

        assertThat(adminInicial.garantir(EMAIL, SENHA)).isTrue();

        var usuario = jdbc.queryForMap("SELECT id, senha_hash, deve_trocar_senha FROM usuario WHERE email = ?", EMAIL.toLowerCase());
        assertThat((String) usuario.get("senha_hash")).startsWith("$2").doesNotContain(SENHA);
        assertThat(jdbc.queryForList("SELECT papel FROM usuario_papel WHERE usuario_id = ? ORDER BY papel", String.class,
                usuario.get("id"))).containsExactly("ADMIN", "BARBEIRO");
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM barbeiro WHERE id = ?", UUID.class, BARBEIRO))
                .isEqualTo(usuario.get("id"));
        mvc.perform(json(post("/auth/login"), corpoDeLogin(EMAIL, SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.papeis[0]").value("ADMIN"))
                .andExpect(jsonPath("$.usuario.papeis[1]").value("BARBEIRO"));
    }

    @Test
    @Order(4)
    @DisplayName("A24: com admin já criado, uma nova ADMIN_SENHA não sobrescreve a antiga")
    void naoSobrescreve() throws Exception {
        assertThat(adminInicial.garantir(EMAIL, "Outra-senha-" + UUID.randomUUID())).isFalse();
        assertThat(adminInicial.garantir("outro.admin@exemplo.test", "Outra-senha-" + UUID.randomUUID())).isFalse();
        assertThat(admins()).isEqualTo(1);
        mvc.perform(json(post("/auth/login"), corpoDeLogin(EMAIL, SENHA))).andExpect(status().isOk());
    }

    @Test
    @Order(5)
    @DisplayName("A42: a senha inicial obriga a troca: painel dá 403 TROCA_DE_SENHA_OBRIGATORIA até trocar")
    void trocaObrigatoria() throws Exception {
        String corpo = mvc.perform(json(post("/auth/login"), corpoDeLogin(EMAIL, SENHA)))
                .andExpect(jsonPath("$.deveTrocarSenha").value(true)).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(corpo, "$.accessToken");

        mvc.perform(get("/admin/qualquer-coisa").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TROCA_DE_SENHA_OBRIGATORIA"));
        mvc.perform(json(put("/me/senha"), """
                { "senhaAtual": "%s", "novaSenha": "%s" }
                """.formatted(SENHA, NOVA)).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());

        String depois = mvc.perform(json(post("/auth/login"), corpoDeLogin(EMAIL, NOVA)))
                .andExpect(jsonPath("$.deveTrocarSenha").value(false)).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/admin/qualquer-coisa").header("Authorization", "Bearer " + JsonPath.read(depois, "$.accessToken")))
                .andExpect(status().isNotFound());
    }
}
