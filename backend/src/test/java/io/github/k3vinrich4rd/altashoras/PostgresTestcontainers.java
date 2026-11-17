package io.github.k3vinrich4rd.altashoras;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL 18 de verdade para os testes (ADR 0003: EXCLUDE USING gist e tstzrange não existem no H2).
 * Como bean, o container vive junto do contexto do Spring, que é reaproveitado entre as classes de teste:
 * um container por configuração de contexto, não um por classe.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
