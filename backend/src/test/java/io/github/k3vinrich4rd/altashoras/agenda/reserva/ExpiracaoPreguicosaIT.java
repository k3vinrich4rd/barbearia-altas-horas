package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.as;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

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

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class ExpiracaoPreguicosaIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("A8: pendente vencida no mesmo horário, que o job ainda não expirou → 201; ela vira EXPIRADO junto")
    void vencidaSaiDoCaminho() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        // Venceu um minuto atrás e continua AGUARDANDO_PAGAMENTO: para a constraint, ainda ocupa o horário.
        DadosDeTeste.reserva(jdbc, barbeiro, servico, as(10, 0), "AGUARDANDO_PAGAMENTO",
                RelogioFixoDeTeste.AGORA.minus(Duration.ofMinutes(1)));

        Reservas.reservar(mvc, Reservas.clienteLogado(mvc), corpo(servico, barbeiro, as(10, 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"));

        List<String> status = jdbc.queryForList(
                "SELECT status FROM reserva WHERE barbeiro_id = ? ORDER BY criado_em, origem", String.class, barbeiro);
        assertThat(status).containsExactlyInAnyOrder("EXPIRADO", "AGUARDANDO_PAGAMENTO");
        assertThat(jdbc.queryForObject("SELECT status FROM reserva WHERE barbeiro_id = ? AND origem = 'GUEST'",
                String.class, barbeiro)).isEqualTo("EXPIRADO");
    }
}
