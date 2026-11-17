package io.github.k3vinrich4rd.altashoras.admin.pagamento;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.admin.Ator;

/**
 * Pendências, devolução e eventos de pagamento do painel. Só ADMIN: o SegurancaConfig barra o BARBEIRO antes do
 * controller (E2). Dado de pagador e status que muda: nunca em cache.
 */
@RestController
class PagamentosAdminController {

    private static final CacheControl SEM_CACHE = CacheControl.noStore();

    private final ListarPendencias pendencias;
    private final RegistrarDevolucao devolucao;
    private final RevisarEvento eventos;

    PagamentosAdminController(ListarPendencias pendencias, RegistrarDevolucao devolucao, RevisarEvento eventos) {
        this.pendencias = pendencias;
        this.devolucao = devolucao;
        this.eventos = eventos;
    }

    @GetMapping("/admin/pagamentos")
    ResponseEntity<ListarPendencias.Pagina> pagamentos(@RequestParam(required = false) @Nullable String status,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(pendencias.listar(status, pagina, tamanho));
    }

    @PostMapping("/admin/pagamentos/{id}/devolucao")
    ResponseEntity<RegistrarDevolucao.Devolvida> devolver(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(devolucao.devolver(Ator.de(token, request), id));
    }

    @GetMapping("/admin/pagamentos/eventos")
    ResponseEntity<RevisarEvento.Pagina> eventosEmRevisao(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(eventos.listar(pagina, tamanho));
    }

    @PostMapping("/admin/pagamentos/eventos/{id}/revisao")
    ResponseEntity<RevisarEvento.EventoAdmin> revisar(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(eventos.revisar(Ator.de(token, request), id));
    }
}
