package io.github.k3vinrich4rd.altashoras.comum.valor;

/** Telefone só com dígitos (DDD e, se vier, DDI). O toString() mostra apenas os 4 últimos. */
public record Telefone(String digitos) {

    public static final int MINIMO = 10;
    public static final int MAXIMO = 13;

    public static Telefone de(String bruto) {
        return new Telefone(bruto.replaceAll("\\D", ""));
    }

    public boolean valido() {
        return digitos.length() >= MINIMO && digitos.length() <= MAXIMO;
    }

    @Override
    public String toString() {
        int visiveis = Math.min(4, digitos.length());
        return "*".repeat(digitos.length() - visiveis) + digitos.substring(digitos.length() - visiveis);
    }
}
