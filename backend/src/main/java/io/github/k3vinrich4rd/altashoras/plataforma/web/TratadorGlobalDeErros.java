package io.github.k3vinrich4rd.altashoras.plataforma.web;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;

/**
 * Todo erro da API sai em application/problem+json (RFC 9457, ADR 0005) com as extensões "code" (estável, para o
 * front decidir) e "traceId" (para achar o log). As exceções do Spring MVC (404 de rota, 405, 415, JSON malformado,
 * validação) passam pela classe base e ganham o mesmo formato em {@link #handleExceptionInternal}.
 */
@RestControllerAdvice
public class TratadorGlobalDeErros extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorGlobalDeErros.class);

    /** Erro não previsto: 500 sem detalhe técnico na resposta; a pilha fica só no log, ligada pelo traceId. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> erroInesperado(Exception ex, HttpServletRequest request) {
        LOG.error("Erro não tratado em {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail corpo = problema(CodigoErro.ERRO_INTERNO, request.getRequestURI());
        return ResponseEntity.status(corpo.getStatus()).body(corpo);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        CodigoErro codigo = codigoPara(ex, statusCode);
        ProblemDetail corpo = problema(codigo, caminho(request));
        corpo.setStatus(statusCode.value());
        if (ex instanceof MethodArgumentNotValidException invalido) {
            corpo.setProperty("errors", invalido.getBindingResult().getFieldErrors().stream()
                    .map(erro -> Map.of("field", erro.getField(),
                            "message", erro.getDefaultMessage() == null ? "inválido" : erro.getDefaultMessage()))
                    .toList());
        }
        return ResponseEntity.status(statusCode).headers(headers).body(corpo);
    }

    static ProblemDetail problema(CodigoErro codigo, String instancia) {
        ProblemDetail corpo = ProblemDetail.forStatus(codigo.status());
        corpo.setType(URI.create(codigo.tipo()));
        corpo.setTitle(codigo.titulo());
        corpo.setDetail(codigo.detalhePadrao());
        corpo.setInstance(URI.create(instancia));
        corpo.setProperty("code", codigo.name());
        corpo.setProperty("traceId", MDC.get(RastreioFilter.CHAVE_MDC));
        corpo.setProperty("errors", List.of());
        return corpo;
    }

    private static CodigoErro codigoPara(Exception ex, HttpStatusCode status) {
        if (ex instanceof HttpMessageNotReadableException) {
            return CodigoErro.JSON_MALFORMADO;
        }
        return switch (status.value()) {
            case 404 -> CodigoErro.RECURSO_NAO_ENCONTRADO;
            case 405 -> CodigoErro.METODO_NAO_PERMITIDO;
            case 415 -> CodigoErro.MIDIA_NAO_SUPORTADA;
            default -> status.is4xxClientError() ? CodigoErro.VALIDACAO_INVALIDA : CodigoErro.ERRO_INTERNO;
        };
    }

    private static String caminho(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : "/";
    }
}
