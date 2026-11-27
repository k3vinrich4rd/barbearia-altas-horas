package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.as;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarPixComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.esperar;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaCobranca;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
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
 * A6 da Fatia 14A (E6): a agenda publica o PrazoDaReservaProrrogado e o pagamento, que só escuta, atualiza o prazo da cobrança
 * pendente. O Pix já gerado é o mesmo e o gateway não é chamado (no Asaas o vencimento é por dia).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, GatewayFalsoDeTeste.class})
class ProrrogacaoCobrancaIT {

    private static Instant em(int hora, int minuto, int segundo) {
        return ZonedDateTime.of(2026, 12, 1, hora, minuto, segundo, 0, RelogioFixoDeTeste.FUSO).toInstant();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelogioAjustavel relogio;

    @Autowired
    GatewayFalso gateway;

    @BeforeEach
    void cenario() {
        gateway.reiniciar();
        relogio.definir(em(10, 0, 0));
    }

    @AfterEach
    void devolverRelogio() {
        relogio.reiniciar();
    }

    @Test
    @DisplayName("A6: depois da prorrogação o listener do pagamento deixa a cobrança PENDENTE com expira_em 10:05:00 (120 s, Fatia 15) e o mesmo pix_copia_e_cola; o gateway não é chamado")
    void cobrancaAcompanhaOPrazo() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(15, 0));
        cobrarPixComoVisitante(mvc, jdbc, reserva);
        String pixAntes = jdbc.queryForObject("SELECT pix_copia_e_cola FROM cobranca WHERE reserva_id = ?::uuid",
                String.class, reserva.id());
        assertThat(pixAntes).isNotBlank();
        assertThat(expiraEmDaCobranca(reserva.id())).isEqualTo(em(10, 3, 0));
        int criar = gateway.chamadasDeCriar.get();
        int buscar = gateway.chamadasDeBuscar.get();
        int consultar = gateway.chamadasDeConsultar.get();

        relogio.definir(em(10, 2, 30));
        mvc.perform(post("/agendamentos/guest/" + reserva.id() + "/prorrogacao").header("X-Guest-Token", reserva.token()))
                .andExpect(status().isCreated());

        esperar(() -> em(10, 5, 0).equals(expiraEmDaCobranca(reserva.id())));
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject("SELECT pix_copia_e_cola FROM cobranca WHERE reserva_id = ?::uuid", String.class,
                reserva.id())).isEqualTo(pixAntes);
        assertThat(gateway.chamadasDeCriar.get()).as("nenhum Pix novo").isEqualTo(criar);
        assertThat(gateway.chamadasDeBuscar.get()).isEqualTo(buscar);
        assertThat(gateway.chamadasDeConsultar.get()).isEqualTo(consultar);
    }

    private Instant expiraEmDaCobranca(String reservaId) {
        return jdbc.queryForObject("SELECT expira_em FROM cobranca WHERE reserva_id = ?::uuid", OffsetDateTime.class,
                reservaId).toInstant();
    }
}
