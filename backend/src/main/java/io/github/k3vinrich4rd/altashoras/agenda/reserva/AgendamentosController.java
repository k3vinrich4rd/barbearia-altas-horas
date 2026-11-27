package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * Rotas de reserva. Não existem rotas de cancelar ou remarcar para o cliente (R18): pedir uma delas é 404.
 * Tudo com no-store: status de reserva muda a cada minuto e tem dado pessoal.
 */
@RestController
class AgendamentosController {

    private static final CacheControl SEM_CACHE = CacheControl.noStore();

    private final ReservarHorario reservar;
    private final ConsultaAgendamentos consulta;
    private final RetirarRegiaoDoVisitante retirarRegiao;
    private final ProrrogarPagamento prorrogar;

    AgendamentosController(ReservarHorario reservar, ConsultaAgendamentos consulta, RetirarRegiaoDoVisitante retirarRegiao,
            ProrrogarPagamento prorrogar) {
        this.reservar = reservar;
        this.consulta = consulta;
        this.retirarRegiao = retirarRegiao;
        this.prorrogar = prorrogar;
    }

    /**
     * A prorrogação não tem corpo (1.11.0): {@code {}} ou nada passam, qualquer campo é 400 CAMPO_DESCONHECIDO (o Jackson
     * recusa o que o record não declara). Desde a 1.12.0 aceita a {@code X-Idempotency-Key} opcional (E21).
     */
    record SemCorpo() {
    }

    /** "Mais tempo para pagar" do cliente logado: 201 quando soma, 200 na repetição com a mesma chave (E21). */
    @PostMapping("/agendamentos/{id}/prorrogacao")
    ResponseEntity<AgendamentoResposta> prorrogarComoCliente(@AuthenticationPrincipal Jwt token, @PathVariable UUID id,
            @RequestHeader(name = CHAVE, required = false) @Nullable String chave,
            @RequestBody(required = false) @Nullable SemCorpo corpo) {
        UUID cliente = UUID.fromString(token.getSubject());
        ProrrogarPagamento.Desfecho desfecho = prorrogar.doCliente(cliente, id, chaveDaProrrogacao(chave));
        return ResponseEntity.status(status(desfecho)).cacheControl(SEM_CACHE).body(consulta.meuSemConciliar(cliente, id));
    }

    /** O mesmo "mais tempo para pagar" do visitante, com o X-Guest-Token da reserva e sem bearer. */
    @PostMapping("/agendamentos/guest/{id}/prorrogacao")
    ResponseEntity<AgendamentoResposta> prorrogarComoVisitante(@PathVariable UUID id,
            @RequestHeader(name = "X-Guest-Token", required = false) @Nullable String token,
            @RequestHeader(name = CHAVE, required = false) @Nullable String chave,
            @RequestBody(required = false) @Nullable SemCorpo corpo) {
        ProrrogarPagamento.Desfecho desfecho = prorrogar.doVisitante(id, token, chaveDaProrrogacao(chave));
        return ResponseEntity.status(status(desfecho)).cacheControl(SEM_CACHE).body(consulta.doVisitanteSemConciliar(id));
    }

    private static final String CHAVE = "X-Idempotency-Key";

    /** Opcional (E21): vazia é o mesmo que nenhuma; fora do formato UUID é 400, antes de qualquer leitura da reserva. */
    private static @Nullable UUID chaveDaProrrogacao(@Nullable String chave) {
        if (chave == null || chave.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(chave.strip());
        } catch (IllegalArgumentException formato) {
            throw ExcecaoDeNegocio.campoInvalido(CHAVE, "deve ser um UUID");
        }
    }

    private static HttpStatus status(ProrrogarPagamento.Desfecho desfecho) {
        return desfecho == ProrrogarPagamento.Desfecho.PRORROGADA ? HttpStatus.CREATED : HttpStatus.OK;
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

    /** O visitante retira a autorização de região da própria reserva (Fatia 11, A6): 204 idempotente; token errado é 404. */
    @DeleteMapping("/agendamentos/guest/{id}/regiao")
    ResponseEntity<Void> retirarRegiaoDoVisitante(@PathVariable UUID id,
            @RequestHeader(name = "X-Guest-Token", required = false) @Nullable String token, HttpServletRequest request) {
        retirarRegiao.retirar(id, token, request.getRemoteAddr());
        return ResponseEntity.noContent().cacheControl(SEM_CACHE).build();
    }
}
