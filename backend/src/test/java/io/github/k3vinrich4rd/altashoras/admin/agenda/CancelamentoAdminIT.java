package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.admin.PainelBase;
import io.github.k3vinrich4rd.altashoras.agenda.ReservaCancelada;

/** A19 a A23: o cancelamento pela barbearia, o efeito no pagamento por evento e o que o cliente enxerga. */
class CancelamentoAdminIT extends PainelBase {

    @Autowired
    IncompleteEventPublications incompletas;

    @Autowired
    ApplicationEventPublisher publicador;

    @Autowired
    PlatformTransactionManager gerenciador;

    private List<String> horarios(Massa m) throws Exception {
        String corpo = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/disponibilidade")
                        .param("servicoId", m.servico().toString()).param("data", "2026-12-01")
                        .param("barbeiroId", m.barbeiroA().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.barbeiros[0].horarios");
    }

    @Test
    @DisplayName("A19: o ADMIN cancela a R1 paga com motivo: CANCELADO, quem e quando gravados, cobrança vira DEVOLUCAO_PENDENTE e aparece nas pendências, e o horário das 09:00 volta")
    void cancelarReservaPaga() throws Exception {
        relogio.reiniciar(); // 17/11 08:00: a antecedência mínima deixa o horário das 09:00 de 01/12 aparecer
        Massa m = massa();
        assertThat(horarios(m)).noneMatch(h -> h.contains("T09:00:00"));
        long v = versaoDa(m.r1());

        patchStatus(m.r1(), m.admin(), corpoDeCancelamento("Barbeiro doente", v)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"))
                .andExpect(jsonPath("$.motivoCancelamento").value("Barbeiro doente"))
                .andExpect(jsonPath("$.version").value(v + 1));

        assertThat(jdbc.queryForObject("SELECT cancelado_por FROM reserva WHERE id = ?", UUID.class, m.r1()))
                .isEqualTo(m.admin().id());
        assertThat(jdbc.queryForObject("SELECT cancelado_em FROM reserva WHERE id = ?", OffsetDateTime.class, m.r1())
                .toInstant()).isEqualTo(relogio.instant());
        esperar(() -> "DEVOLUCAO_PENDENTE".equals(statusDaCobrancaDaReserva(m.r1())));
        get("/admin/pagamentos", m.admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].reservaId").value(m.r1().toString()))
                .andExpect(jsonPath("$.itens[0].status").value("DEVOLUCAO_PENDENTE"));
        assertThat(horarios(m)).anyMatch(h -> h.contains("T09:00:00"));
    }

    @Test
    @DisplayName("A20: cancelar reserva aguardando pagamento: a cobrança PENDENTE vira CANCELADA (nunca devolução) e a reserva sem cobrança só cancela")
    void cancelarReservaNaoPaga() throws Exception {
        Massa m = massa();
        cobranca(m.r2(), "PENDENTE", as(9, 25));
        UUID semCobranca = reserva(m.barbeiroA(), m.servico(), as(16, 0), "AGUARDANDO_PAGAMENTO", as(9, 45).toInstant(),
                m.clienteDaR1(), null);

        patchStatus(m.r2(), m.admin(), corpoDeCancelamento("Falta de energia", versaoDa(m.r2())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADO"));
        patchStatus(semCobranca, m.admin(), corpoDeCancelamento("Falta de energia", versaoDa(semCobranca)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADO"));

        esperar(() -> "CANCELADA".equals(statusDaCobrancaDaReserva(m.r2())));
        assertThat(statusDaReserva(m.r2())).isEqualTo("CANCELADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cobranca WHERE reserva_id = ?", Long.class, semCobranca))
                .isZero();
    }

    @Test
    @DisplayName("A21: cancelar sem motivo ou com motivo só de espaços dá 422 MOTIVO_OBRIGATORIO e com 301 caracteres dá 400; a R1 e a cobrança ficam intactas")
    void motivo() throws Exception {
        Massa m = massa();
        long v = versaoDa(m.r1());

        patchStatus(m.r1(), m.admin(), corpoDeStatus("CANCELADO", v)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MOTIVO_OBRIGATORIO"));
        patchStatus(m.r1(), m.admin(), corpoDeCancelamento("   ", v)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MOTIVO_OBRIGATORIO"));
        patchStatus(m.r1(), m.admin(), corpoDeCancelamento("x".repeat(301), v)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));

        assertThat(statusDaReserva(m.r1())).isEqualTo("CONFIRMADO");
        assertThat(versaoDa(m.r1())).isEqualTo(v);
        assertThat(statusDaCobrancaDaReserva(m.r1())).isEqualTo("PAGA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_publication", Long.class)).isZero();
    }

    @Test
    @DisplayName("A22: com o listener do pagamento falhando na primeira entrega, o evento fica no outbox só com o id e, depois do reenvio, a cobrança vira DEVOLUCAO_PENDENTE; reenviar de novo não muda nada")
    void eventoReenviado() throws Exception {
        Massa m = massa();
        falharNasCobrancas();
        try {
            patchStatus(m.r1(), m.admin(), corpoDeCancelamento("Cliente João pediu", versaoDa(m.r1())))
                    .andExpect(status().isOk());
            esperarEntregaFalhar();

            List<String> eventos = jdbc.queryForList(
                    "SELECT serialized_event FROM event_publication WHERE event_type LIKE '%ReservaCancelada'", String.class);
            assertThat(eventos).hasSize(1);
            assertThat(eventos.getFirst()).isEqualTo("{\"reservaId\":\"" + m.r1() + "\"}");
            assertThat(statusDaCobrancaDaReserva(m.r1())).isEqualTo("PAGA");
        } finally {
            consertarAsCobrancas();
        }

        incompletas.resubmitIncompletePublications(publicacao -> true);
        esperar(() -> "DEVOLUCAO_PENDENTE".equals(statusDaCobrancaDaReserva(m.r1())));
        esperarOutboxVazio();
        long versaoDaCobranca = jdbc.queryForObject("SELECT version FROM cobranca WHERE reserva_id = ?", Long.class, m.r1());

        // Reenvio de novo (a API reiniciando) e entrega repetida do mesmo evento: o status de origem está no WHERE.
        incompletas.resubmitIncompletePublications(publicacao -> true);
        new TransactionTemplate(gerenciador).executeWithoutResult(s -> publicador.publishEvent(new ReservaCancelada(m.r1())));
        esperarOutboxVazio();
        assertThat(statusDaCobrancaDaReserva(m.r1())).isEqualTo("DEVOLUCAO_PENDENTE");
        assertThat(jdbc.queryForObject("SELECT version FROM cobranca WHERE reserva_id = ?", Long.class, m.r1()))
                .isEqualTo(versaoDaCobranca);
    }

    @Test
    @DisplayName("A23: o cliente dono vê CANCELADO e o motivo em /agendamentos/{id} e /me/agendamentos; o cliente B recebe 404")
    void clienteVeOCancelamento() throws Exception {
        Massa m = massa();
        patchStatus(m.r1(), m.admin(), corpoDeCancelamento("Barbeiro doente", versaoDa(m.r1()))).andExpect(status().isOk());
        String dono = emissor.emitir(m.clienteDaR1(), List.of("CLIENTE"), 0, false).valor();
        String outro = emissor.emitir(cliente("Cliente B Teste", "11988880077"), List.of("CLIENTE"), 0, false).valor();

        get("/agendamentos/" + m.r1(), dono).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"))
                .andExpect(jsonPath("$.motivoCancelamento").value("Barbeiro doente"));
        get("/me/agendamentos", dono).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.id == '" + m.r1() + "')].status").value("CANCELADO"))
                .andExpect(jsonPath("$.itens[?(@.id == '" + m.r1() + "')].motivoCancelamento").value("Barbeiro doente"));
        get("/agendamentos/" + m.r1(), outro).andExpect(status().isNotFound());
    }
}
