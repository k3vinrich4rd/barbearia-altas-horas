package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.ContadorDeSql;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.CadastrosBase;

/** A48 a A51: barbeiros do painel, o vínculo com a conta de login e o que inativar não desfaz. */
class BarbeiroAdminIT extends CadastrosBase {

    private static String corpoDeAlteracao(String nome, UUID usuarioId, boolean ativo, long version) {
        String usuario = usuarioId == null ? "" : "\"usuarioId\": \"" + usuarioId + "\",";
        return """
                { "nomeExibicao": "%s", %s "ativo": %s, "version": %d }
                """.formatted(nome, usuario, ativo, version);
    }

    private static String corpoDeCriacao(String nome, UUID usuarioId) {
        String usuario = usuarioId == null ? "" : ", \"usuarioId\": \"" + usuarioId + "\"";
        return """
                { "nomeExibicao": "%s"%s }
                """.formatted(nome, usuario);
    }

    private String nomeDe(UUID barbeiro) {
        return jdbc.queryForObject("SELECT nome_exibicao FROM barbeiro WHERE id = ?", String.class, barbeiro);
    }

    @Test
    @DisplayName("A48: GET /admin/barbeiros lista com ativo, version, usuarioId e jornada em um número fixo de consultas; POST cria ativo e com jornada vazia")
    void listaECria() throws Exception {
        Conta admin = admin();
        UUID primeiro = DadosDeTeste.barbeiro(jdbc, true);
        DadosDeTeste.barbeiro(jdbc, false);
        get("/admin/barbeiros", admin).andExpect(status().isOk());

        long antes = ContadorDeSql.total();
        get("/admin/barbeiros", admin).andExpect(jsonPath("$.length()").value(2));
        long comDois = ContadorDeSql.total() - antes;
        for (int i = 0; i < 4; i++) {
            DadosDeTeste.barbeiro(jdbc, true);
        }
        antes = ContadorDeSql.total();
        String corpo = get("/admin/barbeiros", admin).andExpect(jsonPath("$.length()").value(6)).andReturn().getResponse()
                .getContentAsString();
        long comSeis = ContadorDeSql.total() - antes;

        assertThat(comSeis).isEqualTo(comDois).isBetween(1L, 3L);
        assertThat((List<?>) JsonPath.read(corpo, "$[?(@.id == '" + primeiro + "')].jornada[*]")).hasSize(10);
        assertThat((List<?>) JsonPath.read(corpo, "$[*].version")).hasSize(6);
        assertThat((List<Boolean>) JsonPath.read(corpo, "$[*].ativo")).containsOnlyOnce(false);
        assertThat(corpo).contains("\"usuarioId\":null");

        postJson("/admin/barbeiros", admin, corpoDeCriacao("Bruno", null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.ativo").value(true)).andExpect(jsonPath("$.jornada.length()").value(0))
                .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.nomeExibicao").value("Bruno"));
    }

    @Test
    @DisplayName("A49: usuarioId de conta CLIENTE ou inexistente é 400 no campo usuarioId; conta BARBEIRO livre é 201; conta já ligada a outro barbeiro é 409 USUARIO_JA_VINCULADO (no POST e no PUT)")
    void vinculoComAConta() throws Exception {
        Conta admin = admin();
        Conta eduardo = conta("ADMIN", "BARBEIRO");
        barbeiroDaConta(eduardo.id());
        Conta livre = conta("BARBEIRO");

        postJson("/admin/barbeiros", admin, corpoDeCriacao("Bruno", conta("CLIENTE").id()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors[0].field").value("usuarioId"));
        postJson("/admin/barbeiros", admin, corpoDeCriacao("Bruno", UUID.randomUUID())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("usuarioId"));
        postJson("/admin/barbeiros", admin, corpoDeCriacao("Bruno", eduardo.id())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USUARIO_JA_VINCULADO"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM barbeiro", Long.class)).isEqualTo(1);

        String criado = postJson("/admin/barbeiros", admin, corpoDeCriacao("Bruno", livre.id()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(livre.id().toString()))
                .andReturn().getResponse().getContentAsString();
        UUID bruno = UUID.fromString(JsonPath.read(criado, "$.id"));

        UUID semConta = DadosDeTeste.barbeiro(jdbc, true);
        put("/admin/barbeiros/" + semConta, admin, corpoDeAlteracao("Carlos", eduardo.id(), true, 0))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USUARIO_JA_VINCULADO"));
        put("/admin/barbeiros/" + semConta, admin, corpoDeAlteracao("Carlos", conta("CLIENTE").id(), true, 0))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("usuarioId"));
        put("/admin/barbeiros/" + semConta, admin, corpoDeAlteracao("Carlos", UUID.randomUUID(), true, 0))
                .andExpect(status().isBadRequest());
        // O próprio barbeiro pode se salvar de novo com a conta que já é dele.
        put("/admin/barbeiros/" + bruno, admin, corpoDeAlteracao("Bruno Silva", livre.id(), true, 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(nomeDe(semConta)).startsWith("Barbeiro ");
    }

    @Test
    @DisplayName("A50: inativar barbeiro com reserva futura tira ele de GET /barbeiros e da disponibilidade, e a reserva continua CONFIRMADO; version errada é 409 CONFLITO_DE_VERSAO")
    void inativarNaoCancela() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID reserva = reserva(barbeiro, servico, as(10, 0), "CONFIRMADO", null, cliente("Maria Souza Teste", "11988880001"),
                null);
        Conta admin = admin();
        String nome = nomeDe(barbeiro);
        mvc.perform(MockMvcRequestBuilders.get("/barbeiros")).andExpect(jsonPath("$[?(@.id == '" + barbeiro + "')]").isNotEmpty());

        put("/admin/barbeiros/" + barbeiro, admin, corpoDeAlteracao(nome, null, false, 7)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLITO_DE_VERSAO"));
        assertThat(jdbc.queryForObject("SELECT ativo FROM barbeiro WHERE id = ?", Boolean.class, barbeiro)).isTrue();

        put("/admin/barbeiros/" + barbeiro, admin, corpoDeAlteracao(nome, null, false, 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false)).andExpect(jsonPath("$.version").value(1));

        mvc.perform(MockMvcRequestBuilders.get("/barbeiros")).andExpect(jsonPath("$[?(@.id == '" + barbeiro + "')]").isEmpty());
        mvc.perform(MockMvcRequestBuilders.get("/disponibilidade").param("servicoId", servico.toString())
                .param("data", "2026-12-01")).andExpect(status().isOk())
                .andExpect(jsonPath("$.barbeiros[?(@.barbeiroId == '" + barbeiro + "')]").isEmpty());
        assertThat(statusDaReserva(reserva)).isEqualTo("CONFIRMADO");
    }

    @Test
    @DisplayName("A51: ligar a conta BARBEIRO a um barbeiro sem vínculo muda o GET /admin/agenda dela de 404 para 200 com a agenda dele")
    void ligarAContaDaAcessoAAgenda() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID reserva = reserva(barbeiro, servico, as(10, 0), "CONFIRMADO", null, cliente("Maria Souza Teste", "11988880001"),
                null);
        Conta contaDele = conta("BARBEIRO");
        String agenda = "/admin/agenda?data=2026-12-01";

        get(agenda, contaDele).andExpect(status().isNotFound());

        put("/admin/barbeiros/" + barbeiro, admin(), corpoDeAlteracao(nomeDe(barbeiro), contaDele.id(), true, 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(contaDele.id().toString()));

        get(agenda, contaDele).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(reserva.toString()));
    }
}
