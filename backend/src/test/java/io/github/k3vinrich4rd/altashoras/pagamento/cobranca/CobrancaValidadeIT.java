package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.as;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarPixComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste.GatewayFalso;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;

/**
 * A41 da Fatia 15 (E23, D191): o Pix nasce no gateway valendo pelo prazo máximo que a reserva pode alcançar, calculado das
 * propriedades ({@code expiracaoPagamentoMin} + {@code prorrogacao-maximo} × {@code prorrogacao-pagamento}), sem número
 * fixo no código. Reserva do visitante criada às 10:00:00 de ter 01/12/2026, prazo de 3 minutos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, GatewayFalsoDeTeste.class})
class CobrancaValidadeIT {

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

    /** Cria a reserva do visitante às 10:00:00 e a cobrança Pix; devolve a reserva. */
    ReservaGuest reservaComPix() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(15, 0));
        cobrarPixComoVisitante(mvc, jdbc, reserva);
        return reserva;
    }

    String copiaECola(String reservaId) {
        return jdbc.queryForObject("SELECT pix_copia_e_cola FROM cobranca WHERE reserva_id = ?::uuid", String.class,
                reservaId);
    }

    @Test
    @DisplayName("A41: com expiracaoPagamentoMin 3, prorrogacao-pagamento PT2M e prorrogacao-maximo 10, o gateway recebe a validade de 23 min depois do início do prazo (10:23:00); depois de um pedido de mais tempo o copia e cola é o mesmo e o gateway não é chamado de novo")
    void validadeDoPrazoMaximo() throws Exception {
        ReservaGuest reserva = reservaComPix();
        assertThat(gateway.ultimoPedido.expiraEm()).isEqualTo(em(10, 23, 0));
        String pix = copiaECola(reserva.id());
        int criar = gateway.chamadasDeCriar.get();

        relogio.definir(em(10, 2, 30));
        mvc.perform(post("/agendamentos/guest/" + reserva.id() + "/prorrogacao").header("X-Guest-Token", reserva.token()))
                .andExpect(status().isCreated());

        assertThat(copiaECola(reserva.id())).isEqualTo(pix);
        assertThat(gateway.chamadasDeCriar.get()).as("nenhum Pix novo").isEqualTo(criar);
    }

    @Nested
    @TestPropertySource(properties = {"app.agenda.prorrogacao-pagamento=PT1M", "app.agenda.prorrogacao-maximo=5"})
    class ComOutrasPropriedades {

        @Test
        @DisplayName("A41: com prorrogacao-pagamento PT1M e prorrogacao-maximo 5 a validade muda junto: 8 min depois do início do prazo (10:08:00)")
        void validadeSegueAsPropriedades() throws Exception {
            reservaComPix();
            assertThat(gateway.ultimoPedido.expiraEm()).isEqualTo(em(10, 8, 0));
        }
    }
}
