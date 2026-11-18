package io.github.k3vinrich4rd.altashoras.privacidade.titular;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.privacidade.Consentimentos;

/** {@code GET /politica-privacidade}: pública, sem dado de ninguém; o texto vem dos arquivos versionados (E13). */
@RestController
class PoliticaPrivacidadeController {

    private final PoliticaPrivacidadeResposta resposta;

    PoliticaPrivacidadeController(TextosDaPolitica textos) {
        this.resposta = new PoliticaPrivacidadeResposta(Consentimentos.VERSAO_DO_TEXTO, textos.consentimento(),
                textos.politica(), TextosDaPolitica.ATUALIZADA_EM);
    }

    @GetMapping("/politica-privacidade")
    PoliticaPrivacidadeResposta vigente() {
        return resposta;
    }
}
