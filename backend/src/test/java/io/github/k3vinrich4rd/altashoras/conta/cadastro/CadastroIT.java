package io.github.k3vinrich4rd.altashoras.conta.cadastro;

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

/** A1 a A9: criar conta. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class CadastroIT {

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
    @DisplayName("A1: cadastro válido responde 201 com Location /me, papel CLIENTE, endereço incompleto e nenhuma senha")
    void cadastroValido() throws Exception {
        String corpo = mvc.perform(json(post("/auth/cadastro"), corpoDeCadastro(ContaDeTeste.emailNovo(), ContaDeTeste.SENHA)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/me"))
                .andExpect(jsonPath("$.papeis[0]").value("CLIENTE"))
                .andExpect(jsonPath("$.endereco.cep").value("01001000"))
                .andExpect(jsonPath("$.endereco.completo").value(false))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> campos = JsonPath.read(corpo, "$");
        assertThat(campos).containsOnlyKeys("id", "nome", "email", "papeis", "endereco", "deveTrocarSenha");
        assertThat(corpo).doesNotContain("senha\"").doesNotContain(ContaDeTeste.SENHA).doesNotContain("$2");
    }

    @Test
    @DisplayName("A2: no banco a senha é só hash BCrypt, o e-mail fica em minúsculas e o WhatsApp só com dígitos")
    void dadosNormalizados() throws Exception {
        String email = "Cliente.MAIUSCULO." + ContaDeTeste.emailNovo();
        mvc.perform(json(post("/auth/cadastro"), corpoDeCadastro(email, ContaDeTeste.SENHA))).andExpect(status().isCreated());
        Map<String, Object> linha = jdbc.queryForMap("SELECT email, whatsapp, senha_hash FROM usuario WHERE email = ?",
                email.toLowerCase());
        assertThat((String) linha.get("senha_hash")).startsWith("$2").isNotEqualTo(ContaDeTeste.SENHA);
        assertThat(linha).containsEntry("whatsapp", "11900000001");
    }

    @Test
    @DisplayName("A3: mesmo e-mail com maiúsculas diferentes dá 409 EMAIL_JA_CADASTRADO")
    void emailDuplicado() throws Exception {
        Conta conta = ContaDeTeste.cadastrar(mvc);
        mvc.perform(json(post("/auth/cadastro"), corpoDeCadastro(conta.email().toUpperCase(), ContaDeTeste.SENHA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_JA_CADASTRADO"));
    }

    @Test
    @DisplayName("A4: cinco campos inválidos dão 400 VALIDACAO_INVALIDA com um erro por campo")
    void camposInvalidos() throws Exception {
        String corpo = mvc.perform(json(post("/auth/cadastro"), """
                        { "nome": "A", "email": "sem-arroba", "whatsapp": "12345", "senha": "curta1234", "cep": "0100100" }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andReturn().getResponse().getContentAsString();
        List<String> campos = JsonPath.read(corpo, "$.errors[*].field");
        assertThat(campos).containsExactlyInAnyOrder("nome", "email", "whatsapp", "senha", "cep");
    }

    @Test
    @DisplayName("A5: mandar papeis no corpo dá 400 CAMPO_DESCONHECIDO e nenhuma conta é criada")
    void atribuicaoEmMassa() throws Exception {
        String email = ContaDeTeste.emailNovo();
        mvc.perform(json(post("/auth/cadastro"), """
                        { "nome": "Esperto", "email": "%s", "whatsapp": "11900000001", "senha": "Senha-de-teste-123", "papeis": ["ADMIN"] }
                        """.formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE email = ?", Integer.class, email)).isZero();
    }

    @Test
    @DisplayName("A6: JSON quebrado dá 400 JSON_MALFORMADO")
    void jsonQuebrado() throws Exception {
        mvc.perform(json(post("/auth/cadastro"), "{ \"nome\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JSON_MALFORMADO"));
    }

    @Test
    @DisplayName("A7: senha com 37 letras acentuadas (74 bytes) é recusada: o BCrypt corta em 72 bytes")
    void senhaLongaEmBytes() throws Exception {
        mvc.perform(json(post("/auth/cadastro"), corpoDeCadastro(ContaDeTeste.emailNovo(), "á".repeat(37))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("senha"));
    }

    @Test
    @DisplayName("A8: aceitaMarketing true ou ausente vira a primeira linha de consentimento, com a versão do texto")
    void consentimentoInicial() throws Exception {
        String aceitou = ContaDeTeste.emailNovo();
        mvc.perform(json(post("/auth/cadastro"), """
                { "nome": "Cliente Teste", "email": "%s", "whatsapp": "11900000001", "senha": "Senha-de-teste-123", "aceitaMarketing": true }
                """.formatted(aceitou))).andExpect(status().isCreated());
        Conta semCampo = ContaDeTeste.cadastrar(mvc);

        String sql = "SELECT c.aceito FROM consentimento c JOIN usuario u ON u.id = c.usuario_id WHERE u.email = ?";
        assertThat(jdbc.queryForList(sql, Boolean.class, aceitou)).containsExactly(true);
        assertThat(jdbc.queryForList(sql, Boolean.class, semCampo.email())).containsExactly(false);
        assertThat(jdbc.queryForObject("SELECT DISTINCT versao_texto FROM consentimento", String.class)).isEqualTo("1.2");
    }

    @Test
    @DisplayName("A9: dois cadastros simultâneos com o mesmo e-mail: um 201, um 409 e uma conta só")
    void corridaNoMesmoEmail() throws Exception {
        String email = ContaDeTeste.emailNovo();
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> respostas = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                respostas.add(executor.submit(() -> {
                    largada.await(10, TimeUnit.SECONDS);
                    return mvc.perform(json(post("/auth/cadastro"), corpoDeCadastro(email, ContaDeTeste.SENHA)))
                            .andReturn().getResponse().getStatus();
                }));
            }
            largada.countDown();
        }
        assertThat(List.of(respostas.get(0).get(), respostas.get(1).get())).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE email = ?", Integer.class, email)).isEqualTo(1);
    }
}
