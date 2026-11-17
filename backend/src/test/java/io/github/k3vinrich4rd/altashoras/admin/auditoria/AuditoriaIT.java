package io.github.k3vinrich4rd.altashoras.admin.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/** A34 a A38: cada ação bem-sucedida do painel deixa uma linha, na mesma transação, sem dado pessoal no detalhe. */
class AuditoriaIT extends PainelBase {

    private static final String IP = "203.0.113.7";

    /** As cinco ações da 5A, feitas pelo ADMIN, com o IP de teste. Devolve os ids dos recursos na ordem das ações. */
    private record Acoes(Conta admin, UUID concluida, UUID faltou, UUID cancelada, UUID cobranca, UUID evento) {
    }

    private Acoes fazerAsCincoAcoes(String motivo) throws Exception {
        Conta admin = conta("ADMIN", "BARBEIRO");
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID cliente = cliente("Maria Souza Teste", "11988880001");
        UUID concluida = reserva(barbeiro, servico, as(7, 0), "CONFIRMADO", null, cliente, null);
        UUID faltou = reserva(barbeiro, servico, as(7, 30), "CONFIRMADO", null, cliente, null);
        UUID cancelada = reserva(barbeiro, servico, as(8, 0), "CONFIRMADO", null, cliente, null);
        cobranca(cancelada, "PAGA", as(6, 0));
        UUID paraDevolver = cobranca(reserva(barbeiro, servico, as(8, 30), "CANCELADO", null, cliente, null),
                "DEVOLUCAO_PENDENTE", as(6, 0));
        UUID evento = jdbc.queryForObject("""
                INSERT INTO evento_webhook (provedor, id_evento_provedor, tipo, recebido_em, resultado, tentativas, ultimo_erro)
                VALUES ('SIMULADO', ?, 'PAYMENT_RECEIVED', ?, 'REVISAO_MANUAL', 5, 'SEM_COBRANCA') RETURNING id
                """, UUID.class, "evt_" + UUID.randomUUID(), as(6, 0));

        mudarStatus(concluida, admin, corpoDeStatus("CONCLUIDO", 0));
        mudarStatus(faltou, admin, corpoDeStatus("NAO_COMPARECEU", 0));
        mudarStatus(cancelada, admin, corpoDeCancelamento(motivo, 0));
        mvc.perform(MockMvcRequestBuilders.post("/admin/pagamentos/" + paraDevolver + "/devolucao")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()).with(req -> {
                    req.setRemoteAddr(IP);
                    return req;
                })).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.post("/admin/pagamentos/eventos/" + evento + "/revisao")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()).with(req -> {
                    req.setRemoteAddr(IP);
                    return req;
                })).andExpect(status().isOk());
        return new Acoes(admin, concluida, faltou, cancelada, paraDevolver, evento);
    }

    private void mudarStatus(UUID reserva, Conta quem, String corpo) throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/admin/agendamentos/" + reserva + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + quem.token()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo).with(req -> {
                    req.setRemoteAddr(IP);
                    return req;
                })).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A34: concluir, faltar, cancelar, devolver e revisar deixam uma linha cada, com ator, ação, recurso, id, a hora do relógio e o IP da requisição")
    void umaLinhaPorAcao() throws Exception {
        Acoes a = fazerAsCincoAcoes("Barbeiro doente");

        List<Map<String, Object>> linhas = jdbc.queryForList(
                "SELECT ator_id, acao, recurso, recurso_id, ocorrido_em, ip FROM auditoria_admin ORDER BY ocorrido_em, acao");
        assertThat(linhas).hasSize(5);
        assertThat(linhas).allSatisfy(linha -> {
            assertThat(linha.get("ator_id")).isEqualTo(a.admin().id());
            assertThat(((java.sql.Timestamp) linha.get("ocorrido_em")).toInstant()).isEqualTo(as(9, 30).toInstant());
            assertThat(linha.get("ip")).isEqualTo(IP);
        });
        assertThat(jdbc.queryForObject("SELECT recurso_id FROM auditoria_admin WHERE acao = 'AGENDAMENTO_CONCLUIDO'",
                UUID.class)).isEqualTo(a.concluida());
        assertThat(jdbc.queryForObject("SELECT recurso_id FROM auditoria_admin WHERE acao = 'AGENDAMENTO_NAO_COMPARECEU'",
                UUID.class)).isEqualTo(a.faltou());
        assertThat(jdbc.queryForObject("SELECT recurso_id FROM auditoria_admin WHERE acao = 'AGENDAMENTO_CANCELADO'",
                UUID.class)).isEqualTo(a.cancelada());
        assertThat(jdbc.queryForObject(
                "SELECT recurso || ':' || recurso_id FROM auditoria_admin WHERE acao = 'DEVOLUCAO_REGISTRADA'", String.class))
                .isEqualTo("cobranca:" + a.cobranca());
        assertThat(jdbc.queryForObject(
                "SELECT recurso || ':' || recurso_id FROM auditoria_admin WHERE acao = 'EVENTO_REVISADO'", String.class))
                .isEqualTo("evento_webhook:" + a.evento());
        assertThat(jdbc.queryForList("SELECT DISTINCT recurso FROM auditoria_admin ORDER BY recurso", String.class))
                .containsExactly("agendamento", "cobranca", "evento_webhook");
    }

    @Test
    @DisplayName("A35: o detalhe da auditoria tem só campos técnicos (de, para, ids): nenhum motivo, nome, WhatsApp, e-mail ou token")
    void semDadoPessoalNoDetalhe() throws Exception {
        fazerAsCincoAcoes("Cliente João pediu");

        List<String> detalhes = jdbc.queryForList("SELECT detalhe::text FROM auditoria_admin", String.class);
        assertThat(detalhes).hasSize(5);
        String todos = String.join(" ", detalhes);
        assertThat(todos).doesNotContain("João").doesNotContain("pediu").doesNotContain("Cliente")
                .doesNotContain("Maria").doesNotContain("Souza").doesNotContain("11988880001")
                .doesNotContain("@").doesNotContain("exemplo.test").doesNotContainIgnoringCase("token")
                .doesNotContainIgnoringCase("whatsapp").doesNotContainIgnoringCase("email");
        assertThat(jdbc.queryForList("SELECT DISTINCT k FROM auditoria_admin, jsonb_object_keys(detalhe) AS k ORDER BY k",
                String.class)).isSubsetOf("de", "para", "reservaId", "tipo");
        assertThat(jdbc.queryForObject("""
                SELECT detalhe->>'de' || '>' || (detalhe->>'para') FROM auditoria_admin WHERE acao = 'AGENDAMENTO_CANCELADO'
                """, String.class)).isEqualTo("CONFIRMADO>CANCELADO");
    }

    @Test
    @DisplayName("A36: se a gravação da auditoria falha, a requisição falha e a reserva continua CONFIRMADO com a cobrança PAGA (mesma transação)")
    void falhaNaAuditoriaDesfazAAcao() throws Exception {
        Massa m = massa();
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION falha_do_teste() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'falha injetada pelo teste'; END $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER falha_auditoria BEFORE INSERT ON auditoria_admin FOR EACH ROW EXECUTE FUNCTION falha_do_teste()");
        try {
            patchStatus(m.r1(), m.admin(), corpoDeCancelamento("Barbeiro doente", versaoDa(m.r1())))
                    .andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS falha_auditoria ON auditoria_admin");
        }

        assertThat(statusDaReserva(m.r1())).isEqualTo("CONFIRMADO");
        assertThat(versaoDa(m.r1())).isZero();
        assertThat(statusDaCobrancaDaReserva(m.r1())).isEqualTo("PAGA");
        assertThat(jdbc.queryForObject("SELECT cancelado_em FROM reserva WHERE id = ?", OffsetDateTime.class, m.r1())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_publication", Long.class)).isZero();
        assertThat(linhasDeAuditoria()).isZero();
    }

    @Test
    @DisplayName("A37: ações recusadas (403, 404, 409, 422) e GETs do painel não gravam nenhuma linha de auditoria")
    void recusadasNaoAuditam() throws Exception {
        Massa m = massa();
        UUID paga = cobranca(reserva(m.barbeiroA(), m.servico(), as(0, 0), "CONFIRMADO", null, m.clienteDaR1(), null), "PAGA",
                as(6, 0));

        patchStatus(m.r1(), m.contaA(), corpoDeCancelamento("Barbeiro doente", 0)).andExpect(status().isForbidden());
        patchStatus(UUID.randomUUID(), m.admin(), corpoDeStatus("CONCLUIDO", 0)).andExpect(status().isNotFound());
        patchStatus(m.r1(), m.admin(), corpoDeStatus("CONCLUIDO", 99)).andExpect(status().isConflict());
        patchStatus(m.r5(), m.admin(), corpoDeStatus("CONCLUIDO", 0)).andExpect(status().isUnprocessableEntity());
        post("/admin/pagamentos/" + paga + "/devolucao", m.admin()).andExpect(status().isUnprocessableEntity());
        post("/admin/pagamentos/eventos/" + UUID.randomUUID() + "/revisao", m.admin()).andExpect(status().isNotFound());
        get("/admin/agenda?data=2026-12-01", m.admin()).andExpect(status().isOk());
        get("/admin/pagamentos", m.admin()).andExpect(status().isOk());
        get("/admin/pagamentos/eventos", m.admin()).andExpect(status().isOk());
        get("/admin/auditoria", m.admin()).andExpect(status().isOk());

        assertThat(linhasDeAuditoria()).isZero();
    }

    @Test
    @DisplayName("A38: a auditoria lista as ações mais recentes primeiro, em página, sem o IP; tamanho 51 dá 400 e o BARBEIRO recebe 403")
    void listar() throws Exception {
        Conta admin = conta("ADMIN", "BARBEIRO");
        for (int i = 0; i < 30; i++) {
            jdbc.update("""
                    INSERT INTO auditoria_admin (ator_id, acao, recurso, recurso_id, detalhe, ip, ocorrido_em)
                    VALUES (?, ?, 'agendamento', gen_random_uuid(), '{"de":"CONFIRMADO","para":"CONCLUIDO"}'::jsonb,
                            '10.0.0.1', ?)
                    """, admin.id(), "TESTE_" + i, as(0, 0).plusMinutes(i));
        }

        String corpo = get("/admin/auditoria", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(20))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.total").value(30))
                .andExpect(jsonPath("$.itens[0].acao").value("TESTE_29"))
                .andExpect(jsonPath("$.itens[19].acao").value("TESTE_10"))
                .andExpect(jsonPath("$.itens[0].atorId").value(admin.id().toString()))
                .andExpect(jsonPath("$.itens[0].detalhe.de").value("CONFIRMADO"))
                .andExpect(jsonPath("$.itens[0].ocorridoEm").value("2026-12-01T00:29:00-03:00"))
                .andExpect(jsonPath("$.itens[0].ip").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("10.0.0.1");

        get("/admin/auditoria?pagina=1&tamanho=20", admin).andExpect(jsonPath("$.itens.length()").value(10))
                .andExpect(jsonPath("$.itens[9].acao").value("TESTE_0"));
        get("/admin/auditoria?tamanho=51", admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        get("/admin/auditoria", conta("BARBEIRO")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
    }
}
