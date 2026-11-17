package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.admin.Ator;

/** Configuração da agenda: só ADMIN (o SegurancaConfig barra o resto). Nunca em cache. */
@RestController
class ConfiguracaoAdminController {

    private static final CacheControl SEM_CACHE = CacheControl.noStore();

    private final ConfigurarAgenda configuracao;

    ConfiguracaoAdminController(ConfigurarAgenda configuracao) {
        this.configuracao = configuracao;
    }

    @GetMapping("/admin/configuracao")
    ResponseEntity<ConfiguracaoAdmin> ler() {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(configuracao.ler());
    }

    @PutMapping("/admin/configuracao")
    ResponseEntity<ConfiguracaoAdmin> alterar(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @Valid @RequestBody ConfiguracaoRequest pedido) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(configuracao.alterar(Ator.de(token, request), pedido));
    }
}
