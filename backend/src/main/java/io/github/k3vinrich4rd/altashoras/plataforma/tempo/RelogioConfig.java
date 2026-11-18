package io.github.k3vinrich4rd.altashoras.plataforma.tempo;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O único lugar que lê o relógio do sistema (ADR 0004). O resto do código recebe {@link Clock} injetado, o que
 * deixa os testes "adiantarem o relógio" (expiração de 3 min) sem esperar. Regra garantida pelo ArquiteturaTest.
 */
@Configuration(proxyBeanMethods = false)
class RelogioConfig {

    @Bean
    Clock clock(@Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        return Clock.system(ZoneId.of(fuso));
    }
}
