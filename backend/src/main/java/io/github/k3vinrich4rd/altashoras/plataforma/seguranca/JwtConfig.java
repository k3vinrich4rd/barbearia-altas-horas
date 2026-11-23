package io.github.k3vinrich4rd.altashoras.plataforma.seguranca;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import io.github.k3vinrich4rd.altashoras.plataforma.EmissorDeToken;
import io.github.k3vinrich4rd.altashoras.plataforma.ValidadorDeConta;

/**
 * Assinatura e leitura do JWT (HS256, ADR 0006). A validade é conferida com o Clock injetado, igual ao resto do
 * sistema: é o que permite ao teste "adiantar o relógio" e ver o token vencer sem esperar 15 minutos.
 */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

    static final int BYTES_MINIMOS = 32;

    /** Sem segredo forte a API não sobe: melhor falhar na subida que assinar token com chave fraca (E2). */
    static SecretKey chave(String segredo) {
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < BYTES_MINIMOS) {
            throw new IllegalStateException("JWT_SEGREDO ausente ou com menos de " + BYTES_MINIMOS
                    + " bytes. Defina a variável de ambiente antes de subir a API.");
        }
        return new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    SecretKey chaveDoJwt(@Value("${app.seguranca.jwt.segredo:}") String segredo) {
        return chave(segredo);
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey chaveDoJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chaveDoJwt));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey chaveDoJwt, Clock clock, ObjectProvider<ValidadorDeConta> contas) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chaveDoJwt).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator validade = new JwtTimestampValidator(Duration.ZERO);
        validade.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validade, contaEmUso(contas)));
        return decoder;
    }

    /**
     * A conta do token precisa continuar em uso (Fatia 6B, E16): depois da exclusão, o token de acesso já emitido deixa
     * de valer na hora, e não só quando vencer. O validador da conta é buscado na hora de usar (a conta depende da
     * plataforma, nunca o contrário).
     */
    private static OAuth2TokenValidator<Jwt> contaEmUso(ObjectProvider<ValidadorDeConta> contas) {
        return token -> {
            ValidadorDeConta validador = contas.getIfAvailable();
            if (validador != null && token.getSubject() != null) {
                try {
                    if (!validador.podeAcessar(UUID.fromString(token.getSubject()),
                            token.getClaimAsStringList("papeis") == null ? List.of() : token.getClaimAsStringList("papeis"))) {
                        return OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "A conta deste token não está mais em uso.", null));
                    }
                } catch (IllegalArgumentException subjectQueNaoEhUuid) {
                    return OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Token sem uma conta válida.", null));
                }
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    @Bean
    EmissorDeToken emissorDeToken(JwtEncoder encoder, Clock clock,
            @Value("${app.seguranca.jwt.validade:15m}") Duration validade) {
        return (UUID usuarioId, Collection<String> papeis, int versaoCredencial, boolean deveTrocarSenha) -> {
            Instant agora = clock.instant();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .subject(usuarioId.toString())
                    .issuedAt(agora)
                    .expiresAt(agora.plus(validade))
                    .claim("papeis", List.copyOf(papeis))
                    .claim("ver", versaoCredencial)
                    .claim("trocar", deveTrocarSenha)
                    .build();
            String valor = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                    .getTokenValue();
            return new EmissorDeToken.TokenEmitido(valor, validade.toSeconds());
        };
    }
}
