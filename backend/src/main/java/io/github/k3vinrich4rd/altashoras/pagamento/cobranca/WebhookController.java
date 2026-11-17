package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.GatewayDePagamento;

/**
 * O aviso do gateway (E19, E20). Público no SegurancaConfig: quem autentica é o cabeçalho {@code asaas-access-token},
 * comparado em tempo constante com o segredo configurado. Token errado, ausente ou segredo não configurado: 401 sem
 * gravar nada. Falha ao processar sobe como 5xx de propósito: o gateway reenvia e o reenvio processa (A37).
 */
@RestController
class WebhookController {

    /** Só aqui o corpo ignora campos desconhecidos: o gateway manda dezenas de campos que não usamos. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record WebhookRequest(
            @NotBlank(message = "obrigatório") @Size(max = 120, message = "deve ter no máximo 120 caracteres") String id,
            @NotBlank(message = "obrigatório") @Size(max = 60, message = "deve ter no máximo 60 caracteres") String event,
            @Valid @Nullable PagamentoDoAviso payment) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PagamentoDoAviso(@Size(max = 100, message = "deve ter no máximo 100 caracteres") @Nullable String id) {
    }

    private final ReceberWebhook receber;
    private final GatewayDePagamento gateway;
    private final byte[] tokenEsperado;
    private final ObjectMapper json;
    private final Validator validador;

    WebhookController(ReceberWebhook receber, GatewayDePagamento gateway, ObjectMapper json, Validator validador,
            @Value("${app.pagamento.webhook-token:}") String tokenEsperado) {
        this.receber = receber;
        this.gateway = gateway;
        this.json = json;
        this.validador = validador;
        this.tokenEsperado = tokenEsperado.isEmpty() ? new byte[0] : sha256(tokenEsperado);
    }

    @PostMapping("/pagamentos/webhook")
    ResponseEntity<Void> receber(@RequestHeader(name = "asaas-access-token", required = false) @Nullable String token,
            @RequestBody String corpo) {
        // Autentica ANTES de ler o corpo: quem não tem o token recebe sempre 401, nunca um 400 que revele o formato.
        if (!autentico(token)) {
            throw new ExcecaoDeNegocio(CodigoErro.WEBHOOK_NAO_AUTENTICADO);
        }
        WebhookRequest aviso = ler(corpo);
        receber.receber(gateway.provedor(), aviso.id(), aviso.event(), aviso.payment() == null ? null : aviso.payment().id());
        return ResponseEntity.ok().build();
    }

    private WebhookRequest ler(String corpo) {
        WebhookRequest aviso;
        try {
            aviso = json.readValue(corpo, WebhookRequest.class);
        } catch (JacksonException invalido) {
            throw new ExcecaoDeNegocio(CodigoErro.JSON_MALFORMADO);
        }
        if (aviso == null || !validador.validate(aviso).isEmpty()) {
            throw new ExcecaoDeNegocio(CodigoErro.VALIDACAO_INVALIDA);
        }
        return aviso;
    }

    /** Compara os SHA-256 dos dois com MessageDigest.isEqual (tempo constante, qualquer tamanho). */
    private boolean autentico(@Nullable String token) {
        if (tokenEsperado.length == 0 || token == null || token.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(sha256(token), tokenEsperado);
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }
}
