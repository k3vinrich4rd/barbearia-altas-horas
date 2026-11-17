package io.github.k3vinrich4rd.altashoras.plataforma.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Dá um traceId a cada requisição: vai para o log (MDC), para o cabeçalho X-Trace-Id e para o corpo dos erros
 * RFC 9457. Com o traceId que o cliente vê na tela, dá para achar a linha exata do log sem expor detalhe técnico.
 * Um X-Trace-Id recebido só é aceito no formato W3C (32 hex), para ninguém injetar texto no log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RastreioFilter extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Trace-Id";
    public static final String CHAVE_MDC = "traceId";
    private static final Pattern FORMATO = Pattern.compile("^[0-9a-f]{32}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recebido = request.getHeader(CABECALHO);
        String traceId = recebido != null && FORMATO.matcher(recebido).matches()
                ? recebido
                : UUID.randomUUID().toString().replace("-", "");
        MDC.put(CHAVE_MDC, traceId);
        response.setHeader(CABECALHO, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CHAVE_MDC);
        }
    }
}
