package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/**
 * Atalhos da Fatia 14A. O cenário da spec: reserva criada às 10:00:00 de terça 01/12/2026 para as 15:00, prazo de 3
 * minutos (expiraEm 10:03:00). O relógio é do teste: cada teste o coloca no instante que quer e o devolve no fim.
 */
final class ProrrogacaoDeTeste {

    private ProrrogacaoDeTeste() {
    }

    /** Um instante da terça 01/12/2026, no fuso da barbearia. */
    static Instant em(int hora, int minuto, int segundo) {
        return ZonedDateTime.of(2026, 12, 1, hora, minuto, segundo, 0, RelogioFixoDeTeste.FUSO).toInstant();
    }

    static ResultActions prorrogar(MockMvc mvc, String acesso, String id) throws Exception {
        return mvc.perform(post("/agendamentos/" + id + "/prorrogacao").header(HttpHeaders.AUTHORIZATION, "Bearer " + acesso));
    }

    static ResultActions prorrogarSemToken(MockMvc mvc, String id) throws Exception {
        return mvc.perform(post("/agendamentos/" + id + "/prorrogacao"));
    }

    static ResultActions prorrogarComoVisitante(MockMvc mvc, String id, String token) throws Exception {
        var pedido = post("/agendamentos/guest/" + id + "/prorrogacao");
        return mvc.perform(token == null ? pedido : pedido.header("X-Guest-Token", token));
    }

    static Instant expiraEm(JdbcTemplate jdbc, String id) {
        return jdbc.queryForObject("SELECT expira_em FROM reserva WHERE id = ?::uuid", OffsetDateTime.class, id).toInstant();
    }

    /** Nulo quando a reserva nunca foi prorrogada. */
    static Instant prorrogadaEm(JdbcTemplate jdbc, String id) {
        OffsetDateTime quando = jdbc.queryForObject("SELECT prorrogada_em FROM reserva WHERE id = ?::uuid",
                OffsetDateTime.class, id);
        return quando == null ? null : quando.toInstant();
    }

    static long versao(JdbcTemplate jdbc, String id) {
        return jdbc.queryForObject("SELECT version FROM reserva WHERE id = ?::uuid", Long.class, id);
    }

    static String status(JdbcTemplate jdbc, String id) {
        return jdbc.queryForObject("SELECT status FROM reserva WHERE id = ?::uuid", String.class, id);
    }

    static long eventosDePrazo(JdbcTemplate jdbc, String reservaId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE event_type LIKE '%PrazoDaReservaProrrogado' AND serialized_event LIKE ?
                """, Long.class, "%" + reservaId + "%");
    }
}
