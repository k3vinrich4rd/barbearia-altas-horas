package io.github.k3vinrich4rd.altashoras.plataforma.seguranca;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

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
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

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
                // CSRF ligado, exigido onde o ataque é possível: requisição que altera dados E chega com cookie.
                // A API autentica só pelo token no cabeçalho Authorization (ADR 0006), que o navegador nunca envia
                // sozinho; quem usa a API assim não é afetado. Se um dia algum fluxo passar a usar cookie, a
                // proteção já está ativa para ele (D58).
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(SegurancaConfig::exigeCsrf))
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/servicos", "/barbeiros", "/politica-pagamento",
                                "/disponibilidade", "/actuator/health", "/actuator/health/**").permitAll()
                        // CEP (Fatia 6A): pública, mas com limite por IP; é a primeira rota que gasta serviço de terceiros (H21).
                        .requestMatchers(HttpMethod.GET, "/cep/**").permitAll()
                        // Visitante sem conta (R26): reserva com limite por IP; a consulta exige o X-Guest-Token.
                        .requestMatchers(HttpMethod.POST, "/agendamentos/guest").permitAll()
                        .requestMatchers(HttpMethod.GET, "/agendamentos/guest/*").permitAll()
                        // Pagamento (Fatia 4B): o visitante cobra com o X-Guest-Token e o gateway chama o webhook sem
                        // login; quem decide o acesso é a reserva (dono) e o asaas-access-token. A confirmação simulada
                        // só existe no modo simulado (D59). O CSRF continua valendo só com Cookie.
                        .requestMatchers(HttpMethod.POST, "/pagamentos/cobrancas", "/pagamentos/webhook",
                                "/pagamentos/simulacao/*/confirmar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/cadastro", "/auth/login", "/auth/refresh",
                                "/auth/logout").permitAll()
                        // Só ADMIN (E2): pagamentos, auditoria e, na 5B, os cadastros. A senha inicial continua barrada.
                        .requestMatchers("/admin/pagamentos/**", "/admin/pagamentos", "/admin/auditoria",
                                "/admin/servicos/**", "/admin/servicos", "/admin/barbeiros/**", "/admin/barbeiros",
                                "/admin/configuracao").access(soAdmin())
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

    /**
     * CSRF é o navegador mandar sozinho uma credencial (cookie) numa requisição forjada por outro site. Só existe
     * risco quando a requisição muda algo e carrega cookie; nesses casos o token CSRF é exigido.
     */
    static boolean exigeCsrf(HttpServletRequest request) {
        boolean alteraDados = !METODOS_SEGUROS.contains(request.getMethod());
        return alteraDados && request.getHeader(HttpHeaders.COOKIE) != null;
    }

    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** Painel: papel ADMIN ou BARBEIRO, e nunca com a senha inicial ainda em uso (E24). */
    private static AuthorizationManager<RequestAuthorizationContext> painel() {
        return (autenticacao, contexto) -> {
            Authentication quem = autenticacao.get();
            boolean temPapel = temAlgumPapel(quem, "ROLE_ADMIN", "ROLE_BARBEIRO");
            barrarSenhaInicial(quem, temPapel);
            return new AuthorizationDecision(temPapel);
        };
    }

    /**
     * Rotas só do ADMIN (dinheiro, auditoria e cadastros, E2): o BARBEIRO recebe 403 ACESSO_NEGADO. Quem tem os dois
     * papéis (o Eduardo) vale como ADMIN. Com a senha inicial ainda em uso, o painel inteiro continua barrado.
     */
    private static AuthorizationManager<RequestAuthorizationContext> soAdmin() {
        return (autenticacao, contexto) -> {
            Authentication quem = autenticacao.get();
            barrarSenhaInicial(quem, temAlgumPapel(quem, "ROLE_ADMIN", "ROLE_BARBEIRO"));
            return new AuthorizationDecision(temAlgumPapel(quem, "ROLE_ADMIN"));
        };
    }

    private static boolean temAlgumPapel(Authentication quem, String... papeis) {
        boolean logado = quem != null && quem.isAuthenticated() && !(quem instanceof AnonymousAuthenticationToken);
        return logado && quem.getAuthorities().stream().anyMatch(a -> Arrays.asList(papeis).contains(a.getAuthority()));
    }

    private static void barrarSenhaInicial(Authentication quem, boolean temPapelDoPainel) {
        if (temPapelDoPainel && quem.getPrincipal() instanceof Jwt jwt && Boolean.TRUE.equals(jwt.getClaim("trocar"))) {
            throw new TrocaDeSenhaObrigatoriaException();
        }
    }

    /**
     * O firewall do Spring Security recusa caminho com barra ou percentual codificado (`%2F`, `%25`) antes de qualquer
     * controller, e o padrão é um 400 sem corpo. Aqui sai no formato de todo o resto (RFC 9457): em `/cep/` é
     * `CEP_INVALIDO` (A3); nas demais rotas, `VALIDACAO_INVALIDA`. A recusa continua acontecendo antes de qualquer chamada.
     */
    @Bean
    RequestRejectedHandler pedidoRecusadoPeloFirewall(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver tratadorDeErros) {
        return (request, response, recusa) -> {
            CodigoErro codigo = request.getRequestURI().startsWith("/cep/")
                    ? CodigoErro.CEP_INVALIDO : CodigoErro.VALIDACAO_INVALIDA;
            // Sem a URI: ela pode trazer o CEP (R30). Só o método e o código, para notar se as recusas virarem ataque.
            org.slf4j.LoggerFactory.getLogger(SegurancaConfig.class)
                    .debug("pedido recusado pelo firewall: metodo={} code={}", request.getMethod(), codigo);
            tratadorDeErros.resolveException(request, response, null, new ExcecaoDeNegocio(codigo));
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
