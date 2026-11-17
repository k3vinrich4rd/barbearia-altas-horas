package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.as;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarPixComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.idDoProvedor;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaCobranca;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.ResultActions;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste.GatewayFalso;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;

/** Webhook atrasado: a leitura da reserva reconsulta o gateway, no máximo a cada 10 s por cobrança (E25). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, GatewayFalsoDeTeste.class})
class ConciliacaoIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GatewayFalso gateway;

    @Autowired
    RelogioAjustavel relogio;

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

    private ResultActions ler(ReservaGuest reserva) throws Exception {
        return mvc.perform(get("/agendamentos/guest/" + reserva.id()).header("X-Guest-Token", reserva.token()));
    }

    @Test
    @DisplayName("A41: cobrança paga no gateway e webhook atrasado → a 1ª leitura reconsulta e confirma; a 2ª, 5 s depois, não reconsulta; 11 s depois do pendente, reconsulta de novo")
    void leituraConcilia() throws Exception {
        // Paga no gateway e nenhum webhook chegou: a primeira leitura reconsulta e confirma.
        ReservaGuest paga = reservarComoVisitante(mvc, barbeiro, servico, as(10, 0));
        cobrarPixComoVisitante(mvc, jdbc, paga);
        gateway.marcarPago(idDoProvedor(jdbc, paga.id()));

        ler(paga).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        assertThat(gateway.chamadasDeConsultar.get()).isEqualTo(1);
        assertThat(statusDaCobranca(jdbc, paga.id())).isEqualTo("PAGA");

        relogio.avancar(Duration.ofSeconds(5));
        ler(paga).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        assertThat(gateway.chamadasDeConsultar.get()).as("a segunda leitura não reconsulta").isEqualTo(1);

        // Ainda pendente no gateway: a reconsulta respeita o intervalo de 10 s por cobrança.
        gateway.reiniciar();
        ReservaGuest pendente = reservarComoVisitante(mvc, barbeiro, servico, as(10, 30));
        cobrarPixComoVisitante(mvc, jdbc, pendente);
        String idNoGateway = idDoProvedor(jdbc, pendente.id());

        ler(pendente).andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"));
        assertThat(gateway.chamadasDeConsultar.get()).isEqualTo(1);

        relogio.avancar(Duration.ofSeconds(5));
        ler(pendente).andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"));
        assertThat(gateway.chamadasDeConsultar.get()).as("5 s depois: dentro do limite de 10 s").isEqualTo(1);

        gateway.marcarPago(idNoGateway);
        relogio.avancar(Duration.ofSeconds(6)); // 11 s desde a primeira reconsulta
        ler(pendente).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        assertThat(gateway.chamadasDeConsultar.get()).isEqualTo(2);
    }
}
