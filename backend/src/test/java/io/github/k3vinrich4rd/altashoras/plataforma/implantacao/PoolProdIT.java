package io.github.k3vinrich4rd.altashoras.plataforma.implantacao;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import com.zaxxer.hikari.HikariDataSource;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/**
 * A17 (Fatia 8B): o application-prod.yml carregado de verdade (com variáveis falsas) traz o pool pequeno do Aiven, os
 * timeouts do pool e do driver e o desligamento limpo. O banco real da conexão vem do Testcontainers; o pool
 * ({@code HikariDataSource}) é o configurado pelo perfil.
 */
@SpringBootTest(properties = {
        "DB_URL=jdbc:postgresql://nao-usado.exemplo.test:5432/nao_usado",
        "DB_USUARIO=nao-usado",
        "DB_SENHA=nao-usado",
        "IDEMPOTENCIA_HMAC_SEGREDO=idempotencia-falsa-so-do-teste"})
@ActiveProfiles("prod")
@Import(PostgresTestcontainers.class)
class PoolProdIT {

    @Autowired
    Environment ambiente;

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("A17: perfil prod tem pool de 3 conexões, timeouts curtos, timeouts do driver e shutdown graceful")
    void propriedadesDoPerfilProd() {
        assertThat(ambiente.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class)).isEqualTo(3);
        assertThat(ambiente.getProperty("spring.datasource.hikari.minimum-idle", Integer.class)).isEqualTo(1);
        assertThat(ambiente.getProperty("spring.datasource.hikari.connection-timeout", Long.class)).isEqualTo(10000L);
        assertThat(ambiente.getProperty("spring.datasource.hikari.validation-timeout", Long.class)).isEqualTo(3000L);
        assertThat(ambiente.getProperty("spring.datasource.hikari.max-lifetime", Long.class)).isEqualTo(600000L);
        assertThat(ambiente.getProperty("spring.datasource.hikari.keepalive-time", Long.class)).isEqualTo(120000L);
        assertThat(ambiente.getProperty("spring.datasource.hikari.data-source-properties.connectTimeout")).isEqualTo("10");
        assertThat(ambiente.getProperty("spring.datasource.hikari.data-source-properties.socketTimeout")).isEqualTo("10");
        assertThat(ambiente.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(ambiente.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("20s");
    }

    @Test
    @DisplayName("A17: o pool que a API usa é o Hikari com 3 conexões, e o perfil treino não está ativo")
    void poolEmUso() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(3);
        assertThat(hikari.getConnectionTimeout()).isEqualTo(10000L);
        assertThat(ambiente.getActiveProfiles()).containsExactly("prod");
    }
}
