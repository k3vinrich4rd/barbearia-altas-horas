package io.github.k3vinrich4rd.altashoras.plataforma.seguranca;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Quem pode chamar o quê. Regra central: NEGAR POR PADRÃO. Só o que está listado como público abre sem login;
 * rota nova nasce protegida, mesmo que alguém esqueça de pensar em segurança (E13).
 */
@Configuration(proxyBeanMethods = false)
class SegurancaConfig {

    @Bean
    SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver tratadorDeErros) throws Exception {
        // 401 e 403 nascem nos filtros, antes dos controllers. Repassando para o tratador global, saem no mesmo
        // formato RFC 9457 de todo o resto, com code e traceId (E12).
        AuthenticationEntryPoint naoAutenticado = (request, response, erro) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            tratadorDeErros.resolveException(request, response, null, erro);
        };
        AccessDeniedHandler semPermissao = (request, response, erro) ->
                tratadorDeErros.resolveException(request, response, null, erro);

        JwtGrantedAuthoritiesConverter papeis = new JwtGrantedAuthoritiesConverter();
        papeis.setAuthoritiesClaimName("papeis");
        papeis.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(papeis);

        http
                // CSRF desligado de propósito: a API é stateless e só aceita o token no cabeçalho Authorization.
                // Não há cookie de sessão para um site malicioso reaproveitar (ADR 0006).
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/servicos", "/barbeiros", "/politica-pagamento",
                                "/disponibilidade", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/cadastro", "/auth/login", "/auth/refresh",
                                "/auth/logout").permitAll()
                        .requestMatchers("/admin/**").access(painel())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(conversor))
                        .authenticationEntryPoint(naoAutenticado)
                        .accessDeniedHandler(semPermissao))
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(naoAutenticado)
                        .accessDeniedHandler(semPermissao));
        return http.build();
    }

    /** Painel: papel ADMIN ou BARBEIRO, e nunca com a senha inicial ainda em uso (E24). */
    private static AuthorizationManager<RequestAuthorizationContext> painel() {
        return (autenticacao, contexto) -> {
            Authentication quem = autenticacao.get();
            boolean logado = quem != null && quem.isAuthenticated() && !(quem instanceof AnonymousAuthenticationToken);
            boolean temPapel = logado && quem.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_BARBEIRO"));
            if (temPapel && quem.getPrincipal() instanceof Jwt jwt && Boolean.TRUE.equals(jwt.getClaim("trocar"))) {
                throw new TrocaDeSenhaObrigatoriaException();
            }
            return new AuthorizationDecision(temPapel);
        };
    }

    /** Só as origens de CORS_ORIGENS; nunca "*". Vazio = nenhum navegador de outra origem chama a API (E15). */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.seguranca.cors-origens:}") String origens) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origens.split(",")).map(String::strip).filter(o -> !o.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Idempotency-Key", "X-Guest-Token", "X-Trace-Id"));
        config.setExposedHeaders(List.of("Retry-After", "X-Trace-Id", "Location"));
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", config);
        return fonte;
    }

    /** BCrypt com custo configurável: alto em produção, baixo nos testes para não ficarem lentos (E3). */
    @Bean
    PasswordEncoder codificadorDeSenha(@Value("${app.seguranca.bcrypt-custo:12}") int custo) {
        return new BCryptPasswordEncoder(custo);
    }
}
