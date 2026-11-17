package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.TOKEN_DO_WEBHOOK;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.as;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarPixComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDoWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.enviarWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.idDoProvedor;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaCobranca;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaReserva;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

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
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste.GatewayFalso;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;

/** O webhook do gateway: autenticado, idempotente e sempre reconsultando o gateway (E19, E20, R11, R12). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, GatewayFalsoDeTeste.class})
class WebhookIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GatewayFalso gateway;

    private ReservaGuest reserva;
    private String idNoGateway;

    @BeforeEach
    void cobrancaPixPendente() throws Exception {
        gateway.reiniciar();
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        reserva = reservarComoVisitante(mvc, barbeiro, servico, as(10, 0));
        cobrarPixComoVisitante(mvc, jdbc, reserva);
        idNoGateway = idDoProvedor(jdbc, reserva.id());
        gateway.chamadasDeConsultar.set(0);
    }

    private long eventos(String idEvento) {
        return jdbc.queryForObject("SELECT count(*) FROM evento_webhook WHERE id_evento_provedor = ?", Long.class, idEvento);
    }

    @Test
    @DisplayName("A33: webhook PAYMENT_RECEIVED com o token certo → 200, reconsulta o gateway, cobrança PAGA e reserva CONFIRMADO")
    void pagamentoConfirma() throws Exception {
        gateway.marcarPago(idNoGateway);
        String idEvento = "evt_" + UUID.randomUUID();

        enviarWebhook(mvc, TOKEN_DO_WEBHOOK, corpoDoWebhook(idEvento, "PAYMENT_RECEIVED", idNoGateway))
                .andExpect(status().isOk());

        assertThat(gateway.chamadasDeConsultar.get()).as("reconsultou o gateway").isEqualTo(1);
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PAGA");
        assertThat(statusDaReserva(jdbc, reserva.id())).isEqualTo("CONFIRMADO");
        Map<String, Object> evento = jdbc.queryForMap(
                "SELECT processado_em, resultado FROM evento_webhook WHERE id_evento_provedor = ?", idEvento);
        assertThat(evento.get("processado_em")).isNotNull();
        assertThat(evento.get("resultado")).isEqualTo("PROCESSADO");
        assertThat(jdbc.queryForObject("SELECT paga_em FROM cobranca WHERE reserva_id = ?::uuid", Object.class, reserva.id()))
                .isNotNull();
    }

    @Test
    @DisplayName("A34: o mesmo evento de novo → 200, nada muda e o gateway não é reconsultado")
    void eventoRepetido() throws Exception {
        gateway.marcarPago(idNoGateway);
        String corpo = corpoDoWebhook("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idNoGateway);
        enviarWebhook(mvc, TOKEN_DO_WEBHOOK, corpo).andExpect(status().isOk());
        assertThat(gateway.chamadasDeConsultar.get()).isEqualTo(1);

        enviarWebhook(mvc, TOKEN_DO_WEBHOOK, corpo).andExpect(status().isOk());

        assertThat(gateway.chamadasDeConsultar.get()).as("nenhuma nova reconsulta").isEqualTo(1);
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PAGA");
        assertThat(statusDaReserva(jdbc, reserva.id())).isEqualTo("CONFIRMADO");
    }

    @Test
    @DisplayName("A35: token errado ou ausente → 401 WEBHOOK_NAO_AUTENTICADO e nada gravado")
    void semTokenCerto() throws Exception {
        gateway.marcarPago(idNoGateway);
        String idEvento = "evt_" + UUID.randomUUID();
        String corpo = corpoDoWebhook(idEvento, "PAYMENT_RECEIVED", idNoGateway);

        enviarWebhook(mvc, "token-errado", corpo)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_NAO_AUTENTICADO"));
        enviarWebhook(mvc, null, corpo)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_NAO_AUTENTICADO"));

        assertThat(eventos(idEvento)).isZero();
        assertThat(gateway.chamadasDeConsultar.get()).isZero();
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PENDENTE");
    }

    @Test
    @DisplayName("A36: o corpo diz que pagou, mas o gateway diz pendente → a cobrança continua PENDENTE (o corpo não manda)")
    void corpoNaoManda() throws Exception {
        // O corpo do aviso traz "status": "RECEIVED"; o gateway (consultado de novo) não confirma.
        enviarWebhook(mvc, TOKEN_DO_WEBHOOK, corpoDoWebhook("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idNoGateway))
                .andExpect(status().isOk());

        assertThat(gateway.chamadasDeConsultar.get()).isEqualTo(1);
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PENDENTE");
        assertThat(statusDaReserva(jdbc, reserva.id())).isEqualTo("AGUARDANDO_PAGAMENTO");
    }

    @Test
    @DisplayName("A37: falha ao processar depois de gravar o evento → 5xx; o reenvio do mesmo evento processa")
    void falhaDepoisDeGravar() throws Exception {
        gateway.marcarPago(idNoGateway);
        gateway.falharProximasConsultas(1);
        String idEvento = "evt_" + UUID.randomUUID();
        String corpo = corpoDoWebhook(idEvento, "PAYMENT_RECEIVED", idNoGateway);

        enviarWebhook(mvc, TOKEN_DO_WEBHOOK, corpo).andExpect(status().is5xxServerError());

        assertThat(eventos(idEvento)).as("o evento ficou gravado").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT processado_em FROM evento_webhook WHERE id_evento_provedor = ?", Object.class,
                idEvento)).isNull();
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PENDENTE");

        enviarWebhook(mvc, TOKEN_DO_WEBHOOK, corpo).andExpect(status().isOk());

        assertThat(eventos(idEvento)).isEqualTo(1);
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PAGA");
        assertThat(statusDaReserva(jdbc, reserva.id())).isEqualTo("CONFIRMADO");
        assertThat(jdbc.queryForObject("SELECT processado_em FROM evento_webhook WHERE id_evento_provedor = ?", Object.class,
                idEvento)).isNotNull();
    }

    @Test
    @DisplayName("A35: sem token e com corpo inválido → 401 (autentica antes de ler o corpo), nada gravado")
    void autenticaAntesDeValidarOCorpo() throws Exception {
        enviarWebhook(mvc, null, "{ \"id\": \"\", \"event\": null }")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_NAO_AUTENTICADO"));
        enviarWebhook(mvc, "token-errado", "isto nem é json").andExpect(status().isUnauthorized());
        enviarWebhook(mvc, TOKEN_DO_WEBHOOK, "isto nem é json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JSON_MALFORMADO"));
        enviarWebhook(mvc, TOKEN_DO_WEBHOOK, "{ \"id\": \"\", \"event\": \"X\" }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_webhook WHERE id_evento_provedor = ''", Long.class)).isZero();
    }
}
