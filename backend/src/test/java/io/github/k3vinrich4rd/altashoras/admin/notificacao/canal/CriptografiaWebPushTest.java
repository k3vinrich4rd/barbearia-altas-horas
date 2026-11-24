package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A criptografia do Web Push escrita com o JDK (D172): confere contra o exemplo oficial da RFC 8291 (apêndice A) e faz o
 * caminho de volta, do lado do aparelho, com os mesmos passos da RFC.
 */
class CriptografiaWebPushTest {

    private static byte[] b64(String valor) {
        return Base64.getUrlDecoder().decode(valor);
    }

    @Test
    @DisplayName("A20: o corpo cifrado bate com o exemplo da RFC 8291 (mesmas chaves, sal e texto): o JDK cifra igual à biblioteca de referência")
    void bateComOVetorDaRfc() throws Exception {
        var efemero = ChavesDeTeste.daBase64(
                "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
                "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");

        var cifrado = CriptografiaWebPush.cifrar("When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
                b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
                b64("BTBZMqHH6r4Tts7J_aSIgg"), efemero, b64("DGv6ra1nlYgDCS1FRnbzlw"));

        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(cifrado.corpo())).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    @DisplayName("A20: ida e volta: o aparelho abre o corpo com a chave privada dele e acha o texto; o texto não aparece em claro")
    void idaEVolta() throws Exception {
        var aparelho = ChavesDeTeste.aparelho();
        String texto = "Novo horário: Corte Degradê, qui 27/11 às 10:00";

        var cifrado = CriptografiaWebPush.cifrar(texto.getBytes(StandardCharsets.UTF_8), b64(aparelho.p256dh()), aparelho.segredo());

        assertThat(new String(cifrado.corpo(), StandardCharsets.ISO_8859_1)).doesNotContain("Novo horário");
        assertThat(abrir(cifrado.corpo(), aparelho)).isEqualTo(texto);
        // Cada mensagem tem sal e chave efêmera novos.
        var outro = CriptografiaWebPush.cifrar(texto.getBytes(StandardCharsets.UTF_8), b64(aparelho.p256dh()), aparelho.segredo());
        assertThat(outro.corpo()).isNotEqualTo(cifrado.corpo());
    }

    /** O lado do aparelho (RFC 8291, seção 3.4): ECDH com a privada dele, HKDF e AES-128-GCM. */
    static String abrir(byte[] corpo, ChavesDeTeste.Aparelho aparelho) throws Exception {
        byte[] sal = Arrays.copyOfRange(corpo, 0, 16);
        int tamanhoDoId = corpo[20] & 0xff;
        byte[] chaveDoServidor = Arrays.copyOfRange(corpo, 21, 21 + tamanhoDoId);
        byte[] cifrado = Arrays.copyOfRange(corpo, 21 + tamanhoDoId, corpo.length);
        assertThat(ByteBuffer.wrap(corpo, 16, 4).getInt()).isEqualTo(4096);

        KeyAgreement acordo = KeyAgreement.getInstance("ECDH");
        acordo.init(aparelho.par().chaves().getPrivate());
        acordo.doPhase(CriptografiaWebPush.chavePublica(chaveDoServidor), true);
        byte[] ecdh = acordo.generateSecret();

        byte[] prkChave = CriptografiaWebPush.hmac(aparelho.segredo(), ecdh);
        byte[] meuPonto = CriptografiaWebPush.pontoNaoComprimido((ECPublicKey) aparelho.par().chaves().getPublic());
        byte[] info = juntar("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), meuPonto, chaveDoServidor);
        byte[] ikm = Arrays.copyOf(CriptografiaWebPush.hmac(prkChave, juntar(info, new byte[] {1})), 32);
        byte[] prk = CriptografiaWebPush.hmac(sal, ikm);
        byte[] cek = Arrays.copyOf(CriptografiaWebPush.hmac(prk,
                juntar("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 16);
        byte[] nonce = Arrays.copyOf(CriptografiaWebPush.hmac(prk,
                juntar("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 12);

        Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
        cifra.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] aberto = cifra.doFinal(cifrado);
        assertThat(aberto[aberto.length - 1]).as("delimitador do último registro").isEqualTo((byte) 2);
        return new String(aberto, 0, aberto.length - 1, StandardCharsets.UTF_8);
    }

    private static byte[] juntar(byte[]... partes) {
        var saida = new java.io.ByteArrayOutputStream();
        for (byte[] parte : partes) {
            saida.writeBytes(parte);
        }
        return saida.toByteArray();
    }
}
