package io.github.k3vinrich4rd.altashoras.privacidade.titular;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /me/dados}: a rota que mais devolve dado pessoal por chamada, então nunca fica em cache (no-store). */
@RestController
class ExportacaoController {

    private final ExportarDados exportar;

    ExportacaoController(ExportarDados exportar) {
        this.exportar = exportar;
    }

    @GetMapping("/me/dados")
    ResponseEntity<Map<String, Object>> meusDados(@AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(exportar.exportar(UUID.fromString(token.getSubject())));
    }
}
