package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.TOKEN_DO_WEBHOOK;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.as;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarPixComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDoWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.enviarWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.esperar;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.idDoProvedor;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaCobranca;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaReserva;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste.GatewayFalso;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;

/**
 * Pagamento depois do prazo (R14, D35) e cobrança de reserva que expira (E24). Mora no pacote da reserva porque
 * chama o ExpiracaoJob, que é interno da agenda. O listener do evento ReservaExpirada roda depois do commit: o
 * teste espera o efeito com um laço curto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, GatewayFalsoDeTeste.class})
class PagamentoTardioIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GatewayFalso gateway;

    @Autowired
    RelogioAjustavel relogio;

    @Autowired
    ExpiracaoJob job;

    private UUID barbeiro;
    private UUID servico;

    @BeforeEach
    void dados() {
        gateway.reiniciar();
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, 30);
    }

    @AfterEach
    void voltarRelogio() {
        relogio.reiniciar();
    }

    private void pagarNoGateway(ReservaGuest reserva) throws Exception {
        gateway.marcarPago(idDoProvedor(jdbc, reserva.id()));
        enviarWebhook(mvc, TOKEN_DO_WEBHOOK,
                corpoDoWebhook("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idDoProvedor(jdbc, reserva.id())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A38: reserva expirada com o horário ainda livre e pagamento confirmado → EXPIRADO vira CONFIRMADO (R14)")
    void pagamentoTardioComHorarioLivre() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(10, 0));
        cobrarPixComoVisitante(mvc, jdbc, reserva);

        relogio.avancar(Duration.ofMinutes(16));
        job.executar();
        assertThat(statusDaReserva(jdbc, reserva.id())).isEqualTo("EXPIRADO");
        esperar(() -> "CANCELADA".equals(statusDaCobranca(jdbc, reserva.id())));

        pagarNoGateway(reserva);

        assertThat(statusDaReserva(jdbc, reserva.id())).isEqualTo("CONFIRMADO");
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PAGA");
    }

    @Test
    @DisplayName("A39: reserva expirada e o horário já é de outro cliente → continua EXPIRADO e a cobrança vira PAGA_APOS_EXPIRACAO")
    void pagamentoTardioComHorarioOcupado() throws Exception {
        ReservaGuest primeira = reservarComoVisitante(mvc, barbeiro, servico, as(10, 30));
        cobrarPixComoVisitante(mvc, jdbc, primeira);

        // O prazo da primeira passou; outro cliente pega o mesmo horário (a expiração preguiçosa libera a vencida).
        relogio.avancar(Duration.ofMinutes(16));
        ReservaGuest segunda = reservarComoVisitante(mvc, barbeiro, servico, as(10, 30));
        assertThat(statusDaReserva(jdbc, primeira.id())).isEqualTo("EXPIRADO");
        assertThat(statusDaReserva(jdbc, segunda.id())).isEqualTo("AGUARDANDO_PAGAMENTO");
        esperar(() -> "CANCELADA".equals(statusDaCobranca(jdbc, primeira.id())));

        pagarNoGateway(primeira);

        assertThat(statusDaReserva(jdbc, primeira.id())).isEqualTo("EXPIRADO");
        assertThat(statusDaCobranca(jdbc, primeira.id())).isEqualTo("PAGA_APOS_EXPIRACAO");
        assertThat(statusDaReserva(jdbc, segunda.id())).as("o horário continua do outro cliente").isEqualTo("AGUARDANDO_PAGAMENTO");
    }

    @Test
    @DisplayName("A40: reserva que expira com cobrança pendente → o job de expiração deixa a cobrança CANCELADA")
    void expiracaoCancelaACobranca() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(11, 0));
        cobrarPixComoVisitante(mvc, jdbc, reserva);
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PENDENTE");

        relogio.avancar(Duration.ofMinutes(16));
        job.executar();

        assertThat(statusDaReserva(jdbc, reserva.id())).isEqualTo("EXPIRADO");
        esperar(() -> "CANCELADA".equals(statusDaCobranca(jdbc, reserva.id())));
    }

    @Test
    @DisplayName("A39: pagamento de reserva CANCELADO (sem horário a confirmar) → cobrança PAGA_APOS_EXPIRACAO e webhook 200, sem 422")
    void pagamentoDeReservaCancelada() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(11, 30));
        cobrarPixComoVisitante(mvc, jdbc, reserva);
        jdbc.update("UPDATE reserva SET status = 'CANCELADO', expira_em = NULL WHERE id = ?::uuid", reserva.id());

        pagarNoGateway(reserva);

        assertThat(statusDaReserva(jdbc, reserva.id())).isEqualTo("CANCELADO");
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PAGA_APOS_EXPIRACAO");
    }
}
