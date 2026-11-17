package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador de entrada HTTP. Muda a cada reserva, então nunca fica em cache (E12). */
@RestController
class DisponibilidadeController {

    private final ConsultaDisponibilidade consulta;

    DisponibilidadeController(ConsultaDisponibilidade consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/disponibilidade")
    ResponseEntity<DisponibilidadeResposta> disponibilidade(
            @RequestParam UUID servicoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) UUID barbeiroId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(consulta.consultar(servicoId, data, barbeiroId));
    }
}
