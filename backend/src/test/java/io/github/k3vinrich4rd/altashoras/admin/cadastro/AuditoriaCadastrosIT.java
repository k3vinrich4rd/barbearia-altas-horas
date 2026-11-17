package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.CadastrosBase;

/** A69: cada ação dos cadastros deixa uma linha na auditoria, na mesma transação, só com nomes de campos e ids (R30). */
class AuditoriaCadastrosIT extends CadastrosBase {

    private static final String NOME_DO_BARBEIRO = "Bruno Sobrenome Fictício";
    private static final String NOME_DO_SERVICO = "Hidratação Capilar";
    private static final String MOTIVO = "Consulta médica do Bruno";

    @Test
    @DisplayName("A69: uma ação de cada tipo da 5B grava 8 linhas na auditoria (ator, recurso e id), com detalhe só de nomes de campos e ids, sem nome do barbeiro, do serviço nem motivo")
    void umaLinhaPorAcaoDeCadastro() throws Exception {
        Conta admin = admin();
        OffsetDateTime inicio = as(14, 0);

        String servico = idDe(postJson("/admin/servicos", admin, """
                { "nome": "%s", "duracaoMin": 30, "precoCentavos": 3500 }
                """.formatted(NOME_DO_SERVICO)).andExpect(status().isCreated()));
        put("/admin/servicos/" + servico, admin, """
                { "nome": "%s", "duracaoMin": 30, "precoCentavos": 4000, "ativo": true, "version": 0 }
                """.formatted(NOME_DO_SERVICO)).andExpect(status().isOk());
        String barbeiro = idDe(postJson("/admin/barbeiros", admin, """
                { "nomeExibicao": "%s" }
                """.formatted(NOME_DO_BARBEIRO)).andExpect(status().isCreated()));
        put("/admin/barbeiros/" + barbeiro, admin, """
                { "nomeExibicao": "%s Júnior", "ativo": true, "version": 0 }
                """.formatted(NOME_DO_BARBEIRO)).andExpect(status().isOk());
        put("/admin/barbeiros/" + barbeiro + "/jornada", admin, """
                [ { "diaSemana": 2, "horaInicio": "08:00", "horaFim": "12:00" },
                  { "diaSemana": 2, "horaInicio": "13:00", "horaFim": "18:00" } ]
                """).andExpect(status().isOk());
        String folga = idDe(postJson("/admin/barbeiros/" + barbeiro + "/folgas", admin, """
                { "inicio": "%s", "fim": "%s", "motivo": "%s" }
                """.formatted(inicio, inicio.plusHours(2), MOTIVO)).andExpect(status().isCreated()));
        delete("/admin/barbeiros/" + barbeiro + "/folgas/" + folga, admin).andExpect(status().isNoContent());
        put("/admin/configuracao", admin, """
                { "antecedenciaMinimaMin": 60, "intervaloEntreAtendimentosMin": 0, "granularidadeMin": 30,
                  "horizonteDias": 30, "expiracaoPagamentoMin": 15, "version": 0 }
                """).andExpect(status().isOk());

        List<Map<String, Object>> linhas = jdbc.queryForList(
                "SELECT ator_id, acao, recurso, recurso_id, detalhe::text AS detalhe, ocorrido_em FROM auditoria_admin ORDER BY acao");
        assertThat(linhas).hasSize(8);
        assertThat(linhas.stream().map(l -> (String) l.get("acao"))).containsExactlyInAnyOrder("SERVICO_CRIADO",
                "SERVICO_ALTERADO", "BARBEIRO_CRIADO", "BARBEIRO_ALTERADO", "JORNADA_SUBSTITUIDA", "FOLGA_CRIADA",
                "FOLGA_REMOVIDA", "CONFIGURACAO_ALTERADA");
        assertThat(linhas).allSatisfy(linha -> {
            assertThat(linha.get("ator_id")).isEqualTo(admin.id());
            assertThat(linha.get("recurso")).isNotNull();
            assertThat(((java.sql.Timestamp) linha.get("ocorrido_em")).toInstant()).isEqualTo(RelogioFixoDeTeste.AGORA);
        });
        assertThat(recursoDe("SERVICO_CRIADO")).isEqualTo("servico:" + servico);
        assertThat(recursoDe("SERVICO_ALTERADO")).isEqualTo("servico:" + servico);
        assertThat(recursoDe("BARBEIRO_CRIADO")).isEqualTo("barbeiro:" + barbeiro);
        assertThat(recursoDe("BARBEIRO_ALTERADO")).isEqualTo("barbeiro:" + barbeiro);
        assertThat(recursoDe("JORNADA_SUBSTITUIDA")).isEqualTo("barbeiro:" + barbeiro);
        assertThat(recursoDe("FOLGA_CRIADA")).isEqualTo("folga:" + folga);
        assertThat(recursoDe("FOLGA_REMOVIDA")).isEqualTo("folga:" + folga);
        // A configuração é uma linha única com id numérico (1): não tem UUID para guardar.
        assertThat(jdbc.queryForObject("SELECT recurso FROM auditoria_admin WHERE acao = 'CONFIGURACAO_ALTERADA'",
                String.class)).isEqualTo("configuracao");
        assertThat(jdbc.queryForObject("SELECT recurso_id FROM auditoria_admin WHERE acao = 'CONFIGURACAO_ALTERADA'",
                UUID.class)).isNull();

        String detalhes = String.join(" ", jdbc.queryForList("SELECT coalesce(detalhe::text, '') FROM auditoria_admin", String.class));
        assertThat(detalhes).doesNotContain("Bruno").doesNotContain("Sobrenome").doesNotContain("Hidratação")
                .doesNotContain("Consulta").doesNotContain("médica").doesNotContain("@");
        assertThat(jdbc.queryForList("SELECT DISTINCT k FROM auditoria_admin, jsonb_object_keys(detalhe) AS k ORDER BY k",
                String.class)).isSubsetOf("campos", "intervalos", "barbeiroId");
        assertThat(jdbc.queryForObject("SELECT detalhe->>'campos' FROM auditoria_admin WHERE acao = 'SERVICO_ALTERADO'",
                String.class)).isEqualTo("precoCentavos");
        assertThat(jdbc.queryForObject("SELECT detalhe->>'campos' FROM auditoria_admin WHERE acao = 'BARBEIRO_ALTERADO'",
                String.class)).isEqualTo("nomeExibicao");
        assertThat(jdbc.queryForObject("SELECT detalhe->>'campos' FROM auditoria_admin WHERE acao = 'CONFIGURACAO_ALTERADA'",
                String.class)).isEqualTo("antecedenciaMinimaMin");
        assertThat(JsonPath.<String>read(jdbc.queryForObject(
                "SELECT detalhe::text FROM auditoria_admin WHERE acao = 'JORNADA_SUBSTITUIDA'", String.class), "$.intervalos"))
                .isEqualTo("2");
    }

