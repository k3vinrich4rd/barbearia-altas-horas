package io.github.k3vinrich4rd.altashoras;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Para os testes que dependem de "agora" (antecedência, horizonte, expiração de token): o relógio para em
 * terça-feira, 17/11/2026, 08h00 em Osasco, e o teste pode adiantá-lo. É por isso que o código de produção só lê
 * o tempo pelo Clock. Quem adianta o relógio volta com reiniciar() no fim.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RelogioFixoDeTeste {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    public static final Instant AGORA = ZonedDateTime.of(2026, 11, 17, 8, 0, 0, 0, FUSO).toInstant();

    @Bean
    @Primary
    RelogioAjustavel relogioFixo() {
        return new RelogioAjustavel();
    }

    public static class RelogioAjustavel extends Clock {

        private volatile Instant agora = AGORA;

        public void avancar(Duration quanto) {
            agora = agora.plus(quanto);
        }

        /** Vai direto para um instante (a Fatia 14 conta os segundos do prazo do Pix). Quem usa volta com reiniciar(). */
        public void definir(Instant instante) {
            agora = instante;
        }

        public void reiniciar() {
            agora = AGORA;
        }

        @Override
        public ZoneId getZone() {
            return FUSO;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
