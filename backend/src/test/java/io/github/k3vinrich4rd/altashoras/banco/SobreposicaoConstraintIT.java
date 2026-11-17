package io.github.k3vinrich4rd.altashoras.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/** A4 a A6: a constraint EXCLUDE USING gist com tstzrange '[)' (R1, R2, ADR 0003). */
@SpringBootTest
@Import(PostgresTestcontainers.class)
class SobreposicaoConstraintIT {

    /** 10h00 em Osasco. */
    private static final OffsetDateTime DEZ_HORAS = OffsetDateTime.of(2026, 12, 1, 10, 0, 0, 0, ZoneOffset.ofHours(-3));

    @Autowired
    JdbcTemplate jdbc;

    private UUID barbeiro;
    private UUID servico;

    @BeforeEach
    void novoBarbeiro() {
        barbeiro = ReservaSql.novoBarbeiro(jdbc);
        servico = ReservaSql.novoServico(jdbc);
    }

    private void reservar(OffsetDateTime inicio, String status) {
        jdbc.update(ReservaSql.INSERIR, ReservaSql.parametros(barbeiro, servico, inicio, status));
    }

    @Test
    @DisplayName("A4: segunda reserva no mesmo horário falha com SQLState 23P01")
    void mesmoHorarioRecusado() {
        reservar(DEZ_HORAS, "CONFIRMADO");
        assertThatThrownBy(() -> reservar(DEZ_HORAS, "AGUARDANDO_PAGAMENTO"))
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(((SQLException) ((DataAccessException) ex).getMostSpecificCause())
                        .getSQLState()).isEqualTo("23P01"));
    }

    @Test
    @DisplayName("A4: sobreposição parcial (10h15 sobre 10h00-10h30) também é recusada")
    void sobreposicaoParcialRecusada() {
        reservar(DEZ_HORAS, "AGUARDANDO_PAGAMENTO");
        assertThatThrownBy(() -> reservar(DEZ_HORAS.plusMinutes(15), "AGUARDANDO_PAGAMENTO"))
                .hasMessageContaining("reserva_sem_sobreposicao");
    }

    @Test
    @DisplayName("A5: reservas encostadas [10h00,10h30) e [10h30,11h00) convivem")
    void encostadasPassam() {
        reservar(DEZ_HORAS, "CONFIRMADO");
        reservar(DEZ_HORAS.plusMinutes(30), "CONFIRMADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", Integer.class, barbeiro))
                .isEqualTo(2);
    }

    @ParameterizedTest(name = "A6: reserva {0} não ocupa o horário")
    @ValueSource(strings = {"CANCELADO", "EXPIRADO"})
    void statusQueNaoOcupam(String status) {
        reservar(DEZ_HORAS, status);
        reservar(DEZ_HORAS, "AGUARDANDO_PAGAMENTO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", Integer.class, barbeiro))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Barbeiros diferentes podem ter o mesmo horário")
    void outroBarbeiroPassa() {
        reservar(DEZ_HORAS, "CONFIRMADO");
        barbeiro = ReservaSql.novoBarbeiro(jdbc);
        reservar(DEZ_HORAS, "CONFIRMADO");
    }
}
