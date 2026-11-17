package io.github.k3vinrich4rd.altashoras.admin.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/** A28 a A31: a devolução registrada à mão (no beta o estorno é feito fora do app, E13). */
class DevolucaoIT extends PainelBase {

    private UUID barbeiro;
    private UUID servico;

    private UUID cobrancaCom(String status) {
        if (barbeiro == null) {
            barbeiro = DadosDeTeste.barbeiro(jdbc, true);
            servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        }
        UUID reserva = reserva(barbeiro, servico, as(0, 0).plusMinutes(30L * jdbc.queryForObject("SELECT count(*) FROM reserva", Long.class)),
                "CANCELADO", null, cliente("Maria Souza Teste", "11988880001"), null);
        return cobranca(reserva, status, as(8, 0));
    }

    private String statusDaCobranca(UUID id) {
        return jdbc.queryForObject("SELECT status FROM cobranca WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("A28: devolver uma DEVOLUCAO_PENDENTE: 200 DEVOLVIDA com devolvidaEm às 09:30, devolvida_por é o ADMIN, some das pendências e a auditoria DEVOLUCAO_REGISTRADA é gravada")
    void devolver() throws Exception {
        Conta admin = conta("ADMIN", "BARBEIRO");
        UUID cobranca = cobrancaCom("DEVOLUCAO_PENDENTE");

        post("/admin/pagamentos/" + cobranca + "/devolucao", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEVOLVIDA"))
                .andExpect(jsonPath("$.devolvidaEm").value("2026-12-01T09:30:00-03:00"))
                .andExpect(jsonPath("$.valorCentavos").value(4500))
                .andExpect(jsonPath("$.pix").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT devolvida_por FROM cobranca WHERE id = ?", UUID.class, cobranca))
                .isEqualTo(admin.id());
        get("/admin/pagamentos", admin).andExpect(jsonPath("$.total").value(0));
        assertThat(jdbc.queryForList("SELECT acao FROM auditoria_admin WHERE recurso_id = ?", String.class, cobranca))
                .containsExactly("DEVOLUCAO_REGISTRADA");
    }

    @Test
    @DisplayName("A29: o pagamento tardio sem horário (PAGA_APOS_EXPIRACAO) também pode ser devolvido: 200 DEVOLVIDA")
    void devolverPagamentoTardio() throws Exception {
        UUID cobranca = cobrancaCom("PAGA_APOS_EXPIRACAO");

        post("/admin/pagamentos/" + cobranca + "/devolucao", conta("ADMIN", "BARBEIRO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEVOLVIDA"));

        assertThat(statusDaCobranca(cobranca)).isEqualTo("DEVOLVIDA");
    }

    @Test
    @DisplayName("A30: devolver PAGA, PENDENTE ou já DEVOLVIDA dá 422 TRANSICAO_INVALIDA e id inexistente dá 404; a data da devolução antiga não muda")
    void devolucaoInvalida() throws Exception {
        Conta admin = conta("ADMIN", "BARBEIRO");
        UUID paga = cobrancaCom("PAGA");
        UUID pendente = cobrancaCom("PENDENTE");
        UUID jaDevolvida = cobrancaCom("DEVOLVIDA");
        jdbc.update("UPDATE cobranca SET devolvida_em = ?, devolvida_por = ? WHERE id = ?", as(8, 15), admin.id(), jaDevolvida);

        for (UUID id : List.of(paga, pendente, jaDevolvida)) {
            post("/admin/pagamentos/" + id + "/devolucao", admin).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("TRANSICAO_INVALIDA"));
        }
        post("/admin/pagamentos/" + UUID.randomUUID() + "/devolucao", admin).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));

        assertThat(statusDaCobranca(paga)).isEqualTo("PAGA");
        assertThat(statusDaCobranca(pendente)).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject("SELECT devolvida_em FROM cobranca WHERE id = ?", OffsetDateTime.class, jaDevolvida)
                .toInstant()).isEqualTo(as(8, 15).toInstant());
        assertThat(linhasDeAuditoria()).isZero();
    }

    @Test
    @DisplayName("A31: dois POST de devolução ao mesmo tempo: exatamente um 200 e um 422, e uma só linha de auditoria")
    void doisCliques() throws Exception {
        Conta admin = conta("ADMIN", "BARBEIRO");
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 10; i++) {
                UUID cobranca = cobrancaCom("DEVOLUCAO_PENDENTE");
                long auditoriaAntes = linhasDeAuditoria();

                CountDownLatch largada = new CountDownLatch(1);
                List<Future<MockHttpServletResponse>> respostas = List.of(
                        pool.submit(() -> {
                            largada.await();
                            return post("/admin/pagamentos/" + cobranca + "/devolucao", admin).andReturn().getResponse();
                        }),
                        pool.submit(() -> {
                            largada.await();
                            return post("/admin/pagamentos/" + cobranca + "/devolucao", admin).andReturn().getResponse();
                        }));
                largada.countDown();

                List<Integer> codigos = List.of(respostas.get(0).get().getStatus(), respostas.get(1).get().getStatus());
                assertThat(codigos).containsExactlyInAnyOrder(200, 422);
                assertThat(statusDaCobranca(cobranca)).isEqualTo("DEVOLVIDA");
                assertThat(linhasDeAuditoria() - auditoriaAntes).isEqualTo(1);
            }
        }
    }
}
