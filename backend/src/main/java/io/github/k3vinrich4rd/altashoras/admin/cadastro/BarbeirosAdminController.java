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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.admin.Ator;

/** Barbeiros, jornada e folgas do painel: só ADMIN (o SegurancaConfig barra o resto). Nunca em cache. */
@RestController
class BarbeirosAdminController {

    private static final CacheControl SEM_CACHE = CacheControl.noStore();

    private final CadastroDeBarbeiros barbeiros;
    private final CadastroDeFolgas folgas;

    BarbeirosAdminController(CadastroDeBarbeiros barbeiros, CadastroDeFolgas folgas) {
        this.barbeiros = barbeiros;
        this.folgas = folgas;
    }

    @GetMapping("/admin/barbeiros")
    ResponseEntity<List<BarbeiroAdmin>> listar() {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(barbeiros.listar());
    }

    @PostMapping("/admin/barbeiros")
    ResponseEntity<BarbeiroAdmin> criar(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @Valid @RequestBody BarbeiroRequest pedido) {
        BarbeiroAdmin criado = barbeiros.criar(Ator.de(token, request), pedido);
        return ResponseEntity.created(URI.create("/admin/barbeiros/" + criado.id())).cacheControl(SEM_CACHE)
                .body(criado);
    }

    @PutMapping("/admin/barbeiros/{id}")
    ResponseEntity<BarbeiroAdmin> alterar(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id, @Valid @RequestBody BarbeiroAlteracaoRequest pedido) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(barbeiros.alterar(Ator.de(token, request), id, pedido));
    }

    @PutMapping("/admin/barbeiros/{id}/jornada")
    ResponseEntity<List<IntervaloJornada>> jornada(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id, @RequestBody List<IntervaloJornada> pedido) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE)
                .body(barbeiros.substituirJornada(Ator.de(token, request), id, pedido));
    }

    @GetMapping("/admin/barbeiros/{id}/folgas")
    ResponseEntity<List<FolgaAdmin>> folgas(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(folgas.listar(id));
    }

    @PostMapping("/admin/barbeiros/{id}/folgas")
    ResponseEntity<FolgaAdmin> criarFolga(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id, @Valid @RequestBody FolgaRequest pedido) {
        FolgaAdmin criada = folgas.criar(Ator.de(token, request), id, pedido);
        return ResponseEntity.created(URI.create("/admin/barbeiros/" + id + "/folgas/" + criada.id()))
                .cacheControl(SEM_CACHE).body(criada);
    }

    @DeleteMapping("/admin/barbeiros/{id}/folgas/{folgaId}")
    ResponseEntity<Void> removerFolga(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id, @PathVariable UUID folgaId) {
        folgas.remover(Ator.de(token, request), id, folgaId);
        return ResponseEntity.noContent().cacheControl(SEM_CACHE).build();
    }
}
