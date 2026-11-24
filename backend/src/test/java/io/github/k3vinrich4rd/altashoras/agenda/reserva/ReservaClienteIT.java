package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.as;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpo;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.reservar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/** POST /agendamentos como cliente logado, de ponta a ponta no PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class ReservaClienteIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private UUID barbeiro;
    private UUID servico30;
    private String acesso;

    @BeforeEach
    void dados() throws Exception {
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico30 = DadosDeTeste.servico(jdbc, "Degradê", 30, 5500, true);
        acesso = Reservas.clienteLogado(mvc);
    }

    @Test
    @DisplayName("A1: horário livre → 201, Location, AGUARDANDO_PAGAMENTO, expira em 3 min, preço do serviço")
    void reservaLivre() throws Exception {
        String resposta = reservar(mvc, acesso, corpo(servico30, barbeiro, as(10, 0)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/agendamentos/")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.origem").value("CLIENTE"))
                .andExpect(jsonPath("$.inicio").value("2026-12-01T10:00:00-03:00"))
                .andExpect(jsonPath("$.fim").value("2026-12-01T10:30:00-03:00"))
                .andExpect(jsonPath("$.expiraEm").value("2026-11-17T08:03:00-03:00"))
                .andExpect(jsonPath("$.precoCentavos").value(5500))
                .andExpect(jsonPath("$.servico.id").value(servico30.toString()))
                .andExpect(jsonPath("$.barbeiro.id").value(barbeiro.toString()))
                .andExpect(jsonPath("$.tokenGuest").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(resposta, "$.id");
        assertThat(jdbc.queryForObject("SELECT preco_centavos FROM reserva WHERE id = ?::uuid", Long.class, id))
                .isEqualTo(5500L);
    }

    @Test
    @DisplayName("A2: o mesmo horário de novo → 409 HORARIO_INDISPONIVEL (a constraint do banco decide)")
    void horarioOcupado() throws Exception {
        reservar(mvc, acesso, corpo(servico30, barbeiro, as(10, 0))).andExpect(status().isCreated());
        reservar(mvc, Reservas.clienteLogado(mvc), corpo(servico30, barbeiro, as(10, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORARIO_INDISPONIVEL"));
    }

    @Test
    @DisplayName("A3: fora da grade, no almoço, na segunda e dentro de uma folga → 422 FORA_DO_EXPEDIENTE")
    void foraDoExpediente() throws Exception {
        DadosDeTeste.folga(jdbc, barbeiro, as(14, 0), as(16, 0));
        for (var inicio : new java.time.OffsetDateTime[] {as(10, 15), as(12, 0),
                DadosDeTeste.em(LocalDate.of(2026, 11, 30), 10, 0), as(14, 30)}) {
            reservar(mvc, acesso, corpo(servico30, barbeiro, inicio))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("FORA_DO_EXPEDIENTE"));
        }
    }

    @Test
    @DisplayName("A4: sem antecedência nem horizonte (D197): agora + 1h50 → 201 e 18/12 → 201; horário que já passou (hoje às 07:30) e ontem → 422 ANTECEDENCIA_INSUFICIENTE")
    void soOQueJaPassouEhRecusado() throws Exception {
        reservar(mvc, acesso, corpo(servico30, barbeiro, DadosDeTeste.em(LocalDate.of(2026, 11, 17), 9, 50)))
                .andExpect(status().isCreated());
        reservar(mvc, acesso, corpo(servico30, barbeiro, DadosDeTeste.em(LocalDate.of(2026, 12, 18), 10, 0)))
                .andExpect(status().isCreated());
        reservar(mvc, acesso, corpo(servico30, barbeiro, DadosDeTeste.em(LocalDate.of(2026, 11, 17), 7, 30)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ANTECEDENCIA_INSUFICIENTE"));
        reservar(mvc, acesso, corpo(servico30, barbeiro, DadosDeTeste.em(LocalDate.of(2026, 11, 16), 10, 0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ANTECEDENCIA_INSUFICIENTE"));
    }

    @Test
    @DisplayName("A5: serviço de 60 min às 11h30 invadiria o almoço → 422 FORA_DO_EXPEDIENTE")
    void invadeAlmoco() throws Exception {
        UUID servico60 = DadosDeTeste.servico(jdbc, 60);
        reservar(mvc, acesso, corpo(servico60, barbeiro, as(11, 30)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("FORA_DO_EXPEDIENTE"));
        reservar(mvc, acesso, corpo(servico60, barbeiro, as(11, 0))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A6: serviço inativo → 422 SERVICO_INATIVO; serviço ou barbeiro inexistente → 404")
    void servicoInativoOuInexistente() throws Exception {
        UUID inativo = DadosDeTeste.servico(jdbc, "Inativo", 30, 3000, false);
        reservar(mvc, acesso, corpo(inativo, barbeiro, as(10, 0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SERVICO_INATIVO"));
        reservar(mvc, acesso, corpo(UUID.randomUUID(), barbeiro, as(10, 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        reservar(mvc, acesso, corpo(servico30, UUID.randomUUID(), as(10, 0)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A7: sem token → 401; corpo com status ou precoCentavos → 400 CAMPO_DESCONHECIDO")
    void semTokenOuCampoProibido() throws Exception {
        mvc.perform(post("/agendamentos").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(servico30, barbeiro, as(10, 0))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));

        String base = corpo(servico30, barbeiro, as(10, 0)).strip();
        for (String extra : new String[] {"\"status\": \"CONFIRMADO\"", "\"precoCentavos\": 1"}) {
            String comExtra = base.substring(0, base.length() - 1) + ", " + extra + " }";
            reservar(mvc, acesso, comExtra)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", Long.class, barbeiro))
                .isZero();
    }
}
