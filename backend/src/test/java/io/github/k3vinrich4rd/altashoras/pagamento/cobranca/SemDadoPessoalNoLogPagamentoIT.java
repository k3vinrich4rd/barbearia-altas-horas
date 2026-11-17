package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.CPF;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.CPF_SO_DIGITOS;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.TOKEN_DO_WEBHOOK;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.as;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.chaveNova;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDaCobranca;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDoWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.enviarWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.idDoProvedor;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.versaoVigente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

/** R30: nada pessoal nem secreto no log, nem nos caminhos de erro. Captura a saída real do processo. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, GatewayFalsoDeTeste.class})
@ExtendWith(OutputCaptureExtension.class)
class SemDadoPessoalNoLogPagamentoIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GatewayFalso gateway;

    @BeforeEach
    void reiniciar() {
        gateway.reiniciar();
    }

    @Test
    @DisplayName("A43: reservar, cobrar com CPF, erros, webhook e conciliação não deixam CPF, e-mail, WhatsApp nem token no log")
    void nadaNoLog(CapturedOutput saida) throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(10, 0));
        int versao = versaoVigente(jdbc);
        String chave = chaveNova();

        // Fluxo feliz, a repetição e os caminhos de erro (CPF inválido, token errado, gateway fora do ar).
        cobrarComoVisitante(mvc, reserva, chave, corpoDaCobranca(reserva.id(), "PIX", CPF, versao));
        cobrarComoVisitante(mvc, reserva, chave, corpoDaCobranca(reserva.id(), "PIX", CPF, versao));
        cobrarComoVisitante(mvc, reserva, chaveNova(), corpoDaCobranca(reserva.id(), "PIX", "529.982.247-24", versao));
        cobrarComoVisitante(mvc, new ReservaGuest(reserva.id(), "g1_errado"), chaveNova(),
                corpoDaCobranca(reserva.id(), "PIX", CPF, versao));
        ReservaGuest fora = reservarComoVisitante(mvc, barbeiro, servico, as(10, 30));
        gateway.ficarIndisponivel(false);
        cobrarComoVisitante(mvc, fora, chaveNova(), corpoDaCobranca(fora.id(), "PIX", CPF, versao));
        gateway.voltar();

        // Webhook com token errado, com falha ao processar (500 com pilha no log) e com sucesso; depois a conciliação.
        String idNoGateway = idDoProvedor(jdbc, reserva.id());
        enviarWebhook(mvc, "token-errado-123", corpoDoWebhook("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idNoGateway));
        gateway.marcarPago(idNoGateway);
        gateway.falharProximasConsultas(1);
        String evento = corpoDoWebhook("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idNoGateway);
        enviarWebhook(mvc, TOKEN_DO_WEBHOOK, evento);
        enviarWebhook(mvc, TOKEN_DO_WEBHOOK, evento);
        mvc.perform(get("/agendamentos/guest/" + reserva.id()).header("X-Guest-Token", reserva.token()));

        assertThat(saida.getAll().toLowerCase())
                .doesNotContain(CPF_SO_DIGITOS).doesNotContain(CPF).doesNotContain("529.982")
                .doesNotContain("visitante@exemplo.test").doesNotContain("visitante teste")
                .doesNotContain("11900000002").doesNotContain("90000-0002")
                .doesNotContain(reserva.token().toLowerCase()).doesNotContain("g1_errado")
                .doesNotContain(TOKEN_DO_WEBHOOK).doesNotContain("token-errado-123")
                .doesNotContain(chave);
    }
}
