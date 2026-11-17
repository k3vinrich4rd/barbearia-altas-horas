package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

/**
 * O job de expiração com o agendador LIGADO (os outros testes o desligam), num banco só desta classe. O intervalo
 * é de um dia: o job roda sozinho só na subida (A21); nos outros critérios, o teste o chama à mão.
 */
@SpringBootTest(properties = {
        "app.jobs.habilitados=true",
        "app.jobs.expiracao.intervalo=86400000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/teste-subida"})
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExpiracaoJobIT {

    private static final String RESERVA_DA_SUBIDA = "e0000000-0000-4000-8000-0000000000a1";

    @Autowired
    ExpiracaoJob job;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    RelogioAjustavel relogio;

    @AfterEach
    void voltarRelogio() {
        relogio.reiniciar();
    }

    private String statusDe(String id) {
        return jdbc.queryForObject("SELECT status FROM reserva WHERE id = ?::uuid", String.class, id);
    }

    private List<String> pendentes(UUID barbeiro) {
        return jdbc.queryForList("SELECT id::text FROM reserva WHERE barbeiro_id = ? ORDER BY inicio", String.class,
                barbeiro);
    }

    private static void esperar(BooleanSupplier condicao) throws InterruptedException {
        for (int i = 0; i < 100 && !condicao.getAsBoolean(); i++) {
            Thread.sleep(100);
        }
        assertThat(condicao.getAsBoolean()).as("condição em até 10 s").isTrue();
    }

    /** Primeiro de propósito: os outros testes chamam o job à mão, o que também expiraria a reserva da subida. */
    @Test
    @Order(1)
    @DisplayName("A21: a API sobe com uma pendente já vencida → o job roda na subida e a expira")
    void rodaNaSubida() throws Exception {
        // Nenhum teste chamou o job ainda (@Order(1)): só a rodada da subida pode ter expirado esta reserva.
        esperar(() -> "EXPIRADO".equals(statusDe(RESERVA_DA_SUBIDA)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shedlock WHERE name = 'expiracao-reservas'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("A18: 3 pendentes e o relógio 16 min à frente → o job expira as 3 e o horário volta na disponibilidade")
    void expiraPendentes() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        for (OffsetDateTime inicio : List.of(as(10, 0), as(10, 30), as(11, 0))) {
            DadosDeTeste.reserva(jdbc, barbeiro, servico, inicio, "AGUARDANDO_PAGAMENTO",
                    RelogioFixoDeTeste.AGORA.plus(Duration.ofMinutes(15)));
        }
        job.executar();
        assertThat(pendentes(barbeiro)).allSatisfy(id -> assertThat(statusDe(id)).isEqualTo("AGUARDANDO_PAGAMENTO"));

        relogio.avancar(Duration.ofMinutes(16));
        job.executar();

        assertThat(pendentes(barbeiro)).hasSize(3).allSatisfy(id -> assertThat(statusDe(id)).isEqualTo("EXPIRADO"));
        mvc.perform(get("/disponibilidade").param("servicoId", servico.toString())
                        .param("data", Reservas.TERCA.toString()).param("barbeiroId", barbeiro.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barbeiros[0].horarios[?(@ == '2026-12-01T10:00:00-03:00')]").exists())
                .andExpect(jsonPath("$.barbeiros[0].horarios[?(@ == '2026-12-01T11:00:00-03:00')]").exists());
    }

    @Test
    @DisplayName("A19: com a trava do ShedLock na mão de outra instância, o job não processa; liberada, processa")
    void umaInstanciaPorVez() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        DadosDeTeste.reserva(jdbc, barbeiro, servico, as(10, 0), "AGUARDANDO_PAGAMENTO",
                RelogioFixoDeTeste.AGORA.minus(Duration.ofMinutes(1)));
        String id = pendentes(barbeiro).getFirst();
        // Outra instância "segurando" o job pelos próximos 10 minutos, na hora do banco em UTC (como o ShedLock grava).
        jdbc.update("""
                INSERT INTO shedlock (name, lock_until, locked_at, locked_by)
                VALUES ('expiracao-reservas', timezone('utc', now()) + interval '10 minutes', timezone('utc', now()), 'outra-instancia')
                ON CONFLICT (name) DO UPDATE SET lock_until = EXCLUDED.lock_until, locked_at = EXCLUDED.locked_at,
                                                 locked_by = EXCLUDED.locked_by
                """);
        try {
            job.executar();
            assertThat(statusDe(id)).isEqualTo("AGUARDANDO_PAGAMENTO");
        } finally {
            jdbc.update("UPDATE shedlock SET lock_until = timezone('utc', now()) WHERE name = 'expiracao-reservas'");
        }

        job.executar();
        assertThat(statusDe(id)).isEqualTo("EXPIRADO");
    }

    @Test
    @DisplayName("A20: reserva expirada pelo job → um ReservaExpirada gravado no outbox e concluído")
    void eventoNoOutbox() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        DadosDeTeste.reserva(jdbc, barbeiro, servico, as(10, 0), "AGUARDANDO_PAGAMENTO",
                RelogioFixoDeTeste.AGORA.minus(Duration.ofMinutes(1)));
        String id = pendentes(barbeiro).getFirst();

        job.executar();

        String sql = """
                SELECT count(*) FROM event_publication
                WHERE event_type LIKE '%ReservaExpirada' AND serialized_event LIKE ? AND completion_date IS NOT NULL
                """;
        esperar(() -> jdbc.queryForObject(sql, Long.class, "%" + id + "%") == 1L);
    }
}
