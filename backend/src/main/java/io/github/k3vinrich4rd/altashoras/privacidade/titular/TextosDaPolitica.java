package io.github.k3vinrich4rd.altashoras.privacidade.titular;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.privacidade.Consentimentos;

/**
 * Os textos versionados (E13): um arquivo por versão em {@code resources/privacidade/}. Texto novo = arquivo novo e
 * {@link Consentimentos#VERSAO_DO_TEXTO} nova; os arquivos antigos ficam, porque a prova do consentimento precisa do
 * texto exato que a pessoa viu. Carrega na subida: arquivo faltando derruba a API na hora, não na primeira consulta.
 * O canal de contato vem de {@code app.privacidade.contato} (variável de ambiente), nunca do repositório.
 */
@Component
class TextosDaPolitica {

    /** Texto do "canal de contato" quando a variável não foi configurada. */
    static final String SEM_CONTATO = "o canal de contato que a barbearia divulgar no estabelecimento "
            + "(ainda não configurado nesta instalação)";

    /** A data em que o texto da versão vigente foi escrito (muda junto com a versão). */
    static final String ATUALIZADA_EM = "2026-11-17T00:00:00-03:00";

    private final String consentimento;
    private final String politica;

    TextosDaPolitica(@Value("${app.privacidade.contato:}") String contato,
            @Value("${app.privacidade.retencao-guest-dias:90}") int retencaoGuestDias,
            @Value("${app.privacidade.retencao-ip-dias:365}") int retencaoIpDias,
            @Value("${app.privacidade.retencao-webhook-dias:365}") int retencaoWebhookDias) {
        String versao = Consentimentos.VERSAO_DO_TEXTO;
        this.consentimento = ler("privacidade/consentimento-" + versao + ".md").strip();
        this.politica = ler("privacidade/politica-" + versao + ".md").strip()
                .replace("{{contato}}", contato == null || contato.isBlank() ? SEM_CONTATO : contato.strip())
                .replace("{{retencaoGuestDias}}", Integer.toString(retencaoGuestDias))
                .replace("{{retencaoIpDias}}", Integer.toString(retencaoIpDias))
                .replace("{{retencaoWebhookDias}}", Integer.toString(retencaoWebhookDias));
    }

    String consentimento() {
        return consentimento;
    }

    String politica() {
        return politica;
    }

    private static String ler(String caminho) {
        try {
            return new String(new ClassPathResource(caminho).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException falha) {
            throw new UncheckedIOException("Texto de privacidade ausente: " + caminho, falha);
        }
    }
}
