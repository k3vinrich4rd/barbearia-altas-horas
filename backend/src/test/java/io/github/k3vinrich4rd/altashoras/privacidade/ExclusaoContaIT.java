package io.github.k3vinrich4rd.altashoras.privacidade;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.SENHA;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.corpoDeLogin;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.corpoDeRefresh;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/** A39 a A43 e A48: a exclusão da conta (LGPD art. 18, E15, E16): anonimiza, derruba a sessão e não deixa dado pessoal para trás. */
class ExclusaoContaIT extends PrivacidadeBase {

    private Map<String, Object> linhaDoUsuario(UUID id) {
        return jdbc.queryForMap("""
                SELECT nome, email, whatsapp, senha_hash, cep, bairro, cidade, uf, ativo, anonimizado_em, versao_credencial
                FROM usuario WHERE id = ?
                """, id);
    }

    private void comEnderecoCompleto(UUID id) {
        jdbc.update("UPDATE usuario SET cep = '01001000', bairro = 'Sé', cidade = 'São Paulo', uf = 'SP' WHERE id = ?", id);
    }

    @Test
    @DisplayName("A39: cliente sem reservas exclui a conta e recebe 204; depois o login com a senha antiga, o token de acesso antigo e o refresh antigo dão 401")
    void sessaoMorreJuntoComAConta() throws Exception {
        Cliente cliente = novoCliente(false);

        delete("/me", cliente.acesso()).andExpect(status().isNoContent());

        mvc.perform(json(MockMvcRequestBuilders.post("/auth/login"), corpoDeLogin(cliente.email(), SENHA)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"));
        get("/me", cliente.acesso()).andExpect(status().isUnauthorized());
        mvc.perform(json(MockMvcRequestBuilders.post("/auth/refresh"), corpoDeRefresh(cliente.refresh())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A40: a linha do usuário fica sem dado pessoal (nome Conta excluída, e-mail excluido-<id>@anonimo.invalid, whatsapp, senha e endereço nulos), inativa, anonimizada em 01/12 09:30, e sem nenhum refresh token")
    void contaAnonimizada() throws Exception {
        Cliente cliente = novoCliente(false);
        comEnderecoCompleto(cliente.id());
        int versaoAntes = (int) linhaDoUsuario(cliente.id()).get("versao_credencial");
        assertThat(contar("SELECT count(*) FROM refresh_token WHERE usuario_id = ?", cliente.id())).isPositive();

        delete("/me", cliente.acesso()).andExpect(status().isNoContent());

        Map<String, Object> linha = linhaDoUsuario(cliente.id());
        assertThat(linha).containsEntry("nome", "Conta excluída")
                .containsEntry("email", "excluido-" + cliente.id() + "@anonimo.invalid")
                .containsEntry("ativo", false).containsEntry("versao_credencial", versaoAntes + 1);
        assertThat(linha.get("whatsapp")).isNull();
        assertThat(linha.get("senha_hash")).isNull();
        assertThat(linha.get("cep")).isNull();
        assertThat(linha.get("bairro")).isNull();
        assertThat(linha.get("cidade")).isNull();
        assertThat(linha.get("uf")).isNull();
        assertThat(instante(linha.get("anonimizado_em"))).isEqualTo(relogio.instant());
        assertThat(contar("SELECT count(*) FROM refresh_token WHERE usuario_id = ?", cliente.id())).isZero();
    }

    @Test
    @DisplayName("A41: o histórico de consentimento fica e ganha uma linha final aceito=false; solicitacao_titular guarda a EXCLUSAO com concluida_em de 01/12 09:30")
    void historicoFicaComRevogacaoFinal() throws Exception {
        relogio.reiniciar();
        String email = cadastrar(true);
        irPara(TERCA, 9, 30);
        Cliente cliente = entrar(email);
        consentimentoEm(cliente.id(), false, DadosDeTeste.em(TERCA, 9, 0));
        consentimentoEm(cliente.id(), true, DadosDeTeste.em(TERCA, 9, 15));
        assertThat(linhasDeConsentimento(cliente.id())).isEqualTo(3);

        delete("/me", cliente.acesso()).andExpect(status().isNoContent());

        var historico = consentimentosNoBanco(cliente.id());
        assertThat(historico).hasSize(4);
        assertThat(historico.get(2)).containsEntry("aceito", true);
        assertThat(historico.get(3)).containsEntry("aceito", false).containsEntry("versao_texto", "1.2");
        assertThat(instante(historico.get(3).get("registrado_em"))).isEqualTo(relogio.instant());
        var pedidos = jdbc.queryForList("SELECT tipo, concluida_em FROM solicitacao_titular WHERE usuario_id = ?", cliente.id());
        assertThat(pedidos).hasSize(1);
        assertThat(pedidos.getFirst()).containsEntry("tipo", "EXCLUSAO");
        assertThat(instante(pedidos.getFirst().get("concluida_em"))).isEqualTo(relogio.instant());
    }

    @Test
    @DisplayName("A42: o e-mail da conta excluída fica livre: novo cadastro com o mesmo e-mail dá 201")
    void emailFicaLivre() throws Exception {
        Cliente cliente = novoCliente(false);
        delete("/me", cliente.acesso()).andExpect(status().isNoContent());

        mvc.perform(json(MockMvcRequestBuilders.post("/auth/cadastro"), """
                { "nome": "Cliente Novo", "email": "%s", "whatsapp": "(11) 90000-0002", "senha": "%s" }
                """.formatted(cliente.email(), SENHA))).andExpect(status().isCreated());
        assertThat(contar("SELECT count(*) FROM usuario WHERE email = ?", cliente.email())).isEqualTo(1);
    }

    @Test
    @DisplayName("A43: BARBEIRO e ADMIN recebem 403 ACESSO_NEGADO e a conta continua intacta; sem token dá 401; o mesmo token usado depois de um 204 dá 401")
    void soClienteSeExclui() throws Exception {
        Conta barbeiro = conta("BARBEIRO");
        Conta admin = conta("ADMIN", "BARBEIRO");
        for (Conta equipe : new Conta[] {barbeiro, admin}) {
            delete("/me", equipe.token()).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
            Map<String, Object> linha = linhaDoUsuario(equipe.id());
            assertThat(linha).containsEntry("nome", "Equipe Teste").containsEntry("ativo", true);
            assertThat(linha.get("anonimizado_em")).isNull();
        }
        mvc.perform(MockMvcRequestBuilders.delete("/me")).andExpect(status().isUnauthorized());

        Cliente cliente = novoCliente(false);
        delete("/me", cliente.acesso()).andExpect(status().isNoContent());
        delete("/me", cliente.acesso()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A48: a reserva e a cobrança da conta excluída continuam com valor, status e datas, e nenhuma linha de reserva, cobrança ou solicitacao_titular guarda nome, e-mail ou WhatsApp do titular")
    void registroFinanceiroSemDadoPessoal() throws Exception {
        Cliente cliente = novoCliente(false);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID reserva = reserva(barbeiro, servico, DadosDeTeste.em(LocalDate.of(2026, 11, 30), 10, 0), "CONCLUIDO", null,
                cliente.id(), null);
        cobranca(reserva, "PAGA", DadosDeTeste.em(LocalDate.of(2026, 11, 29), 18, 0));
        Map<String, Object> cobrancaAntes = jdbc.queryForMap(
                "SELECT valor_centavos, status, criado_em FROM cobranca WHERE reserva_id = ?", reserva);

        delete("/me", cliente.acesso()).andExpect(status().isNoContent());

        assertThat(jdbc.queryForMap("SELECT valor_centavos, status, criado_em FROM cobranca WHERE reserva_id = ?", reserva))
                .isEqualTo(cobrancaAntes);
        assertThat(statusDaReserva(reserva)).isEqualTo("CONCLUIDO");
        String tudo = String.join("\n",
                jdbc.queryForList("SELECT row_to_json(r)::text FROM reserva r WHERE cliente_id = ?", String.class, cliente.id()))
                + jdbc.queryForList("SELECT row_to_json(c)::text FROM cobranca c WHERE reserva_id = ?", String.class, reserva)
                + jdbc.queryForList("SELECT row_to_json(s)::text FROM solicitacao_titular s WHERE usuario_id = ?",
                        String.class, cliente.id());
        assertThat(tudo).doesNotContain("Cliente Teste").doesNotContain(cliente.email())
                .doesNotContain("11900000001").doesNotContain("90000-0001");
    }
}
