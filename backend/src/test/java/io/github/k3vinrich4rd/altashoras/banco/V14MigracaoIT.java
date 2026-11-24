package io.github.k3vinrich4rd.altashoras.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/**
 * A13 da Fatia 14A: o que a V14 deixa no banco. Dois olhares: a API que sobe com a V14 aplicada (colunas e CHECK) e, num
 * PostgreSQL só deste teste, a migração partindo da V13 com valores diferentes do padrão (as demais colunas da configuração
 * mantêm o que o admin tinha escolhido).
 */
@SpringBootTest
@Import(PostgresTestcontainers.class)
class V14MigracaoIT {

    private static final OffsetDateTime INICIO = OffsetDateTime.of(2026, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    JdbcTemplate jdbc;

    private static List<String> colunas(JdbcTemplate jdbc, String tabela) {
        return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                String.class, tabela);
    }

    @Test
    @DisplayName("A13: com a API no ar a V14 foi aplicada: configuracao_agenda não tem mais antecedencia_minima_min nem horizonte_dias nem coluna de prorrogação; reserva.prorrogada_em existe e o CHECK recusa prorrogação em reserva MANUAL")
    void v14AplicadaNaSubida() {
        assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '14'", Boolean.class))
                .isTrue();
        assertThat(colunas(jdbc, "configuracao_agenda")).doesNotContain("antecedencia_minima_min", "horizonte_dias")
                .noneMatch(coluna -> coluna.contains("prorrog"))
                .contains("intervalo_entre_atendimentos_min", "granularidade_min", "expiracao_pagamento_min", "fuso");
        assertThat(colunas(jdbc, "reserva")).contains("prorrogada_em");

        UUID barbeiro = ReservaSql.novoBarbeiro(jdbc);
        UUID servico = ReservaSql.novoServico(jdbc);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO reserva (id, barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status,
                                     preco_centavos, prorrogada_em)
                VALUES (?, ?, ?, 'MANUAL', ?, ?, ?, 'CONFIRMADO', 5500, now())
                """, UUID.randomUUID(), barbeiro, servico, INICIO, INICIO.plusMinutes(30), INICIO.plusMinutes(30)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("reserva_prorrogacao_ck");
        // A reserva manual sem prorrogação continua valendo: o CHECK só recusa a combinação.
        jdbc.update("""
                INSERT INTO reserva (id, barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos)
                VALUES (?, ?, ?, 'MANUAL', ?, ?, ?, 'CONFIRMADO', 5500)
                """, UUID.randomUUID(), barbeiro, servico, INICIO, INICIO.plusMinutes(30), INICIO.plusMinutes(30));
    }

    @Test
    @DisplayName("A13: partindo da V13 com antecedência 60, horizonte 45, intervalo 10, granularidade 15 e expiração 7, a V14 tira os dois campos e as outras colunas mantêm os valores (o CHECK volta sem eles)")
    void v14MantemOsOutrosValores() {
        try (PostgreSQLContainer banco = new PostgreSQLContainer("postgres:18-alpine")) {
            banco.start();
            DriverManagerDataSource dados = new DriverManagerDataSource(banco.getJdbcUrl(), banco.getUsername(),
                    banco.getPassword());
            Flyway.configure().dataSource(dados).locations("classpath:db/migration").target("13").load().migrate();
            JdbcTemplate velho = new JdbcTemplate(dados);
            velho.update("""
                    UPDATE configuracao_agenda SET antecedencia_minima_min = 60, horizonte_dias = 45,
                           intervalo_entre_atendimentos_min = 10, granularidade_min = 15, expiracao_pagamento_min = 7
                    WHERE id = 1
                    """);

            Flyway.configure().dataSource(dados).locations("classpath:db/migration").load().migrate();

            assertThat(colunas(velho, "configuracao_agenda")).doesNotContain("antecedencia_minima_min", "horizonte_dias");
            Map<String, Object> config = velho.queryForMap("SELECT * FROM configuracao_agenda WHERE id = 1");
            assertThat(config).containsEntry("intervalo_entre_atendimentos_min", 10).containsEntry("granularidade_min", 15)
                    .containsEntry("expiracao_pagamento_min", 7).containsEntry("fuso", "America/Sao_Paulo");
            assertThat(colunas(velho, "reserva")).contains("prorrogada_em");
            assertThatThrownBy(() -> velho.update("UPDATE configuracao_agenda SET granularidade_min = 25 WHERE id = 1"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("configuracao_agenda_valores_ck");
            assertThatThrownBy(() -> velho.update("UPDATE configuracao_agenda SET expiracao_pagamento_min = 2 WHERE id = 1"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("configuracao_agenda_valores_ck");
        }
    }
}
