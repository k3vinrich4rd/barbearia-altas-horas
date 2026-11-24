package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A19 e A20 (endurecimento da Fatia 13): o VAPID só nasce com um par que bate e um assunto válido, e o JWT é JSON bem escapado. */
class VapidTest {

    @Test
    @DisplayName("A19: a chave pública que não é a da privada derruba a construção, sem imprimir chave na mensagem")
    void parQueNaoBate() {
        ChavesDeTeste.Par um = ChavesDeTeste.par();
        ChavesDeTeste.Par outro = ChavesDeTeste.par();

        assertThatThrownBy(() -> new Vapid(outro.publica(), um.privada(), "https://site.exemplo.test"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(um.privada()).hasMessageNotContaining(outro.publica());
        assertThat(new Vapid(um.publica(), um.privada(), "https://site.exemplo.test").publica()).isEqualTo(um.publica());
    }

    @Test
    @DisplayName("A19: o assunto precisa começar com mailto: ou https:")
    void assuntoValido() {
        ChavesDeTeste.Par par = ChavesDeTeste.par();
        assertThat(new Vapid(par.publica(), par.privada(), "mailto:contato@exemplo.test")).isNotNull();
        for (String ruim : new String[] {"", "site.exemplo.test", "http://site.exemplo.test", "javascript:x", "ftp://x"}) {
            assertThatThrownBy(() -> new Vapid(par.publica(), par.privada(), ruim)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("A20: o JWT escapa aspas e barras do assunto e da audiência: o corpo continua um JSON com os dois campos certos")
    void jwtEscapado() {
        ChavesDeTeste.Par par = ChavesDeTeste.par();
        Vapid vapid = new Vapid(par.publica(), par.privada(), "https://site.exemplo.test/\"x\\y");

        String autorizacao = vapid.autorizacao("https://push.exemplo.test\"\n", Instant.parse("2026-11-24T12:00:00Z"));
        String corpo = new String(Base64.getUrlDecoder().decode(autorizacao.split("\\.")[1]), StandardCharsets.UTF_8);

        assertThat(corpo).contains("\"aud\":\"https://push.exemplo.test\\\"\\u000a\"").contains("\"sub\":\"https://site.exemplo.test/\\\"x\\\\y\"");
        assertThat(corpo).doesNotContain("\n");
    }

    @Test
    @DisplayName("A20: o segredo de autenticação do aparelho precisa ter 16 bytes")
    void authComDezesseisBytes() {
        ChavesDeTeste.Aparelho aparelho = ChavesDeTeste.aparelho();
        byte[] chave = Base64.getUrlDecoder().decode(aparelho.p256dh());
        assertThatThrownBy(() -> CriptografiaWebPush.cifrar(new byte[] {1}, chave, new byte[15])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CriptografiaWebPush.cifrar(new byte[] {1}, chave, new byte[17])).isInstanceOf(IllegalArgumentException.class);
        assertThat(CriptografiaWebPush.cifrar(new byte[] {1}, chave, aparelho.segredo()).corpo()).isNotEmpty();
    }
}
