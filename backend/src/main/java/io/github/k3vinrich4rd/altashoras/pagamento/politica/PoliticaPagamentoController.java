package io.github.k3vinrich4rd.altashoras.pagamento.politica;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PoliticaPagamentoController {

    record PoliticaResposta(int versao, String texto) {
    }

    private final PoliticaPagamentoRepository politicas;

    PoliticaPagamentoController(PoliticaPagamentoRepository politicas) {
        this.politicas = politicas;
    }

    @GetMapping("/politica-pagamento")
    ResponseEntity<PoliticaResposta> vigente() {
        PoliticaPagamento politica = politicas.findFirstByOrderByVersaoDesc().orElseThrow();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(new PoliticaResposta(politica.getVersao(), politica.getTexto()));
    }
}
