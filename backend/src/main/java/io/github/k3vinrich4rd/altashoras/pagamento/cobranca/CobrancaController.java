package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * A rota é pública no SegurancaConfig (o visitante não tem login), mas quem decide o acesso é a reserva: bearer ou
 * X-Guest-Token do dono, senão 401 ou 404 (R23). Resposta com no-store: tem Pix e link de pagamento.
 */
@RestController
class CobrancaController {

    private final CriarCobranca criar;

    CobrancaController(CriarCobranca criar) {
        this.criar = criar;
    }

    @PostMapping("/pagamentos/cobrancas")
    ResponseEntity<CobrancaResposta> criar(@AuthenticationPrincipal @Nullable Jwt token,
            @RequestHeader(name = "X-Guest-Token", required = false) @Nullable String tokenGuest,
            @RequestHeader(name = "X-Idempotency-Key", required = false) @Nullable String chave,
            @Valid @RequestBody CobrancaRequest pedido) {
        CriarCobranca.Resultado resultado = criar.executar(Donos.de(token, tokenGuest), chave, pedido);
        return ResponseEntity.status(resultado.repeticao() ? HttpStatus.OK : HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore()).body(resultado.cobranca());
    }
}
