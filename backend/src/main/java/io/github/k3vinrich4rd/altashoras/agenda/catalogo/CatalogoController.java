package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo público: o que o visitante vê antes de qualquer login. Muda pouco, então cabe cache curto (E12). */
@RestController
class CatalogoController {

    static final CacheControl CACHE_DO_CATALOGO = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final ServicoRepository servicos;
    private final BarbeiroRepository barbeiros;

    CatalogoController(ServicoRepository servicos, BarbeiroRepository barbeiros) {
        this.servicos = servicos;
        this.barbeiros = barbeiros;
    }

    @GetMapping("/servicos")
    ResponseEntity<List<ServicoResumo>> servicos() {
        return ResponseEntity.ok().cacheControl(CACHE_DO_CATALOGO).body(servicos.listarAtivos());
    }

    @GetMapping("/barbeiros")
    ResponseEntity<List<BarbeiroNoCatalogo>> barbeiros() {
        return ResponseEntity.ok().cacheControl(CACHE_DO_CATALOGO).body(barbeiros.listarAtivos());
    }
}
