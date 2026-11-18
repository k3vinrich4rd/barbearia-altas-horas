package io.github.k3vinrich4rd.altashoras.cep;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * Troca o que fala com a internet e o que mede tempo no cache do CEP (E5, E9): os clientes recebem o construtor ligado
 * ao {@link ProvedoresFalsos} e o cache usa um {@link TickerFalso} que o teste adianta sem esperar.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CepDeTeste {

    @Bean
    ProvedoresFalsos provedoresFalsos() {
        return new ProvedoresFalsos();
    }

    @Bean
    @Primary
    RestClient.Builder construtorComProvedoresFalsos(ProvedoresFalsos provedores) {
        return provedores.construtor();
    }

    @Bean
    @Primary
    TickerFalso tickerFalso() {
        return new TickerFalso();
    }

    /** Relógio do cache que só anda quando o teste manda. */
    public static class TickerFalso implements Ticker {

        private final AtomicLong nanos = new AtomicLong();

        public void avancar(Duration quanto) {
            nanos.addAndGet(quanto.toNanos());
        }

        public void reiniciar() {
            nanos.set(0);
        }

        @Override
        public long read() {
            return nanos.get();
        }
    }
}
