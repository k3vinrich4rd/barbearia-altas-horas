package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/**
 * A61b: com o agendador LIGADO (os outros testes o desligam), o job de retenção roda sozinho na subida, como o job de
 * expiração: o Render free hiberna e pode estar dormindo às 3h. O cron fica no padrão (3h): só a rodada da subida pode ter
 * mexido no visitante vencido, que a migração de teste deixa pronto antes de o agendador começar.
 */
@SpringBootTest(properties = {
        "app.jobs.habilitados=true",
        "app.jobs.expiracao.intervalo=86400000",
        "app.jobs.reprocessamento.intervalo=86400000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/teste-subida-retencao"})
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class RetencaoNaSubidaIT {

    private static final String RESERVA_DA_SUBIDA = "e0000000-0000-4000-8000-0000000000b1";

    @Autowired
    JdbcTemplate jdbc;

    private static void esperar(BooleanSupplier condicao) throws InterruptedException {
        for (int i = 0; i < 100 && !condicao.getAsBoolean(); i++) {
            Thread.sleep(100);
        }
        assertThat(condicao.getAsBoolean()).as("condição em até 10 s").isTrue();
    }

    @Test
    @DisplayName("A61b: com app.jobs.habilitados=true, a API sobe com um visitante vencido e o job de retenção o anonimiza sozinho, pela trava do ShedLock")
    void rodaNaSubida() throws Exception {
        esperar(() -> "Visitante anonimizado".equals(jdbc.queryForObject(
                "SELECT nome FROM reserva_guest WHERE reserva_id = ?::uuid", String.class, RESERVA_DA_SUBIDA)));
        assertThat(jdbc.queryForObject("SELECT anonimizado_em IS NOT NULL FROM reserva_guest WHERE reserva_id = ?::uuid",
                Boolean.class, RESERVA_DA_SUBIDA)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shedlock WHERE name = 'retencao-de-dados'", Long.class))
                .isEqualTo(1L);
    }
}
