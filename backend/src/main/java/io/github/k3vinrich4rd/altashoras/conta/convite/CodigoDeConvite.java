package io.github.k3vinrich4rd.altashoras.conta.convite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * O código do convite: 32 bytes de {@link SecureRandom} em base64url (43 caracteres, 256 bits). Com tanta entropia o banco
 * guarda só o SHA-256 (sem BCrypt): o código não é senha, expira, serve uma vez e se revoga (E3).
 */
public final class CodigoDeConvite {

    public static final int TAMANHO = 43;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private CodigoDeConvite() {
    }

    public static String gerar() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 em hexadecimal minúsculo (64 caracteres), o que a coluna {@code token_hash} guarda. */
    public static String hash(String codigo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }
}
