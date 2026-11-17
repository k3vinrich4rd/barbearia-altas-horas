package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.admin.Ator;

/** Serviços do painel: só ADMIN (o SegurancaConfig já barra BARBEIRO, CLIENTE e a senha inicial). Nunca em cache. */
@RestController
class ServicosAdminController {

    private static final CacheControl SEM_CACHE = CacheControl.noStore();

    private final CadastroDeServicos servicos;

    ServicosAdminController(CadastroDeServicos servicos) {
        this.servicos = servicos;
    }

    @GetMapping("/admin/servicos")
    ResponseEntity<List<ServicoAdmin>> listar() {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(servicos.listar());
    }

    @PostMapping("/admin/servicos")
    ResponseEntity<ServicoAdmin> criar(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @Valid @RequestBody ServicoRequest pedido) {
        ServicoAdmin criado = servicos.criar(Ator.de(token, request), pedido);
        return ResponseEntity.created(URI.create("/admin/servicos/" + criado.id())).cacheControl(SEM_CACHE)
                .body(criado);
    }

    @PutMapping("/admin/servicos/{id}")
    ResponseEntity<ServicoAdmin> alterar(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id, @Valid @RequestBody ServicoAlteracaoRequest pedido) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(servicos.alterar(Ator.de(token, request), id, pedido));
    }
}
