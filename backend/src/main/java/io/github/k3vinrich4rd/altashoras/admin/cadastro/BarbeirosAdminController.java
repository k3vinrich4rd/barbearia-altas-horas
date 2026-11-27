package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.admin.Ator;

/** Barbeiros, jornada e folgas do painel: só ADMIN (o SegurancaConfig barra o resto). Nunca em cache. */
@RestController
class BarbeirosAdminController {

    private static final CacheControl SEM_CACHE = CacheControl.noStore();

    private final CadastroDeBarbeiros barbeiros;
    private final CadastroDeFolgas folgas;
    private final ConviteDeBarbeiro convites;

    BarbeirosAdminController(CadastroDeBarbeiros barbeiros, CadastroDeFolgas folgas, ConviteDeBarbeiro convites) {
        this.barbeiros = barbeiros;
        this.folgas = folgas;
        this.convites = convites;
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

    /** O convite de uso único (Fatia 10B). Sem corpo: o barbeiro digita o próprio e-mail ao aceitar (D-1). */
    @PostMapping("/admin/barbeiros/{id}/convite")
    ResponseEntity<ConviteGerado> gerarConvite(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id) {
        return ResponseEntity.status(201).cacheControl(SEM_CACHE).body(convites.gerar(Ator.de(token, request), id));
    }

    @DeleteMapping("/admin/barbeiros/{id}/convite")
    ResponseEntity<Void> revogarConvite(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id) {
        convites.revogar(Ator.de(token, request), id);
        return ResponseEntity.noContent().cacheControl(SEM_CACHE).build();
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
    ResponseEntity<FolgaSalva> criarFolga(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id, @Valid @RequestBody FolgaRequest pedido) {
        FolgaSalva criada = folgas.criar(Ator.de(token, request), id, pedido);
        return ResponseEntity.created(URI.create("/admin/barbeiros/" + id + "/folgas/" + criada.id()))
                .cacheControl(SEM_CACHE).body(criada);
    }

    /** A prévia antes de salvar (ADR 0015, E24). Rota literal: no Spring ela ganha da {@code /folgas/{folgaId}}. */
    @GetMapping("/admin/barbeiros/{id}/folgas/conflitos")
    ResponseEntity<List<ConflitoDeFolga>> conflitosDaFolga(@PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime inicio,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime fim) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(folgas.conflitos(id, inicio, fim));
    }

    @PutMapping("/admin/barbeiros/{id}/folgas/{folgaId}")
    ResponseEntity<FolgaSalva> alterarFolga(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id, @PathVariable UUID folgaId, @Valid @RequestBody FolgaRequest pedido) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE)
                .body(folgas.alterar(Ator.de(token, request), id, folgaId, pedido));
    }

    @DeleteMapping("/admin/barbeiros/{id}/folgas/{folgaId}")
    ResponseEntity<Void> removerFolga(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id, @PathVariable UUID folgaId) {
        folgas.remover(Ator.de(token, request), id, folgaId);
        return ResponseEntity.noContent().cacheControl(SEM_CACHE).build();
    }
}
