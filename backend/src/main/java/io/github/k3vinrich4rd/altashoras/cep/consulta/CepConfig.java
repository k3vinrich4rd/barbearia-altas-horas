package io.github.k3vinrich4rd.altashoras.cep.consulta;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Monta o que fala com a rede e o que lembra: os clientes HTTP com timeout curto (conexão 1 s, leitura 2 s) e os dois
 * caches Caffeine, um para CEP encontrado e outro para CEP inexistente, cada um com o seu prazo. O {@link Ticker} é um
 * bean para os testes trocarem por um falso e "passar 7 dias" sem esperar.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CepProps.class)
class CepConfig {

    static final String CACHE_ENCONTRADO = "cep-encontrado";
    static final String CACHE_INEXISTENTE = "cep-inexistente";

    /** Construtor dos clientes dos provedores; os testes trocam por um ligado ao MockRestServiceServer (E9). */
    @Bean
    RestClient.Builder construtorDeClientesDeCep(CepProps props) {
        return RestClient.builder().requestFactory(fabricaComTimeouts(props));
    }

    static JdkClientHttpRequestFactory fabricaComTimeouts(CepProps props) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(props.conexaoTimeout()).build());
        fabrica.setReadTimeout(props.leituraTimeout());
        return fabrica;
    }

    @Bean
    Ticker relogioDoCache() {
        return Ticker.systemTicker();
    }

    /** Falha de provedor nunca entra aqui (E5): só o que o provedor respondeu de verdade. */
    @Bean
    CacheManager cacheDoCep(CepProps props, Ticker ticker) {
        SimpleCacheManager gerenciador = new SimpleCacheManager();
        gerenciador.setCaches(List.of(
                cache(CACHE_ENCONTRADO, props.cacheEncontradoTtl(), props.cacheMax(), ticker),
                cache(CACHE_INEXISTENTE, props.cacheInexistenteTtl(), props.cacheMax(), ticker)));
        return gerenciador;
    }

    private static CaffeineCache cache(String nome, Duration ttl, long teto, Ticker ticker) {
        return new CaffeineCache(nome,
                Caffeine.newBuilder().ticker(ticker).expireAfterWrite(ttl).maximumSize(teto).build(), false);
    }
}
