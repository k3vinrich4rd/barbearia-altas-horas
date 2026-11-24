package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;

/**
 * Chaves só de teste, geradas em tempo de execução (nenhuma chave de verdade fica no repositório): o par VAPID do servidor, no
 * mesmo formato de {@code web-push generate-vapid-keys}, e o par do "aparelho" (p256dh e auth) que o navegador entregaria.
 */
public final class ChavesDeTeste {

    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    /** Um par de chaves P-256 com a pública de 65 bytes e a privada de 32, em base64url. */
    public record Par(KeyPair chaves, String publica, String privada) {
    }

    /** O aparelho de um barbeiro: o par dele e o segredo de autenticação, como a inscrição do navegador. */
    public record Aparelho(Par par, String p256dh, String auth, byte[] segredo) {
    }

    private ChavesDeTeste() {
    }

    public static Par par() {
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("EC");
            gerador.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair chaves = gerador.generateKeyPair();
            byte[] d = trinta2(((ECPrivateKey) chaves.getPrivate()).getS());
            return new Par(chaves, BASE64URL.encodeToString(CriptografiaWebPush.pontoNaoComprimido((ECPublicKey) chaves.getPublic())),
                    BASE64URL.encodeToString(d));
        } catch (GeneralSecurityException falha) {
            throw new IllegalStateException(falha);
        }
    }

    public static Aparelho aparelho() {
        Par par = par();
        byte[] segredo = new byte[16];
        new java.security.SecureRandom().nextBytes(segredo);
        return new Aparelho(par, par.publica(), BASE64URL.encodeToString(segredo), segredo);
    }

    /** Monta o par de chaves a partir da privada (32 bytes) e da pública (65 bytes) em base64url. */
    public static KeyPair daBase64(String publica, String privada) throws GeneralSecurityException {
        KeyFactory fabrica = KeyFactory.getInstance("EC");
        var privadaJdk = fabrica.generatePrivate(new ECPrivateKeySpec(new BigInteger(1, Base64.getUrlDecoder().decode(privada)),
                CriptografiaWebPush.CURVA));
        return new KeyPair(CriptografiaWebPush.chavePublica(Base64.getUrlDecoder().decode(publica)), privadaJdk);
    }

    private static byte[] trinta2(BigInteger valor) {
        byte[] bytes = valor.toByteArray();
        byte[] saida = new byte[32];
        int deslocamento = bytes.length > 32 ? bytes.length - 32 : 0;
        int tamanho = bytes.length - deslocamento;
        System.arraycopy(bytes, deslocamento, saida, 32 - tamanho, tamanho);
        return saida;
    }
}
