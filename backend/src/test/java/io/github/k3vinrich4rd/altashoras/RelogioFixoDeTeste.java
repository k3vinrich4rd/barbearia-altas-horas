package io.github.k3vinrich4rd.altashoras;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Para os testes que dependem de "agora" (antecedência, horizonte, expiração): o relógio para em
 * terça-feira, 17/11/2026, 08h00 em Osasco. É por isso que o código de produção só lê o tempo pelo Clock.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RelogioFixoDeTeste {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    public static final Instant AGORA = ZonedDateTime.of(2026, 11, 17, 8, 0, 0, 0, FUSO).toInstant();

    @Bean
    @Primary
    Clock relogioFixo() {
        return Clock.fixed(AGORA, FUSO);
    }
}
