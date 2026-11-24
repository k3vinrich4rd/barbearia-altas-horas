package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECPrivateKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Identificação do servidor perante o serviço de push (VAPID, RFC 8292) com o JDK: um JWT ES256 curto assinado com a chave
 * privada. As chaves são as de {@code web-push generate-vapid-keys}: a pública com 65 bytes (ponto não comprimido) e a
 * privada com 32 bytes, as duas em base64url. A chave privada nunca sai deste objeto: o {@code toString} não a imprime e
 * as mensagens de erro não repetem nenhum valor (R30, A19).
 */
public final class Vapid {

    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    /** O JWT vale por 12 horas, abaixo do limite de 24 h da RFC. */
    private static final Duration VALIDADE = Duration.ofHours(12);

    private final PrivateKey privada;
    private final String publica;
    private final String assunto;

    public Vapid(String publicaBase64Url, String privadaBase64Url, String assunto) {
        try {
            byte[] d = Base64.getUrlDecoder().decode(privadaBase64Url);
            byte[] pub = Base64.getUrlDecoder().decode(publicaBase64Url);
            if (pub.length != 65 || pub[0] != 4) {
                throw new IllegalArgumentException("chave VAPID pública inválida: esperado um ponto não comprimido de 65 bytes");
            }
            if (assunto == null || !(assunto.startsWith("mailto:") || assunto.startsWith("https:"))) {
                throw new IllegalArgumentException("assunto do VAPID precisa começar com mailto: ou https:");
            }
            this.privada = KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), CriptografiaWebPush.CURVA));
            this.publica = publicaBase64Url;
            this.assunto = assunto;
            conferirQueOParBate(pub);
        } catch (GeneralSecurityException | IllegalArgumentException falha) {
            // Nunca a mensagem original: ela pode trazer pedaço da chave.
            throw new IllegalArgumentException("par de chaves VAPID inválido (confira APP_NOTIFICACAO_VAPID_PUBLICA e _PRIVADA)");
        }
    }

    /** A pública tem de ser a da privada: assina um texto e confere com a pública. Par trocado seria 403 em todo envio. */
    private void conferirQueOParBate(byte[] pub) throws GeneralSecurityException {
        byte[] teste = "conferencia-do-par-vapid".getBytes(StandardCharsets.US_ASCII);
        Signature assinatura = Signature.getInstance("SHA256withECDSAinP1363Format");
        assinatura.initSign(privada);
        assinatura.update(teste);
        byte[] resultado = assinatura.sign();
        Signature conferencia = Signature.getInstance("SHA256withECDSAinP1363Format");
        conferencia.initVerify(CriptografiaWebPush.chavePublica(pub));
        conferencia.update(teste);
        if (!conferencia.verify(resultado)) {
            throw new IllegalArgumentException("a chave pública não é a da chave privada");
        }
    }

    /** Escapa o texto para dentro de um literal JSON (o assunto e a audiência vêm de configuração e de URL). */
    private static String json(String valor) {
        StringBuilder saida = new StringBuilder();
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '"' -> saida.append("\\\"");
                case '\\' -> saida.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        saida.append(String.format("\\u%04x", (int) c));
                    } else {
                        saida.append(c);
                    }
                }
            }
        }
        return saida.toString();
    }

    public String publica() {
        return publica;
    }

    /** O valor do cabeçalho {@code Authorization} para o serviço de push dono do {@code endpoint}. */
    public String autorizacao(String audiencia, Instant agora) {
        String cabecalho = BASE64URL.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.US_ASCII));
        String corpo = BASE64URL.encodeToString(("{\"aud\":\"" + json(audiencia) + "\",\"exp\":" + agora.plus(VALIDADE).getEpochSecond()
                + ",\"sub\":\"" + json(assunto) + "\"}").getBytes(StandardCharsets.UTF_8));
        String assinado = cabecalho + "." + corpo;
        try {
            Signature assinatura = Signature.getInstance("SHA256withECDSAinP1363Format");
            assinatura.initSign(privada);
            assinatura.update(assinado.getBytes(StandardCharsets.US_ASCII));
            return "vapid t=" + assinado + "." + BASE64URL.encodeToString(assinatura.sign()) + ", k=" + publica;
        } catch (GeneralSecurityException falha) {
            throw new IllegalStateException("falha ao assinar o VAPID");
        }
    }

    @Override
    public String toString() {
        return "Vapid[chave privada omitida]";
    }
}
