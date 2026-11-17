package io.github.k3vinrich4rd.altashoras.admin.auditoria;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Só ADMIN (o SegurancaConfig barra o BARBEIRO). Sem cache: é registro de quem fez o quê. */
@RestController
class AuditoriaController {

    private final AuditoriaAdmin auditoria;

    AuditoriaController(AuditoriaAdmin auditoria) {
        this.auditoria = auditoria;
    }

    @GetMapping("/admin/auditoria")
    ResponseEntity<AuditoriaAdmin.Pagina> listar(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auditoria.listar(pagina, tamanho));
    }
}
