package io.github.k3vinrich4rd.altashoras.privacidade;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.SENHA;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.corpoDeLogin;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/**
 * A47: a exclusão é tudo ou nada (E15, D75). A falha vem de um gatilho do PostgreSQL que recusa a gravação: o resto do
 * trabalho da transação (que já tinha rodado) tem de desfazer. O {@code PainelBase} derruba a função do gatilho depois de
 * cada teste, mesmo se o teste quebrar no meio.
 */
class ExclusaoAtomicaIT extends PrivacidadeBase {

    private void gatilhoQueRecusa(String tabela, String quando) {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION falha_do_teste() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'falha injetada pelo teste'; END $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER falha_da_exclusao BEFORE INSERT OR UPDATE ON " + tabela
                + " FOR EACH ROW WHEN (" + quando + ") EXECUTE FUNCTION falha_do_teste()");
    }

    private void conferirIntacta(Cliente cliente, long consentimentos, long refresh) throws Exception {
        assertThat(jdbc.queryForMap("SELECT nome, email, whatsapp, ativo, anonimizado_em FROM usuario WHERE id = ?",
                cliente.id()))
                .containsEntry("nome", "Cliente Teste").containsEntry("email", cliente.email())
                .containsEntry("whatsapp", "11900000001").containsEntry("ativo", true)
                .containsEntry("anonimizado_em", null);
        assertThat(contar("SELECT count(*) FROM solicitacao_titular WHERE usuario_id = ?", cliente.id())).isZero();
        assertThat(linhasDeConsentimento(cliente.id())).isEqualTo(consentimentos);
        assertThat(contar("SELECT count(*) FROM refresh_token WHERE usuario_id = ?", cliente.id())).isEqualTo(refresh);
        mvc.perform(json(MockMvcRequestBuilders.post("/auth/login"), corpoDeLogin(cliente.email(), SENHA)))
                .andExpect(status().isOk());
        get("/me", cliente.acesso()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A47: o banco recusa o cancelamento da reserva futura: a resposta é de erro (não 204), a conta continua ativa, com todos os dados, o login funciona e não sobra solicitacao_titular")
    void falhaNoCancelamentoDaReserva() throws Exception {
        Cliente cliente = novoCliente(true);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID reserva = reserva(barbeiro, servico, DadosDeTeste.em(java.time.LocalDate.of(2026, 12, 2), 10, 0),
                "AGUARDANDO_PAGAMENTO", relogio.instant().plus(Duration.ofMinutes(10)), cliente.id(), null);
        long consentimentos = linhasDeConsentimento(cliente.id());
        long refresh = contar("SELECT count(*) FROM refresh_token WHERE usuario_id = ?", cliente.id());
        gatilhoQueRecusa("reserva", "NEW.status = 'CANCELADO'");

        delete("/me", cliente.acesso()).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ERRO_INTERNO"));

        conferirIntacta(cliente, consentimentos, refresh);
        assertThat(statusDaReserva(reserva)).isEqualTo("AGUARDANDO_PAGAMENTO");
    }

    @Test
    @DisplayName("A47: o banco recusa a revogação final, que é o ÚLTIMO passo, depois de a conta já ter sido anonimizada e os refresh apagados: tudo isso desfaz")
    void falhaNoUltimoPasso() throws Exception {
        Cliente cliente = novoCliente(true);
        long consentimentos = linhasDeConsentimento(cliente.id());
        long refresh = contar("SELECT count(*) FROM refresh_token WHERE usuario_id = ?", cliente.id());
        gatilhoQueRecusa("consentimento", "NEW.aceito = false");

        delete("/me", cliente.acesso()).andExpect(status().isInternalServerError());

        conferirIntacta(cliente, consentimentos, refresh);
        Map<String, Object> linha = jdbc.queryForMap("SELECT senha_hash IS NOT NULL AS tem_senha FROM usuario WHERE id = ?",
                cliente.id());
        assertThat(linha).containsEntry("tem_senha", true);
    }
}
