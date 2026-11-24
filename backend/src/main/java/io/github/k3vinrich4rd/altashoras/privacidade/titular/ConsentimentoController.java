package io.github.k3vinrich4rd.altashoras.privacidade.titular;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.privacidade.Consentimentos;

/** {@code /me/consentimentos}: o titular vê e muda o próprio consentimento. O id vem do token, nunca da URL (R23). */
@RestController
class ConsentimentoController {

    private final Consentimentos consentimentos;
    private final ZoneId fuso;

    ConsentimentoController(Consentimentos consentimentos, @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.consentimentos = consentimentos;
        this.fuso = ZoneId.of(fuso);
    }

    @GetMapping("/me/consentimentos")
    List<ConsentimentoResposta> meus(@AuthenticationPrincipal Jwt token) {
        return consentimentos.atuais(UUID.fromString(token.getSubject())).stream()
                .map(registro -> ConsentimentoResposta.de(registro, fuso)).toList();
    }

    @PutMapping("/me/consentimentos/marketing")
    ConsentimentoResposta registrar(@AuthenticationPrincipal Jwt token, @Valid @RequestBody ConsentimentoRequest pedido) {
        // A tela mostrou o texto de uma versão; se não é mais a vigente, a pessoa precisa ler o texto novo (E12).
        if (!Consentimentos.VERSAO_DO_TEXTO.equals(pedido.versaoTexto())) {
            throw ExcecaoDeNegocio.campoInvalido("versaoTexto",
                    "Esta versão do texto não é a vigente. Recarregue a política e tente de novo.");
        }
        return ConsentimentoResposta.de(
                consentimentos.registrar(UUID.fromString(token.getSubject()), pedido.aceito(), pedido.versaoTexto()), fuso);
    }

    /**
     * O consentimento de região (Fatia 11, A4): no molde do de marketing. Revogar apaga a localidade do perfil e a região de
     * todas as reservas da conta (evento síncrono, na mesma transação).
     */
    @PutMapping("/me/consentimentos/regiao")
    ConsentimentoResposta registrarRegiao(@AuthenticationPrincipal Jwt token, @Valid @RequestBody ConsentimentoRequest pedido) {
        if (!Consentimentos.VERSAO_DO_TEXTO.equals(pedido.versaoTexto())) {
            throw ExcecaoDeNegocio.campoInvalido("versaoTexto",
                    "Esta versão do texto não é a vigente. Recarregue a política e tente de novo.");
        }
        return ConsentimentoResposta.de(consentimentos.registrarRegiao(UUID.fromString(token.getSubject()), pedido.aceito(),
                pedido.versaoTexto()), fuso);
    }
}
