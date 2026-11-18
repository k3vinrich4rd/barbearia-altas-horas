package io.github.k3vinrich4rd.altashoras.privacidade.titular;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code DELETE /me}: só CLIENTE (o {@code SegurancaConfig} responde 403 para BARBEIRO e ADMIN antes de chegar aqui) e
 * sem pedir a senha no beta (D-3). O id vem do token, nunca da URL.
 */
@RestController
class ExclusaoController {

    private final ExcluirConta excluir;

    ExclusaoController(ExcluirConta excluir) {
        this.excluir = excluir;
    }

    @DeleteMapping("/me")
    ResponseEntity<Void> excluirMinhaConta(@AuthenticationPrincipal Jwt token) {
        excluir.excluir(UUID.fromString(token.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
