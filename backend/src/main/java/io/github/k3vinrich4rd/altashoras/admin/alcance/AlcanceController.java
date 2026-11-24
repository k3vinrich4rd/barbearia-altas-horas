package io.github.k3vinrich4rd.altashoras.admin.alcance;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;

/**
 * {@code GET /admin/alcance} (Fatia 6C, reescrito na 11): ADMIN ou BARBEIRO (o SegurancaConfig já barra CLIENTE e a senha
 * inicial). Quem vê o quê é regra da agenda, pelo {@code sub} do token: o ADMIN vê tudo ou filtra por barbeiro; o BARBEIRO vê só
 * os próprios clientes (E15). O {@code nivel} (estado, região, município), a {@code uf} e o {@code periodo} são o seletor da tela.
 * Nunca em cache: o resultado depende de quem pergunta.
 */
@RestController
class AlcanceController {

    private final AlcanceDaAgenda alcance;

    AlcanceController(AlcanceDaAgenda alcance) {
        this.alcance = alcance;
    }

    @GetMapping("/admin/alcance")
    ResponseEntity<AlcanceDaAgenda.Painel> alcance(@AuthenticationPrincipal Jwt token, HttpServletRequest request,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) UUID barbeiroId, @RequestParam(required = false) AlcanceDaAgenda.Nivel nivel,
            @RequestParam(required = false) String uf, @RequestParam(required = false) AlcanceDaAgenda.PeriodoPadrao periodo) {
        Ator ator = Ator.de(token, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                alcance.painel(new PainelDaAgenda.Escopo(ator.id(), ator.admin()), de, ate, barbeiroId, nivel, uf, periodo));
    }
}
