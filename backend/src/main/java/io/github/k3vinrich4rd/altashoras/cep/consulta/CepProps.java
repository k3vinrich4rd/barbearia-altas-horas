package io.github.k3vinrich4rd.altashoras.cep.consulta;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.cep.*}. Os padrões são os números do ADR 0008; os testes trocam o teto do cache e as URLs.
 * Sem chave e sem conta: os dois provedores são gratuitos (D59).
 */
@ConfigurationProperties("app.cep")
public record CepProps(
        @DefaultValue("https://viacep.com.br") String viacepUrl,
        @DefaultValue("https://brasilapi.com.br") String brasilapiUrl,
        @DefaultValue("1s") Duration conexaoTimeout,
        @DefaultValue("2s") Duration leituraTimeout,
        @DefaultValue("7d") Duration cacheEncontradoTtl,
        @DefaultValue("1h") Duration cacheInexistenteTtl,
        @DefaultValue("10000") long cacheMax) {
}
