package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.TOKEN_DO_WEBHOOK;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarPixComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDoWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.enviarWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.idDoProvedor;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaCobranca;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste.GatewayFalso;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;

/**
 * A11 a A13: com {@code manterReservas: true} o período é gravado, as reservas que já estavam nele continuam como estão
 * (nada é cancelado sozinho, E5), ninguém marca horário novo dentro dele e a reserva mantida ainda pode ser paga (E6).
 */
@Import(GatewayFalsoDeTeste.class)
class ManterReservasIT extends FeriasBase {

    @Autowired
    GatewayFalso gateway;

    @BeforeEach
    void gatewayLimpo() {
        gateway.reiniciar();
    }

    private String feriasMantendo() {
        return corpo(FERIAS_INICIO, FERIAS_FIM, "FERIAS", "Viagem com a família", true);
    }

    @Test
    @DisplayName("A11: POST de férias com manterReservas true por cima das reservas do A8 dá 201 com as 2 em reservasMantidas; as 2 continuam com o mesmo status; a disponibilidade de 22/12 não tem horário; a auditoria guarda a quantidade (2) e os ids, sem nome nem motivo")
    void gravaEMantem() throws Exception {
        UUID[] reservas = reservasDoA8();

        criarFolga(feriasMantendo()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("FERIAS"))
                .andExpect(jsonPath("$.reservasMantidas.length()").value(2))
                .andExpect(jsonPath("$.reservasMantidas[0].reservaId").value(reservas[0].toString()))
                .andExpect(jsonPath("$.reservasMantidas[0].status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.reservasMantidas[1].reservaId").value(reservas[1].toString()))
                .andExpect(jsonPath("$.reservasMantidas[1].status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.manterReservas").doesNotExist());

        assertThat(statusDaReserva(reservas[0])).isEqualTo("CONFIRMADO");
        assertThat(statusDaReserva(reservas[1])).isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(horasDe(LocalDate.of(2026, 12, 22))).isEmpty();
        assertThat(auditoria("FOLGA_CRIADA")).singleElement().asString()
                .contains("\"reservasMantidas\": \"2\"", reservas[0].toString(), reservas[1].toString())
                .doesNotContain("Cliente Exemplo", "Visitante Dois", "Viagem");
    }

    @Test
    @DisplayName("A12: depois do A11, uma reserva nova no período (cliente, visitante ou manual, 22/12 16:00) dá 422 FORA_DO_EXPEDIENTE")
    void ninguemMarcaDepois() throws Exception {
        reservasDoA8();
        criarFolga(feriasMantendo()).andExpect(status().isCreated());

        reservar(conta("CLIENTE"), servico, eduardo, dez(22, 16, 0)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FORA_DO_EXPEDIENTE"));
        reservarComoVisitante(dez(22, 16, 0)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FORA_DO_EXPEDIENTE"));
        reservaManual(dez(22, 16, 0)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FORA_DO_EXPEDIENTE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", Long.class, eduardo))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("A13: a reserva AGUARDANDO_PAGAMENTO mantida pelo A11 e paga dentro do prazo (webhook) vira CONFIRMADO (E6)")
    void mantidaPodeSerPaga() throws Exception {
        String resposta = reservarComoVisitante(dez(22, 15, 30)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ReservaGuest reserva = new ReservaGuest(JsonPath.read(resposta, "$.id"), JsonPath.read(resposta, "$.tokenGuest"));
        cobrarPixComoVisitante(mvc, jdbc, reserva);
        criarFolga(feriasMantendo()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservasMantidas[0].reservaId").value(reserva.id()));

        gateway.marcarPago(idDoProvedor(jdbc, reserva.id()));
        enviarWebhook(mvc, TOKEN_DO_WEBHOOK,
                corpoDoWebhook("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idDoProvedor(jdbc, reserva.id())))
                .andExpect(status().isOk());

        assertThat(statusDaReserva(UUID.fromString(reserva.id()))).isEqualTo("CONFIRMADO");
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PAGA");
    }
}
