package io.github.k3vinrich4rd.altashoras.conta.convite;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.SENHA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/**
 * A26 a A29 da Fatia 10B (ADR 0013, H31, H33): "excluir" barbeiro é DESLIGAR, não apagar. Tira o acesso e mantém o histórico
 * (reservas e auditoria apontam para o mesmo id). Só o dono desliga. Terça 01/12/2026, 09:30. Contas fictícias.
 */
class DesligarBarbeiroIT extends ConviteBase {

    @Test
    @DisplayName("A26: barbeiro só de equipe com sessão aberta: ao desligar, o token antigo dá 401 na hora, o refresh falha, a conta é anonimizada (id e papel ficam) e as reservas e a auditoria continuam válidas; nada é apagado")
    void desligarContaSoDeEquipe() throws Exception {
        Conta dono = dono();
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID barbeiro = barbeiroSemConta();
        jdbc.update("UPDATE barbeiro SET whatsapp = '11999990000' WHERE id = ?", barbeiro);
        UUID reserva = reserva(barbeiro, servico, as(9, 0), "CONFIRMADO", null, cliente("Maria Souza Teste", "11988880001"), null);
        String email = convidarEAceitar(dono, barbeiro);
        ContaDeTeste.Sessao sessao = ContaDeTeste.entrar(mvc, email, SENHA);
        UUID usuario = usuarioDoBarbeiro(barbeiro);
        get("/admin/agenda?data=2026-12-01", sessao.acesso()).andExpect(status().isOk());
        long auditoriasDele = jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE ator_id = ?", Long.class, usuario);

        desligar(dono, barbeiro).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.acesso").value("DESLIGADO")).andExpect(jsonPath("$.whatsapp").doesNotExist());

        get("/admin/agenda?data=2026-12-01", sessao.acesso()).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(ContaDeTeste.corpoDeRefresh(sessao.refresh()))).andExpect(status().isUnauthorized());
        mvc.perform(ContaDeTeste.json(MockMvcRequestBuilders.post("/auth/login"), ContaDeTeste.corpoDeLogin(email, SENHA)))
                .andExpect(status().isUnauthorized());
        var conta = jdbc.queryForMap("SELECT nome, email, senha_hash, whatsapp, ativo FROM usuario WHERE id = ?", usuario);
        assertThat(conta.get("ativo")).isEqualTo(false);
        assertThat(conta.get("senha_hash")).isNull();
        assertThat(conta.get("whatsapp")).isNull();
        assertThat((String) conta.get("email")).isNotEqualTo(email).endsWith("@anonimo.invalid");
        assertThat(conta.get("nome")).isEqualTo("Conta excluída");
        assertThat(jdbc.queryForList("SELECT papel FROM usuario_papel WHERE usuario_id = ?", String.class, usuario)).containsExactly("BARBEIRO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE usuario_id = ?", Long.class, usuario)).isZero();
        // Histórico preservado: a reserva, o barbeiro (sem WhatsApp) e a auditoria do ator continuam.
        assertThat(jdbc.queryForObject("SELECT barbeiro_id FROM reserva WHERE id = ?", UUID.class, reserva)).isEqualTo(barbeiro);
        assertThat(jdbc.queryForObject("SELECT whatsapp FROM barbeiro WHERE id = ?", String.class, barbeiro)).isNull();
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM barbeiro WHERE id = ?", UUID.class, barbeiro)).isEqualTo(usuario);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE ator_id = ?", Long.class, usuario)).isEqualTo(auditoriasDele);
        assertThat(jdbc.queryForList("SELECT acao FROM auditoria_admin WHERE recurso_id = ?", String.class, barbeiro)).contains("BARBEIRO_DESLIGADO");
    }

    @Test
    @DisplayName("A26: desligar revoga o convite pendente (o código dá 422) e o barbeiro fica DESLIGADO")
    void desligarRevogaOConvitePendente() throws Exception {
        Conta dono = dono();
        UUID barbeiro = barbeiroSemConta();
        String codigo = gerarCodigo(dono, barbeiro);

        desligar(dono, barbeiro).andExpect(status().isOk()).andExpect(jsonPath("$.acesso").value("DESLIGADO"));

        aceitar(codigo, emailDoBarbeiro(), SENHA).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE nome = (SELECT nome_exibicao FROM barbeiro WHERE id = ?)",
                Long.class, barbeiro)).isZero();
    }

    @Test
    @DisplayName("A27: barbeiro que também é cliente: ao desligar, o token antigo (com BARBEIRO) dá 401, a conta segue ativa como cliente com nome, e-mail e reservas intactos, sem o papel BARBEIRO, e o painel dá 403")
    void desligarQuemTambemECliente() throws Exception {
        Conta dono = dono();
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        ContaDeTeste.Conta ana = novoCliente();
        UUID barbeiro = barbeiroSemConta();
        UUID outro = barbeiroSemConta();
        UUID comoCliente = reserva(outro, servico, as(11, 0), "CONFIRMADO", null, UUID.fromString(ana.id()), null);
        aceitarLogado(ContaDeTeste.entrar(mvc, ana.email(), SENHA).acesso(), gerarCodigo(dono, barbeiro), ana.email(), null)
                .andExpect(status().isNoContent());
        String tokenAntigo = ContaDeTeste.entrar(mvc, ana.email(), SENHA).acesso();
        get("/admin/agenda?data=2026-12-01", tokenAntigo).andExpect(status().isOk());
        String nome = jdbc.queryForObject("SELECT nome FROM usuario WHERE email = ?", String.class, ana.email());

        desligar(dono, barbeiro).andExpect(status().isOk());

        get("/admin/agenda?data=2026-12-01", tokenAntigo).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT ativo FROM usuario WHERE email = ?", Boolean.class, ana.email())).isTrue();
        assertThat(jdbc.queryForObject("SELECT nome FROM usuario WHERE email = ?", String.class, ana.email())).isEqualTo(nome);
        assertThat(jdbc.queryForList("SELECT papel FROM usuario_papel WHERE usuario_id = ?", String.class, UUID.fromString(ana.id())))
                .containsExactly("CLIENTE");
        String tokenDeCliente = ContaDeTeste.entrar(mvc, ana.email(), SENHA).acesso();
        get("/me/agendamentos", tokenDeCliente).andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].id").value(comoCliente.toString()));
        get("/admin/agenda?data=2026-12-01", tokenDeCliente).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A28: depois de desligado, reativar sozinho (ativo: true) não devolve o acesso (DESLIGADO, o login antigo não entra); só um convite novo, aceito com um e-mail novo, devolve (ATIVO, ligado à conta nova)")
    void reativarExigeConviteNovo() throws Exception {
        Conta dono = dono();
        UUID barbeiro = barbeiroSemConta();
        String emailAntigo = convidarEAceitar(dono, barbeiro);
        UUID contaAntiga = usuarioDoBarbeiro(barbeiro);
        desligar(dono, barbeiro).andExpect(status().isOk());

        reativar(dono, barbeiro).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.acesso").value("DESLIGADO"));
        mvc.perform(ContaDeTeste.json(MockMvcRequestBuilders.post("/auth/login"), ContaDeTeste.corpoDeLogin(emailAntigo, SENHA)))
                .andExpect(status().isUnauthorized());

        String emailNovo = convidarEAceitar(dono, barbeiro);

        get("/admin/barbeiros", dono).andExpect(jsonPath("$[?(@.id=='" + barbeiro + "')].acesso").value("ATIVO"));
        assertThat(usuarioDoBarbeiro(barbeiro)).isNotEqualTo(contaAntiga);
        get("/admin/agenda?data=2026-12-01", tokenDe(emailNovo)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A29: barbeiro ativo que também é cliente chama DELETE /me e recebe 409 pedindo que o dono desligue antes, sem apagar nada; depois de desligado, a exclusão de cliente passa (204)")
    void excluirContaDeBarbeiroAtivo() throws Exception {
        Conta dono = dono();
        ContaDeTeste.Conta ana = novoCliente();
        UUID barbeiro = barbeiroSemConta();
        aceitarLogado(ContaDeTeste.entrar(mvc, ana.email(), SENHA).acesso(), gerarCodigo(dono, barbeiro), ana.email(), null)
                .andExpect(status().isNoContent());
        String token = ContaDeTeste.entrar(mvc, ana.email(), SENHA).acesso();

        mvc.perform(MockMvcRequestBuilders.delete("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USUARIO_JA_VINCULADO"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("desligar")));
        assertThat(jdbc.queryForObject("SELECT ativo AND anonimizado_em IS NULL FROM usuario WHERE email = ?", Boolean.class, ana.email())).isTrue();
        assertThat(usuarioDoBarbeiro(barbeiro)).isEqualTo(UUID.fromString(ana.id()));

        desligar(dono, barbeiro).andExpect(status().isOk());
        String tokenDeCliente = ContaDeTeste.entrar(mvc, ana.email(), SENHA).acesso();
        mvc.perform(MockMvcRequestBuilders.delete("/me").header("Authorization", "Bearer " + tokenDeCliente)).andExpect(status().isNoContent());
    }
}
