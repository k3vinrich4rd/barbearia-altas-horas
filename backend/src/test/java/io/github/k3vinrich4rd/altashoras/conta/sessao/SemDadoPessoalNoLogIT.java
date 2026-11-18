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
import java.util.UUID;

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
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.ContaDeTeste.Conta;
import io.github.k3vinrich4rd.altashoras.ContaDeTeste.Sessao;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.privacidade.RetencaoDeDados;

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

    @Test
    @DisplayName("A75: o roteiro da 6C (visitante agenda com CEP, CEP de formato errado, painel e vitrine do alcance) não deixa CEP, e-mail, WhatsApp nem IP no log: ele diz só \"região resolvida: sim/não\"")
    void nadaNoLogDoAlcance(CapturedOutput saida) throws Exception {
        String ip = "203.0.113.75";
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        String corpo = """
                { "servicoId": "%s", "barbeiroId": "%s", "inicio": "%s", "nome": "Visitante Alcance",
                  "email": "visitante.alcance@exemplo.test", "whatsapp": "(11) 90000-0075", "cep": "%s" }
                """;
        // Os provedores de CEP aqui apontam para uma porta fechada (nenhuma rede real): a região não é resolvida.
        mvc.perform(post("/agendamentos/guest").contentType(MediaType.APPLICATION_JSON).with(ContaDeTeste.ip(ip))
                .content(corpo.formatted(servico, barbeiro, DadosDeTeste.em(java.time.LocalDate.of(2026, 12, 1), 10, 0),
                        "01001-000"))).andExpect(status().isCreated());
        mvc.perform(post("/agendamentos/guest").contentType(MediaType.APPLICATION_JSON).with(ContaDeTeste.ip(ip))
                .content(corpo.formatted(servico, barbeiro, DadosDeTeste.em(java.time.LocalDate.of(2026, 12, 1), 10, 30),
                        "123"))).andExpect(status().isBadRequest());
        mvc.perform(get("/alcance").with(ContaDeTeste.ip(ip))).andExpect(status().isOk());

        assertThat(saida.getAll()).contains("região resolvida: não")
                .doesNotContain("01001000").doesNotContain("01001-000")
                .doesNotContain("visitante.alcance@exemplo.test").doesNotContain("11900000075").doesNotContain("90000-0075")
                .doesNotContain(ip);
    }

    @Autowired
    RetencaoDeDados retencao;

    @Test
    @DisplayName("A59: o roteiro da privacidade (cadastro com CEP e marketing, consulta de CEP, consentimento, exportação, exclusão e job de retenção) não deixa e-mail, WhatsApp, CEP, CPF nem IP no log")
    void nadaNoLogDaPrivacidade(CapturedOutput saida) throws Exception {
        String ip = "203.0.113.7";
        String email = ContaDeTeste.emailNovo();
        mvc.perform(json(post("/auth/cadastro"), """
                { "nome": "Cliente Teste", "email": "%s", "whatsapp": "(11) 90000-0001", "senha": "%s",
                  "cep": "01001-000", "aceitaMarketing": true }
                """.formatted(email, ContaDeTeste.SENHA)).with(ContaDeTeste.ip(ip))).andExpect(status().isCreated());
        // O CPF nunca é aceito no cadastro: a recusa (400) também não pode devolver o valor ao log.
        mvc.perform(json(post("/auth/cadastro"), """
                { "nome": "Cliente Teste", "email": "outro.%s", "whatsapp": "(11) 90000-0001", "senha": "%s",
                  "cpf": "52998224725" }
                """.formatted(email, ContaDeTeste.SENHA)).with(ContaDeTeste.ip(ip))).andExpect(status().isBadRequest());
        Sessao sessao = ContaDeTeste.entrar(mvc, email, ContaDeTeste.SENHA);
        String acesso = "Bearer " + sessao.acesso();

        mvc.perform(get("/cep/01001000").with(ContaDeTeste.ip(ip)));
        mvc.perform(put("/me/consentimentos/marketing").header("Authorization", acesso)
                .contentType(MediaType.APPLICATION_JSON).content("{ \"aceito\": false, \"versaoTexto\": \"1.1\" }"));
        mvc.perform(get("/me/dados").header("Authorization", acesso).with(ContaDeTeste.ip(ip))).andExpect(status().isOk());

        // Um visitante vencido e uma auditoria de mais de 1 ano, para o job ter o que limpar (e logar só a contagem).
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        DadosDeTeste.reserva(jdbc, barbeiro, DadosDeTeste.servico(jdbc, 30), DadosDeTeste.em(java.time.LocalDate.of(2026, 6, 1), 10, 0),
                "CONCLUIDO", null);
        jdbc.update("""
                INSERT INTO reserva_guest (reserva_id, nome, email, whatsapp, token_hash)
                SELECT id, 'Visitante Antigo', 'visitante.antigo@exemplo.test', '11977770001', repeat('a', 64)
                FROM reserva WHERE barbeiro_id = ?
                """, barbeiro);
        jdbc.update("""
                INSERT INTO auditoria_admin (ator_id, acao, recurso, ip, ocorrido_em)
                SELECT id, 'ACAO_ANTIGA', 'agendamento', '203.0.113.9', timestamptz '2025-01-01 10:00-03'
                FROM usuario WHERE email = ?
                """, email);
        retencao.executar();
        mvc.perform(MockMvcRequestBuilders.delete("/me").header("Authorization", acesso).with(ContaDeTeste.ip(ip)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva_guest WHERE nome = 'Visitante anonimizado'"
                + " AND email LIKE 'anonimizado-%'", Long.class)).isPositive();
        assertThat(saida.getAll())
                .doesNotContain(email).doesNotContain("outro." + email)
                .doesNotContain("11900000001").doesNotContain("90000-0001")
                .doesNotContain("01001000").doesNotContain("01001-000")
                .doesNotContain("52998224725").doesNotContain("529.982.247-25")
                .doesNotContain(ip).doesNotContain("203.0.113.9")
                .doesNotContain("visitante.antigo@exemplo.test").doesNotContain("11977770001").doesNotContain("Visitante Antigo")
                .doesNotContain(sessao.acesso()).doesNotContain(sessao.refresh())
                .contains("Retenção de dados: limpeza=reserva_guest linhas=");
    }
}
