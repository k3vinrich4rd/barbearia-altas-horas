package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.TOKEN_DO_WEBHOOK;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarPixComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDoWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.enviarWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.esperar;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.idDoProvedor;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaCobranca;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import io.github.k3vinrich4rd.altashoras.admin.cadastro.FeriasBase;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste.GatewayFalso;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;

/**
 * A14 (E7): a constraint do banco não enxerga folgas. Uma reserva que venceu e depois ficou dentro de uma folga ou de
 * férias criadas DEPOIS não pode voltar a CONFIRMADO quando o Pix chega atrasado: continua vencida e a cobrança vira
 * PAGA_APOS_EXPIRACAO, nas pendências do painel. Antes da Fatia 15 ela reconfirmava (Reservas.confirmarPagamento).
 * Mora no pacote da reserva por causa do {@link ExpiracaoJob}, que é interno da agenda.
 */
@Import(GatewayFalsoDeTeste.class)
class PagamentoTardioEmFolgaIT extends FeriasBase {

    @Autowired
    GatewayFalso gateway;

    @Autowired
    ExpiracaoJob job;

    @BeforeEach
    void gatewayLimpo() {
        gateway.reiniciar();
    }

    private ReservaGuest reservaComPixAs15DoDia22() throws Exception {
        String resposta = reservarComoVisitante(dez(22, 15, 0)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ReservaGuest reserva = new ReservaGuest(JsonPath.read(resposta, "$.id"), JsonPath.read(resposta, "$.tokenGuest"));
        cobrarPixComoVisitante(mvc, jdbc, reserva);
        return reserva;
    }

    private void pagarAtrasado(ReservaGuest reserva) throws Exception {
        gateway.marcarPago(idDoProvedor(jdbc, reserva.id()));
        enviarWebhook(mvc, TOKEN_DO_WEBHOOK,
                corpoDoWebhook("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idDoProvedor(jdbc, reserva.id())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A14: reserva EXPIRADO às 22/12 15:00 e depois férias que cobrem o horário; o pagamento tardio deixa a reserva EXPIRADO e a cobrança PAGA_APOS_EXPIRACAO, nas pendências do painel")
    void expiradaDentroDasFeriasNaoReconfirma() throws Exception {
        ReservaGuest reserva = reservaComPixAs15DoDia22();
        relogio.avancar(Duration.ofMinutes(4)); // o prazo é de 3 min (D121)
        job.executar();
        assertThat(statusDaReserva(UUID.fromString(reserva.id()))).isEqualTo("EXPIRADO");
        esperar(() -> "CANCELADA".equals(statusDaCobranca(jdbc, reserva.id())));

        admin = comTokenNovo(admin, "ADMIN", "BARBEIRO");
        // A reserva vencida não é conflito (E3): as férias entram sem manterReservas.
        criarFolga(corpo(FERIAS_INICIO, FERIAS_FIM, "FERIAS", null, null)).andExpect(status().isCreated());

        pagarAtrasado(reserva);

        assertThat(statusDaReserva(UUID.fromString(reserva.id()))).isEqualTo("EXPIRADO");
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PAGA_APOS_EXPIRACAO");
        String pendencias = get("/admin/pagamentos", admin).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(pendencias).contains(reserva.id()).contains("PAGA_APOS_EXPIRACAO");
    }

    @Test
    @DisplayName("A14: o mesmo com a reserva ainda AGUARDANDO_PAGAMENTO e o prazo vencido (o job não passou) e uma folga só das 15:00 às 16:00: continua sem CONFIRMADO e a cobrança vira PAGA_APOS_EXPIRACAO")
    void vencidaSemJobDentroDaFolgaNaoReconfirma() throws Exception {
        ReservaGuest reserva = reservaComPixAs15DoDia22();
        relogio.avancar(Duration.ofMinutes(4));
        admin = comTokenNovo(admin, "ADMIN", "BARBEIRO");
        criarFolga(corpo(dez(22, 15, 0), dez(22, 16, 0), "FOLGA", null, null)).andExpect(status().isCreated());

        pagarAtrasado(reserva);

        assertThat(statusDaReserva(UUID.fromString(reserva.id()))).isNotEqualTo("CONFIRMADO");
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PAGA_APOS_EXPIRACAO");
    }

    @Test
    @DisplayName("A14: controle: a mesma reserva vencida, com a folga só encostando (15:30 às 16:00, meio aberto R2), volta a CONFIRMADO como no R14")
    void folgaQueSoEncostaNaoImpede() throws Exception {
        ReservaGuest reserva = reservaComPixAs15DoDia22();
        relogio.avancar(Duration.ofMinutes(4));
        job.executar();
        admin = comTokenNovo(admin, "ADMIN", "BARBEIRO");
        criarFolga(corpo(dez(22, 15, 30), dez(22, 16, 0), "FOLGA", null, null)).andExpect(status().isCreated());

        pagarAtrasado(reserva);

        assertThat(statusDaReserva(UUID.fromString(reserva.id()))).isEqualTo("CONFIRMADO");
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PAGA");
    }
}
