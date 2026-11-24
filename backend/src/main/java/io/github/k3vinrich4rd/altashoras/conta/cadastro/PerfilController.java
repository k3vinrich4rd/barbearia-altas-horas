package io.github.k3vinrich4rd.altashoras.conta.cadastro;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.conta.usuario.PerfilResposta;

/** {@code PUT /me} (Fatia 11): autorizar ou retirar a região do perfil. O id vem do token (claim sub), nunca do corpo (R23). */
@RestController
class PerfilController {

    private final AtualizarRegiaoDoPerfil regiao;

    PerfilController(AtualizarRegiaoDoPerfil regiao) {
        this.regiao = regiao;
    }

    @PutMapping("/me")
    ResponseEntity<PerfilResposta> atualizar(@AuthenticationPrincipal Jwt token, @Valid @RequestBody AtualizarPerfilRequest pedido) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(regiao.atualizar(UUID.fromString(token.getSubject()), pedido));
    }
}
