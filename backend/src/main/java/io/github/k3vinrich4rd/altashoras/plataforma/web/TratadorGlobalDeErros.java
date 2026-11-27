package io.github.k3vinrich4rd.altashoras.plataforma.web;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeLimite;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.plataforma.seguranca.TrocaDeSenhaObrigatoriaException;

/**
 * Todo erro da API sai em application/problem+json (RFC 9457, ADR 0005) com as extensões "code" (estável, para o
 * front decidir) e "traceId" (para achar o log). As exceções do Spring MVC (404 de rota, 405, 415, JSON malformado,
 * validação) passam pela classe base e ganham o mesmo formato em {@link #handleExceptionInternal}.
 */
@RestControllerAdvice
public class TratadorGlobalDeErros extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorGlobalDeErros.class);

    /** Erro previsto pelo contrato (404, 409, 422...): o código e o detalhe vêm da própria exceção. */
    @ExceptionHandler(ExcecaoDeNegocio.class)
    ResponseEntity<ProblemDetail> erroDeNegocio(ExcecaoDeNegocio ex, HttpServletRequest request) {
        ProblemDetail corpo = problema(ex.codigo(), request.getRequestURI());
        corpo.setDetail(ex.getMessage());
        String campo = ex.campo();
        if (campo != null) {
            corpo.setProperty("errors", List.of(Map.of("field", campo, "message", ex.getMessage())));
        }
        ResponseEntity.BodyBuilder resposta = ResponseEntity.status(corpo.getStatus());
        // Extensão do contrato (ex.: conflitos[] com nome de cliente, só para o ADMIN): vai no corpo, nunca no log, e
        // a resposta não fica em cache (R30, E15 da Fatia 15).
        Map<String, Object> extensoes = ex.extensoes();
        if (!extensoes.isEmpty()) {
            extensoes.forEach(corpo::setProperty);
            resposta.cacheControl(CacheControl.noStore());
        }
        if (ex instanceof ExcecaoDeLimite limite) {
            resposta.header(HttpHeaders.RETRY_AFTER, Long.toString(limite.tentarDeNovoEmSegundos()));
        }
        return resposta.body(corpo);
    }

    /** Sem login, ou com token inválido ou vencido (vem do filtro de segurança, pelo SegurancaConfig). */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> naoAutenticado(AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail corpo = problema(CodigoErro.NAO_AUTENTICADO, request.getRequestURI());
        return ResponseEntity.status(corpo.getStatus()).body(corpo);
    }

    /** Logado, mas sem o papel (403), ou ainda com a senha inicial. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> semPermissao(AccessDeniedException ex, HttpServletRequest request) {
        CodigoErro codigo = ex instanceof TrocaDeSenhaObrigatoriaException
                ? CodigoErro.TROCA_DE_SENHA_OBRIGATORIA : CodigoErro.ACESSO_NEGADO;
        ProblemDetail corpo = problema(codigo, request.getRequestURI());
        return ResponseEntity.status(corpo.getStatus()).body(corpo);
    }

    /**
     * Duas pessoas (ou o job e o painel) mexeram na mesma linha ao mesmo tempo: @Version recusou o segundo (E8, E23).
     * É 409 e nunca 500: recarregar a tela resolve.
     */
    @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> conflitoDeVersao(Exception ex, HttpServletRequest request) {
        ProblemDetail corpo = problema(CodigoErro.CONFLITO_DE_VERSAO, request.getRequestURI());
        return ResponseEntity.status(corpo.getStatus()).body(corpo);
    }

    /**
     * Banco fora, lento ou sem conexão no pool (Fatia 8B, E10, A15): 503 genérico com Retry-After, nunca um pedido
     * pendurado nem a mensagem do driver (R30: sem URL, sem usuário). A API se recupera sozinha quando o banco volta.
     */
    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class})
    ResponseEntity<ProblemDetail> bancoIndisponivel(Exception ex, HttpServletRequest request) {
        LOG.warn("Banco indisponível em {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName());
        ProblemDetail corpo = problema(CodigoErro.BANCO_INDISPONIVEL, request.getRequestURI());
        return ResponseEntity.status(corpo.getStatus()).header(HttpHeaders.RETRY_AFTER, "30").body(corpo);
    }

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
            // Um item por campo (o primeiro erro de cada um), em ordem estável.
            Map<String, String> porCampo = new java.util.TreeMap<>();
            invalido.getBindingResult().getFieldErrors().forEach(erro -> porCampo.putIfAbsent(erro.getField(),
                    erro.getDefaultMessage() == null ? "inválido" : erro.getDefaultMessage()));
            corpo.setProperty("errors", porCampo.entrySet().stream()
                    .map(e -> Map.of("field", e.getKey(), "message", e.getValue())).toList());
        } else if (ex instanceof MissingServletRequestParameterException ausente) {
            corpo.setProperty("errors", List.of(Map.of("field", ausente.getParameterName(), "message", "obrigatório")));
        } else if (ex instanceof MethodArgumentTypeMismatchException tipoErrado) {
            corpo.setProperty("errors", List.of(Map.of("field", tipoErrado.getName(), "message", "formato inválido")));
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
            // Campo fora do contrato (fail-on-unknown-properties) é erro diferente de JSON quebrado.
            for (Throwable causa = ex.getCause(); causa != null; causa = causa.getCause()) {
                if (causa.getClass().getSimpleName().contains("UnrecognizedProperty")) {
                    return CodigoErro.CAMPO_DESCONHECIDO;
                }
            }
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
