package io.github.k3vinrich4rd.altashoras.conta.convite;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /auth/convite}: pública, com limite por IP; o token do cliente é opcional (conta que já existe, H32). */
@RestController
class ConviteController {

    private final AceitarConvite aceitarConvite;

    ConviteController(AceitarConvite aceitarConvite) {
        this.aceitarConvite = aceitarConvite;
    }

    @PostMapping("/auth/convite")
    ResponseEntity<Void> aceitar(@Valid @RequestBody ConviteAceiteRequest pedido, HttpServletRequest request,
            Authentication autenticacao) {
        UUID contaLogada = autenticacao instanceof JwtAuthenticationToken jwt && jwt.getToken().getSubject() != null
                ? UUID.fromString(jwt.getToken().getSubject()) : null;
        aceitarConvite.aceitar(pedido, request.getRemoteAddr(), contaLogada);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
