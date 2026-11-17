package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.jspecify.annotations.Nullable;

/**
 * O "login" do visitante numa reserva só (E7): 32 bytes aleatórios, prefixo g1_ (versão do formato), guardado
 * como SHA-256. Comparação em tempo constante, para o tempo de resposta não dizer quantos caracteres acertaram.
 */
public final class TokenGuest {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private TokenGuest() {
    }

    public static String novo() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return "g1_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }

    public static boolean confere(@Nullable String token, String hashGuardado) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.US_ASCII),
                hashGuardado.getBytes(StandardCharsets.US_ASCII));
    }
}
