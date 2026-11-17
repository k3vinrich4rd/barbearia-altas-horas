package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.as;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarPixComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.idDoProvedor;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaCobranca;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaReserva;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

/** Eventos presos (E26): o job processa os gravados há mais de 10 min e, na 5ª falha seguida, manda para revisão manual. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, GatewayFalsoDeTeste.class})
class ReprocessamentoWebhookIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GatewayFalso gateway;

    @Autowired
    ReprocessamentoWebhookJob job;

    private UUID barbeiro;
    private UUID servico;

    @BeforeEach
    void dados() {
        gateway.reiniciar();
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, 30);
    }

    /** Evento gravado e nunca processado, recebido há {@code minutos} (pelo relógio fixo dos testes). */
    private String eventoPreso(String idNoGateway, long minutos) {
        String idEvento = "evt_" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO evento_webhook (provedor, id_evento_provedor, tipo, id_cobranca_provedor, recebido_em)
                VALUES ('SIMULADO', ?, 'PAYMENT_RECEIVED', ?, ?)
                """, idEvento, idNoGateway,
                OffsetDateTime.ofInstant(RelogioFixoDeTeste.AGORA.minus(Duration.ofMinutes(minutos)), ZoneOffset.UTC));
        return idEvento;
    }

    private Map<String, Object> evento(String idEvento) {
        return jdbc.queryForMap("SELECT processado_em, resultado, tentativas, ultimo_erro FROM evento_webhook "
                + "WHERE id_evento_provedor = ?", idEvento);
    }

    @Test
    @DisplayName("A42: evento gravado sem processar há 11 min → o job processa; o de 5 min espera; com 5 falhas seguidas vira REVISAO_MANUAL e o job para de tentar")
    void reprocessaEEsgotaTentativas() throws Exception {
        // 1) Preso há 11 min e o gateway confirma o pagamento: o job processa.
        ReservaGuest paga = reservarComoVisitante(mvc, barbeiro, servico, as(10, 0));
        cobrarPixComoVisitante(mvc, jdbc, paga);
        String idPago = idDoProvedor(jdbc, paga.id());
        gateway.marcarPago(idPago);
        String preso = eventoPreso(idPago, 11);

        // 2) Gravado há só 5 min: ainda dentro da espera, o job não encosta.
        ReservaGuest recente = reservarComoVisitante(mvc, barbeiro, servico, as(10, 30));
        cobrarPixComoVisitante(mvc, jdbc, recente);
        String idRecente = idDoProvedor(jdbc, recente.id());
        gateway.marcarPago(idRecente);
        String novo = eventoPreso(idRecente, 5);

        job.executar();

        assertThat(evento(preso).get("processado_em")).isNotNull();
        assertThat(evento(preso).get("resultado")).isEqualTo("PROCESSADO");
        assertThat(statusDaCobranca(jdbc, paga.id())).isEqualTo("PAGA");
        assertThat(statusDaReserva(jdbc, paga.id())).isEqualTo("CONFIRMADO");
        assertThat(evento(novo).get("processado_em")).isNull();
        assertThat(evento(novo).get("tentativas")).isEqualTo(0);
        assertThat(statusDaCobranca(jdbc, recente.id())).isEqualTo("PENDENTE");

        // 3) O gateway falha sempre: 5 rodadas gastam as 5 tentativas e o evento vai para revisão manual.
        ReservaGuest teimosa = reservarComoVisitante(mvc, barbeiro, servico, as(11, 0));
        cobrarPixComoVisitante(mvc, jdbc, teimosa);
        String idTeimosa = idDoProvedor(jdbc, teimosa.id());
        String esgotado = eventoPreso(idTeimosa, 30);
        gateway.falharSempreAoConsultar();
        for (int i = 1; i <= 4; i++) {
            job.executar();
            assertThat(evento(esgotado).get("tentativas")).isEqualTo(i);
            assertThat(evento(esgotado).get("resultado")).as("até a 4ª falha ainda tenta").isNull();
        }
        job.executar();

        Map<String, Object> revisao = evento(esgotado);
        assertThat(revisao.get("tentativas")).isEqualTo(5);
        assertThat(revisao.get("resultado")).isEqualTo("REVISAO_MANUAL");
        assertThat(revisao.get("processado_em")).isNull();
        assertThat((String) revisao.get("ultimo_erro")).isEqualTo("IllegalStateException");

        gateway.chamadasDeConsultar.set(0);
        job.executar();
        assertThat(evento(esgotado).get("tentativas")).as("em revisão manual o job não tenta mais").isEqualTo(5);
        assertThat(statusDaCobranca(jdbc, teimosa.id())).isEqualTo("PENDENTE");
    }
}
