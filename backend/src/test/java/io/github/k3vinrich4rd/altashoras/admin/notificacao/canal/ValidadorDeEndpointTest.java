package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A24 (parte unitária): a lista de hosts do Web Push, a defesa contra SSRF (E9). */
class ValidadorDeEndpointTest {

    private final ValidadorDeEndpoint validador = new ValidadorDeEndpoint(
            List.of("fcm.googleapis.com", "updates.push.services.mozilla.com", "*.push.apple.com", "*.notify.windows.com"));

    @Test
    @DisplayName("A24: aceita os serviços de push conhecidos em https, inclusive subdomínios dos curingas")
    void aceita() {
        assertThat(validador.aceita("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(validador.aceita("https://updates.push.services.mozilla.com/wpush/v2/abc")).isTrue();
        assertThat(validador.aceita("https://web.push.apple.com/QZ-abc")).isTrue();
        assertThat(validador.aceita("https://wns2-par02p.notify.windows.com/w/?token=abc")).isTrue();
        assertThat(validador.aceita("https://FCM.googleapis.com:443/fcm/send/abc")).isTrue();
    }

    @Test
    @DisplayName("A24: recusa host fora da lista, http, IP, localhost, porta fora da 443, usuário na URL, curinga sem subdomínio, parecidos e lixo")
    void recusa() {
        for (String endpoint : List.of("https://exemplo.interno/x", "http://fcm.googleapis.com/fcm/send/abc", "https://10.0.0.1/x",
                "https://127.0.0.1/x", "https://[::1]/x", "https://localhost/x", "https://fcm.googleapis.com:8443/x",
                "https://usuario:senha@fcm.googleapis.com/x", "https://push.apple.com/x", "https://apple.com/x",
                "https://fcm.googleapis.com.mal.exemplo/x", "https://mal-fcm.googleapis.com/x", "https://evilnotify.windows.com/x",
                "ftp://fcm.googleapis.com/x", "//fcm.googleapis.com/x", "fcm.googleapis.com/x", "", "   ", "https://",
                "https://fcm.googleapis.com/" + "a".repeat(700))) {
            assertThat(validador.aceita(endpoint)).as("recusa: %s", endpoint.length() > 80 ? "(enorme)" : endpoint).isFalse();
        }
        assertThat(validador.aceita(null)).isFalse();
    }
}
