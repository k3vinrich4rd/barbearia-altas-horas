package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas de reserva. Não existem rotas de cancelar ou remarcar para o cliente (R18): pedir uma delas é 404.
 * Tudo com no-store: status de reserva muda a cada minuto e tem dado pessoal.
 */
@RestController
class AgendamentosController {

    private static final CacheControl SEM_CACHE = CacheControl.noStore();

    private final ReservarHorario reservar;
    private final ConsultaAgendamentos consulta;

    AgendamentosController(ReservarHorario reservar, ConsultaAgendamentos consulta) {
        this.reservar = reservar;
        this.consulta = consulta;
    }

    @PostMapping("/agendamentos")
    ResponseEntity<AgendamentoResposta> reservarComoCliente(@AuthenticationPrincipal Jwt token,
            @Valid @RequestBody Pedidos.Reserva pedido) {
        UUID cliente = UUID.fromString(token.getSubject());
        UUID id = reservar.comoCliente(cliente, pedido);
        return ResponseEntity.created(URI.create("/agendamentos/" + id)).cacheControl(SEM_CACHE)
                .body(consulta.meu(cliente, id));
    }

    @GetMapping("/me/agendamentos")
    ResponseEntity<ConsultaAgendamentos.Pagina> meus(@AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE)
                .body(consulta.meus(UUID.fromString(token.getSubject()), pagina, tamanho));
    }

    @GetMapping("/agendamentos/{id}")
    ResponseEntity<AgendamentoResposta> meu(@AuthenticationPrincipal Jwt token, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(consulta.meu(UUID.fromString(token.getSubject()), id));
    }

    @PostMapping("/agendamentos/guest")
    ResponseEntity<AgendamentoResposta> reservarComoVisitante(@Valid @RequestBody Pedidos.ReservaGuest pedido,
            HttpServletRequest request) {
        ReservarHorario.ReservaDoVisitante criada = reservar.comoVisitante(pedido, request.getRemoteAddr());
        return ResponseEntity.created(URI.create("/agendamentos/guest/" + criada.id())).cacheControl(SEM_CACHE)
                .body(consulta.criadaPeloVisitante(criada.id(), criada.token()));
    }

    @GetMapping("/agendamentos/guest/{id}")
    ResponseEntity<AgendamentoResposta> doVisitante(@PathVariable UUID id,
            @RequestHeader(name = "X-Guest-Token", required = false) @Nullable String token) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(consulta.doVisitante(id, token));
    }
}