    @Test
    @DisplayName("A69: ação recusada (409, 422, 404) e GET dos cadastros não gravam nenhuma linha na auditoria")
    void recusadaNaoAudita() throws Exception {
        Conta admin = admin();
        postJson("/admin/servicos", admin, """
                { "nome": "Corte", "duracaoMin": 30, "precoCentavos": 3500 }
                """).andExpect(status().isCreated());
        assertThat(linhasDeAuditoria()).isEqualTo(1);

        postJson("/admin/servicos", admin, """
                { "nome": "corte", "duracaoMin": 30, "precoCentavos": 3500 }
                """).andExpect(status().isConflict());
        put("/admin/barbeiros/" + UUID.randomUUID() + "/jornada", admin, "[]").andExpect(status().isNotFound());
        put("/admin/configuracao", admin, """
                { "antecedenciaMinimaMin": 60, "intervaloEntreAtendimentosMin": 0, "granularidadeMin": 30,
                  "horizonteDias": 30, "expiracaoPagamentoMin": 15, "version": 9 }
                """).andExpect(status().isConflict());
        get("/admin/servicos", admin).andExpect(status().isOk());
        get("/admin/barbeiros", admin).andExpect(status().isOk());
        get("/admin/configuracao", admin).andExpect(status().isOk());

        assertThat(linhasDeAuditoria()).isEqualTo(1);
    }

    private String recursoDe(String acao) {
        return jdbc.queryForObject("SELECT recurso || ':' || recurso_id FROM auditoria_admin WHERE acao = ?", String.class, acao);
    }
}
