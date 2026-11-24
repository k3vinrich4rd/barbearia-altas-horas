package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Criptografia do corpo do Web Push com o JDK, sem biblioteca (D-2, D172): RFC 8291 (criptografia de mensagem) no formato
 * {@code aes128gcm} da RFC 8188, um único registro. Só o serviço de push (Google, Mozilla, Apple) vê o corpo fechado; só o
 * aparelho do barbeiro o abre. Sem estado: cada mensagem usa um par de chaves efêmero e um sal novos.
 */
public final class CriptografiaWebPush {

    /** O corpo cifrado já com o cabeçalho (sal, tamanho do registro, id da chave) pronto para o POST. */
    public record Cifrado(byte[] corpo) {
    }

    private static final int TAMANHO_DO_REGISTRO = 4096;
    private static final int TAMANHO_DA_CHAVE_PUBLICA = 65;
    private static final SecureRandom ALEATORIO = new SecureRandom();
    static final ECParameterSpec CURVA = curvaP256();

    private CriptografiaWebPush() {
    }

    /** Cifra para o aparelho com um par efêmero e um sal aleatórios. */
    public static Cifrado cifrar(byte[] texto, byte[] chavePublicaDoAparelho, byte[] segredoDeAutenticacao) {
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("EC");
            gerador.initialize(new ECGenParameterSpec("secp256r1"), ALEATORIO);
            byte[] sal = new byte[16];
            ALEATORIO.nextBytes(sal);
            return cifrar(texto, chavePublicaDoAparelho, segredoDeAutenticacao, gerador.generateKeyPair(), sal);
        } catch (GeneralSecurityException falha) {
            throw new IllegalStateException("falha ao cifrar o aviso", falha);
        }
    }

    /** Versão com o par efêmero e o sal fixos, para conferir contra o vetor de teste da RFC 8291. */
    static Cifrado cifrar(byte[] texto, byte[] chavePublicaDoAparelho, byte[] segredoDeAutenticacao,
            KeyPair efemero, byte[] sal) {
        try {
            byte[] minhaPublica = pontoNaoComprimido((ECPublicKey) efemero.getPublic());
            ECPublicKey deles = chavePublica(chavePublicaDoAparelho);
            KeyAgreement acordo = KeyAgreement.getInstance("ECDH");
            acordo.init(efemero.getPrivate());
            acordo.doPhase(deles, true);
            byte[] segredoCompartilhado = acordo.generateSecret();

            byte[] prkChave = hmac(segredoDeAutenticacao, segredoCompartilhado);
            byte[] infoDaChave = concatenar("WebPush: info\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    chavePublicaDoAparelho, minhaPublica);
            byte[] ikm = expandir(prkChave, infoDaChave, 32);

            byte[] prk = hmac(sal, ikm);
            byte[] cek = expandir(prk, "Content-Encoding: aes128gcm\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 16);
            byte[] nonce = expandir(prk, "Content-Encoding: nonce\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 12);

            // Um registro só: o texto e o delimitador 0x02 do último registro (RFC 8188, seção 2).
            byte[] registro = concatenar(texto, new byte[] {2});
            if (registro.length + 16 > TAMANHO_DO_REGISTRO) {
                throw new IllegalArgumentException("texto grande demais para um registro");
            }
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] cifrado = cifra.doFinal(registro);

            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            saida.writeBytes(sal);
            saida.writeBytes(ByteBuffer.allocate(4).putInt(TAMANHO_DO_REGISTRO).array());
            saida.write(minhaPublica.length);
            saida.writeBytes(minhaPublica);
            saida.writeBytes(cifrado);
            return new Cifrado(saida.toByteArray());
        } catch (java.security.InvalidKeyException | java.security.spec.InvalidKeySpecException chaveRuim) {
            // Chave do aparelho que o JDK recusa (ponto fora da curva): o destino é inválido, não é falha passageira.
            throw new IllegalArgumentException("chave pública do aparelho inválida");
        } catch (GeneralSecurityException falha) {
            throw new IllegalStateException("falha ao cifrar o aviso", falha);
        }
    }

    /** A chave pública do aparelho (65 bytes, ponto não comprimido, o {@code p256dh}) como chave do JDK. */
    static ECPublicKey chavePublica(byte[] ponto) throws GeneralSecurityException {
        if (ponto.length != TAMANHO_DA_CHAVE_PUBLICA || ponto[0] != 4) {
            throw new IllegalArgumentException("chave pública do aparelho inválida");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(ponto, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(ponto, 33, 65));
        if (!pontoNaCurva(x, y)) {
            throw new IllegalArgumentException("chave pública do aparelho fora da curva");
        }
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), CURVA));
    }

    /** x e y menores que p e y² = x³ + ax + b (mod p): um ponto fora da curva abriria ataque de curva inválida no ECDH. */
    static boolean pontoNaCurva(BigInteger x, BigInteger y) {
        BigInteger p = ((java.security.spec.ECFieldFp) CURVA.getCurve().getField()).getP();
        if (x.signum() < 0 || y.signum() < 0 || x.compareTo(p) >= 0 || y.compareTo(p) >= 0) {
            return false;
        }
        BigInteger esquerda = y.multiply(y).mod(p);
        BigInteger direita = x.pow(3).add(CURVA.getCurve().getA().multiply(x)).add(CURVA.getCurve().getB()).mod(p);
        return esquerda.equals(direita);
    }

    /** O {@code p256dh} (65 bytes, não comprimido) é um ponto válido da curva P-256? Nunca lança. */
    public static boolean ehChavePublicaValida(byte[] ponto) {
        try {
            chavePublica(ponto);
            return true;
        } catch (GeneralSecurityException | IllegalArgumentException invalida) {
            return false;
        }
    }

    static byte[] pontoNaoComprimido(ECPublicKey chave) {
        byte[] saida = new byte[TAMANHO_DA_CHAVE_PUBLICA];
        saida[0] = 4;
        copiarSemSinal(chave.getW().getAffineX(), saida, 1);
        copiarSemSinal(chave.getW().getAffineY(), saida, 33);
        return saida;
    }

    private static void copiarSemSinal(BigInteger valor, byte[] destino, int inicio) {
        byte[] bytes = valor.toByteArray();
        int deslocamento = bytes.length > 32 ? bytes.length - 32 : 0;
        int tamanho = bytes.length - deslocamento;
        System.arraycopy(bytes, deslocamento, destino, inicio + 32 - tamanho, tamanho);
    }

    private static ECParameterSpec curvaP256() {
        try {
            AlgorithmParameters parametros = AlgorithmParameters.getInstance("EC");
            parametros.init(new ECGenParameterSpec("secp256r1"));
            return parametros.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException falha) {
            throw new ExceptionInInitializerError(falha);
        }
    }

    // ---- HKDF (RFC 5869) com HMAC-SHA-256; as saídas pedidas têm no máximo 32 bytes, então basta um bloco -------------------

    static byte[] hmac(byte[] chave, byte[] dados) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave.length == 0 ? new byte[32] : chave, "HmacSHA256"));
        return mac.doFinal(dados);
    }

    private static byte[] expandir(byte[] prk, byte[] info, int tamanho) throws GeneralSecurityException {
        return Arrays.copyOf(hmac(prk, concatenar(info, new byte[] {1})), tamanho);
    }

    private static byte[] concatenar(byte[]... partes) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        for (byte[] parte : partes) {
            saida.writeBytes(parte);
        }
        return saida.toByteArray();
    }
}
