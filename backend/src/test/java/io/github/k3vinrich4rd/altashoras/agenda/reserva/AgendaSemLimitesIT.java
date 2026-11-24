package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.em;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.as;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpo;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpoGuest;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.reservar;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.reservarComoVisitante;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/**
 * A7, A8 e A19 da Fatia 14A (D197): a agenda não tem antecedência mínima nem horizonte; a única regra de tempo é o início
 * ser depois de agora, no fuso de São Paulo. Eduardo com jornada de ter a sáb (07:00-12:00 e 13:00-20:00), serviço de 30 min.
 */
class AgendaSemLimitesIT extends PainelBase {

    private static final LocalDate DEZ_01 = LocalDate.of(2026, 12, 1);
    private static final LocalDate JAN_2028 = LocalDate.of(2028, 1, 5);

    private UUID eduardo;
    private UUID servico;

    @BeforeEach
    void cenario() {
        eduardo = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, 30);
    }

    private ResultActions disponibilidade(LocalDate dia) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/disponibilidade?servicoId=" + servico + "&barbeiroId=" + eduardo + "&data=" + dia));
    }

    @Test
    @DisplayName("A7: às 09:55 de ter 01/12/2026 o horário das 10:00 de hoje aparece livre e reserva (cliente e visitante), e qua 05/01/2028 (400 dias à frente) também")
    void semAntecedenciaESemHorizonte() throws Exception {
        relogio.definir(em(9, 55, 0));
        Conta cliente = conta("CLIENTE");

        disponibilidade(DEZ_01).andExpect(status().isOk())
                .andExpect(jsonPath("$.barbeiros[0].horarios", hasItem("2026-12-01T10:00:00-03:00")));
        reservar(mvc, cliente.token(), corpo(servico, eduardo, as(10, 0))).andExpect(status().isCreated());
        reservarComoVisitante(mvc, ContaDeTeste.ipNovo(), corpoGuest(servico, eduardo, as(10, 30)))
                .andExpect(status().isCreated());

        disponibilidade(JAN_2028).andExpect(status().isOk())
                .andExpect(jsonPath("$.barbeiros[0].horarios", hasItem("2028-01-05T10:00:00-03:00")));
        reservar(mvc, cliente.token(), corpo(servico, eduardo, DadosDeTeste.em(JAN_2028, 10, 0)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A8: às 10:05 ontem (GET e reserva), hoje às 10:00 (já passou) e hoje às 10:00 com o relógio em 10:00:00 exato dão 422 ANTECEDENCIA_INSUFICIENTE; o GET de hoje não mostra 10:00")
    void soOQueJaPassouEhRecusado() throws Exception {
        // O token nasce às 10:00:00 (o JWT usa o mesmo Clock) e o relógio só anda para a frente.
        relogio.definir(em(10, 0, 0));
        Conta cliente = conta("CLIENTE");
        reservar(mvc, cliente.token(), corpo(servico, eduardo, as(10, 0))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ANTECEDENCIA_INSUFICIENTE"));

        relogio.definir(em(10, 5, 0));
        LocalDate ontem = DEZ_01.minusDays(1);

        disponibilidade(ontem).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ANTECEDENCIA_INSUFICIENTE"));
        reservar(mvc, cliente.token(), corpo(servico, eduardo, DadosDeTeste.em(ontem, 10, 0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ANTECEDENCIA_INSUFICIENTE"));
        reservar(mvc, cliente.token(), corpo(servico, eduardo, as(10, 0))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ANTECEDENCIA_INSUFICIENTE"));

        disponibilidade(DEZ_01).andExpect(status().isOk())
                .andExpect(jsonPath("$.barbeiros[0].horarios", not(hasItem("2026-12-01T10:00:00-03:00"))))
                .andExpect(jsonPath("$.barbeiros[0].horarios", hasItem("2026-12-01T10:30:00-03:00")));
    }

    @Test
    @DisplayName("A19: reserva manual às 09:55 de ter 01/12/2026: 10:00 de hoje e qua 05/01/2028 às 10:00 dão 201; 09:30 de hoje (já passou) dá 422 ANTECEDENCIA_INSUFICIENTE")
    void reservaManualSemLimites() throws Exception {
        relogio.definir(em(9, 55, 0));
        Conta admin = comTokenNovo(conta("ADMIN", "BARBEIRO"), "ADMIN", "BARBEIRO");

        manual(admin, as(10, 0)).andExpect(status().isCreated()).andExpect(jsonPath("$.origem").value("MANUAL"));
        manual(admin, DadosDeTeste.em(JAN_2028, 10, 0)).andExpect(status().isCreated());
        manual(admin, as(9, 30)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ANTECEDENCIA_INSUFICIENTE"));
    }

    private ResultActions manual(Conta admin, OffsetDateTime inicio) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/admin/agendamentos").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                .contentType(MediaType.APPLICATION_JSON).content(
                        "{ \"servicoId\": \"" + servico + "\", \"barbeiroId\": \"" + eduardo + "\", \"inicio\": \""
                                + inicio + "\" }"));
    }
}
