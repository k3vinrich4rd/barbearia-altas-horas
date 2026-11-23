package io.github.k3vinrich4rd.altashoras.conta.convite;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.SENHA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/**
 * A23 e A24 da Fatia 10B (H32): o barbeiro que já é cliente. O convite soma o papel BARBEIRO à conta que a pessoa já tem, só
 * com o login dela, e nunca define nem troca a senha. Terça 01/12/2026, 09:30. Contas e e-mails fictícios.
 */
class ConviteContaExistenteIT extends ConviteBase {

    @Test
    @DisplayName("A23: cliente com conta e senha: sem login e logado em OUTRA conta dão 422; logado na própria dá 204 (soma BARBEIRO, sobe a versão da credencial, liga ao barbeiro); a senha antiga continua valendo")
    void contaQueJaExiste() throws Exception {
        Conta dono = dono();
        ContaDeTeste.Conta ana = novoCliente();
        String tokenDaAna = ContaDeTeste.entrar(mvc, ana.email(), ana.senha()).acesso();
        String tokenDeOutra = ContaDeTeste.entrar(mvc, clienteCadastrado(), SENHA).acesso();
        UUID barbeiro = barbeiroSemConta();
        String codigo = gerarCodigo(dono, barbeiro);
        int versaoAntes = jdbc.queryForObject("SELECT versao_credencial FROM usuario WHERE email = ?", Integer.class, ana.email());
        String hashAntes = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE email = ?", String.class, ana.email());

        aceitar(codigo, ana.email(), null).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CONVITE_INVALIDO"));
        aceitarLogado(tokenDeOutra, codigo, ana.email(), null).andExpect(status().isUnprocessableEntity());
        assertThat(usuarioDoBarbeiro(barbeiro)).isNull();

        aceitarLogado(tokenDaAna, codigo, ana.email(), null).andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("SELECT papel FROM usuario_papel WHERE usuario_id = ? ORDER BY papel", String.class, UUID.fromString(ana.id()))).containsExactly("BARBEIRO", "CLIENTE");
        assertThat(jdbc.queryForObject("SELECT versao_credencial FROM usuario WHERE email = ?", Integer.class, ana.email()))
                .isEqualTo(versaoAntes + 1);
        assertThat(jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE email = ?", String.class, ana.email())).isEqualTo(hashAntes);
        assertThat(usuarioDoBarbeiro(barbeiro)).isEqualTo(UUID.fromString(ana.id()));
        ContaDeTeste.entrar(mvc, ana.email(), ana.senha()); // a senha antiga continua valendo
    }

    @Test
    @DisplayName("A23: logado na conta e mandando senha, o convite responde 400 CAMPO_DESCONHECIDO e não é consumido; a senha da conta não muda")
    void contaQueJaTemSenhaNaoRecebeSenhaNova() throws Exception {
        Conta dono = dono();
        ContaDeTeste.Conta bia = novoCliente();
        String token = ContaDeTeste.entrar(mvc, bia.email(), bia.senha()).acesso();
        UUID barbeiro = barbeiroSemConta();
        String codigo = gerarCodigo(dono, barbeiro);
        String hashAntes = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE email = ?", String.class, bia.email());

        aceitarLogado(token, codigo, bia.email(), "Outra-senha-123").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));

        assertThat(jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE email = ?", String.class, bia.email())).isEqualTo(hashAntes);
        assertThat(jdbc.queryForObject("SELECT usado_em IS NULL FROM convite_conta WHERE barbeiro_id = ?", Boolean.class, barbeiro)).isTrue();
        aceitarLogado(token, codigo, bia.email(), null).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("A24: conta CLIENTE + BARBEIRO: no painel vê só a própria agenda (a de outro é 404); nas rotas de cliente vê só as próprias reservas, inclusive a marcada com outro barbeiro, sem enxergar a agenda dele")
    void clienteEBarbeiroNaMesmaConta() throws Exception {
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        Conta dono = dono();
        ContaDeTeste.Conta ana = novoCliente();
        UUID minhaAgenda = barbeiroSemConta();
        UUID outro = barbeiroSemConta();
        UUID comoCliente = reserva(outro, servico, as(11, 0), "CONFIRMADO", null, UUID.fromString(ana.id()), null);
        UUID deOutroCliente = reserva(outro, servico, as(12, 30), "CONFIRMADO", null, cliente("Ana Costa Teste", "11988880006"), null);
        UUID noMeuBarbeiro = reserva(minhaAgenda, servico, as(9, 0), "CONFIRMADO", null, cliente("Maria Souza Teste", "11988880001"), null);
        String token = ContaDeTeste.entrar(mvc, ana.email(), ana.senha()).acesso();

        aceitarLogado(token, gerarCodigo(dono, minhaAgenda), ana.email(), null).andExpect(status().isNoContent());
        String tokenComBarbeiro = ContaDeTeste.entrar(mvc, ana.email(), ana.senha()).acesso(); // o papel novo entra no token seguinte

        String agenda = get("/admin/agenda?data=2026-12-01", tokenComBarbeiro).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat((List<String>) JsonPath.read(agenda, "$[*].id")).containsExactly(noMeuBarbeiro.toString());
        get("/admin/agenda?data=2026-12-01&barbeiroId=" + outro, tokenComBarbeiro).andExpect(status().isNotFound());
        String minhas = get("/me/agendamentos", tokenComBarbeiro).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(minhas, "$.itens[*].id")).containsExactly(comoCliente.toString())
                .doesNotContain(deOutroCliente.toString(), noMeuBarbeiro.toString());
        get("/agendamentos/" + deOutroCliente, tokenComBarbeiro).andExpect(status().isNotFound());
    }
}
