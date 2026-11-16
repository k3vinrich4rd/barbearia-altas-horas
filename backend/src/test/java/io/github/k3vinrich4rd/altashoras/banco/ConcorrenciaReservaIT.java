package io.github.k3vinrich4rd.altashoras.banco;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/**
 * A7: várias conexões tentam o mesmo horário ao mesmo tempo e só uma vence. Cada thread abre a própria conexão
 * (fora do pool de 5 do Hikari) e todas esperam o mesmo sinal para inserir: a disputa é real, no banco.
 * O que nunca muda é o resultado: uma reserva só.
 */
@SpringBootTest
@Import(PostgresTestcontainers.class)
class ConcorrenciaReservaIT {

    private static final int THREADS = 12;
    private static final OffsetDateTime INICIO = OffsetDateTime.of(2026, 12, 2, 15, 0, 0, 0, ZoneOffset.ofHours(-3));

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PostgreSQLContainer postgres;

    @RepeatedTest(3)
    @DisplayName("A7: 12 inserções simultâneas no mesmo horário, exatamente 1 vence; as outras recebem 23P01 ou 40P01")
    void somenteUmaVence() throws Exception {
        UUID barbeiro = ReservaSql.novoBarbeiro(jdbc);
        UUID servico = ReservaSql.novoServico(jdbc);
        CountDownLatch prontas = new CountDownLatch(THREADS);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<String>> resultados = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                resultados.add(executor.submit(() -> {
                    try (Connection conexao = DriverManager.getConnection(
                            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                         PreparedStatement insert = conexao.prepareStatement(ReservaSql.INSERIR)) {
                        Object[] p = ReservaSql.parametros(barbeiro, servico, INICIO, "AGUARDANDO_PAGAMENTO");
                        for (int c = 0; c < p.length; c++) {
                            insert.setObject(c + 1, p[c]);
                        }
                        prontas.countDown();
                        largada.await(10, TimeUnit.SECONDS);
                        insert.executeUpdate();
                        return "OK";
                    } catch (SQLException e) {
                        return e.getSQLState();
                    }
                }));
            }
            assertThat(prontas.await(30, TimeUnit.SECONDS)).isTrue();
            largada.countDown();
        }

        List<String> estados = new ArrayList<>();
        for (Future<String> r : resultados) {
            estados.add(r.get());
        }
        // Quem perde recebe violação da constraint (23P01) ou, quando várias esperam umas pelas outras, deadlock
        // (40P01): o PostgreSQL aborta as perdedoras. A API refaz uma vez e responde 409 (ADR 0003, D27).
        assertThat(estados).containsAnyOf("OK").isSubsetOf("OK", "23P01", "40P01");
        assertThat(estados).filteredOn("OK"::equals).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", Integer.class, barbeiro))
                .isEqualTo(1);
    }
}
