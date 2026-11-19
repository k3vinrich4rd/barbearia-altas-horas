package io.github.k3vinrich4rd.altashoras.admin.agenda;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;

/**
 * Rotas da agenda do painel: ADMIN ou BARBEIRO (o SegurancaConfig já barra CLIENTE e senha inicial). Quem pode ver ou
 * fechar o quê é regra da agenda, pelo {@code sub} do token. Com dado pessoal e status que muda: nunca em cache.
 */
@RestController
class AgendaAdminController {

    private static final CacheControl SEM_CACHE = CacheControl.noStore();

    private final ConsultarAgendaDoPainel consultar;
    private final MudarStatusDoAgendamento mudar;
    private final CriarReservaManual criarManual;
    private final PainelDaAgenda agenda;

    AgendaAdminController(ConsultarAgendaDoPainel consultar, MudarStatusDoAgendamento mudar,
            CriarReservaManual criarManual, PainelDaAgenda agenda) {
        this.consultar = consultar;
        this.mudar = mudar;
        this.criarManual = criarManual;
        this.agenda = agenda;
    }

    /** Trava um horário combinado por WhatsApp (Fatia 6D): ADMIN para qualquer barbeiro, BARBEIRO só para si. */
    @PostMapping("/admin/agendamentos")
    ResponseEntity<AgendamentoAdmin> criarManual(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @Valid @RequestBody ReservaManualRequest pedido) {
        AgendamentoAdmin criado = criarManual.criar(Ator.de(token, request), pedido);
        return ResponseEntity.created(URI.create("/admin/agendamentos/" + criado.id())).cacheControl(SEM_CACHE)
                .body(criado);
    }

    @GetMapping("/admin/agenda")
    ResponseEntity<List<AgendamentoAdmin>> agenda(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(defaultValue = "DIA") PainelDaAgenda.Visao visao,
            @RequestParam(required = false) UUID barbeiroId) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE)
                .body(consultar.consultar(Ator.de(token, request), data, visao, barbeiroId));
    }

    /**
     * O barbeiro da própria conta e a jornada dele (D142, contrato 1.7.0): ADMIN ou BARBEIRO. O BARBEIRO não lê
     * {@code /admin/barbeiros}; sem esta rota a agenda dele não saberia onde fica o almoço. Nada de URL com id: o
     * barbeiro vem do {@code sub} do token, então ninguém lê a jornada de outro.
     */
    @GetMapping("/admin/minha-jornada")
    ResponseEntity<MinhaJornada> minhaJornada(@AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE)
                .body(MinhaJornada.de(agenda.minhaJornada(UUID.fromString(token.getSubject()))));
    }

    @PatchMapping("/admin/agendamentos/{id}/status")
    ResponseEntity<AgendamentoAdmin> status(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @PathVariable UUID id, @Valid @RequestBody MudancaStatusRequest pedido) {
        return ResponseEntity.ok().cacheControl(SEM_CACHE).body(mudar.mudar(Ator.de(token, request), id, pedido));
    }
}
