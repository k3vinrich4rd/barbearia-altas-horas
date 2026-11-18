package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.as;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpoGuest;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.reservarComoVisitante;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/** O visitante sem conta: reserva com nome, e-mail e WhatsApp, e acompanha pelo token mostrado uma vez. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class ReservaGuestIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private UUID barbeiro;
    private UUID servico;

    @BeforeEach
    void dados() {
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, 30);
    }

    @Test
    @DisplayName("A11: visitante → 201 com tokenGuest g1_; no banco só o SHA-256 do token; nenhum CPF pedido")
    void reservaDoVisitante() throws Exception {
        String resposta = reservarComoVisitante(mvc, ContaDeTeste.ipNovo(), corpoGuest(servico, barbeiro, as(10, 0)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/agendamentos/guest/")))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.origem").value("GUEST"))
                .andExpect(jsonPath("$.expiraEm").value("2026-11-17T08:03:00-03:00"))
                .andExpect(jsonPath("$.cpf").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(resposta, "$.id");
        String token = JsonPath.read(resposta, "$.tokenGuest");
        assertThat(token).startsWith("g1_").hasSizeGreaterThan(40);

        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT email, whatsapp, token_hash FROM reserva_guest WHERE reserva_id = ?::uuid", id);
        assertThat(linha.get("token_hash")).isEqualTo(TokenGuest.hash(token)).isNotEqualTo(token);
        assertThat(linha.get("email")).isEqualTo("visitante@exemplo.test");
        assertThat(linha.get("whatsapp")).isEqualTo("11900000002");
        assertThat(jdbc.queryForObject("SELECT cliente_id FROM reserva WHERE id = ?::uuid", UUID.class, id)).isNull();
    }

    @Test
    @DisplayName("A12: consultar com o token → 200; token errado ou sem token → 404")
    void consultaPeloToken() throws Exception {
        String resposta = reservarComoVisitante(mvc, ContaDeTeste.ipNovo(), corpoGuest(servico, barbeiro, as(10, 0)))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(resposta, "$.id");
        String token = JsonPath.read(resposta, "$.tokenGuest");

        mvc.perform(get("/agendamentos/guest/" + id).header("X-Guest-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.tokenGuest").doesNotExist());
        mvc.perform(get("/agendamentos/guest/" + id).header("X-Guest-Token", "g1_errado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        mvc.perform(get("/agendamentos/guest/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A13: a 4ª reserva de visitante do mesmo IP em 1 hora → 429 LIMITE_EXCEDIDO com Retry-After")
    void limitePorIp() throws Exception {
        String ip = ContaDeTeste.ipNovo();
        // Tentativas recusadas (horário fora do expediente) não gastam a cota: só reserva criada conta.
        for (int i = 0; i < 3; i++) {
            reservarComoVisitante(mvc, ip, corpoGuest(servico, barbeiro, as(12, 0)))
                    .andExpect(status().isUnprocessableContent());
        }
        reservarComoVisitante(mvc, ip, corpoGuest(servico, barbeiro, as(10, 0))).andExpect(status().isCreated());
        reservarComoVisitante(mvc, ip, corpoGuest(servico, barbeiro, as(10, 30))).andExpect(status().isCreated());
        reservarComoVisitante(mvc, ip, corpoGuest(servico, barbeiro, as(11, 0))).andExpect(status().isCreated());
        reservarComoVisitante(mvc, ip, corpoGuest(servico, barbeiro, as(11, 30)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("LIMITE_EXCEDIDO"));
        // Outro IP continua reservando.
        reservarComoVisitante(mvc, ContaDeTeste.ipNovo(), corpoGuest(servico, barbeiro, as(11, 30)))
                .andExpect(status().isCreated());
    }
}
