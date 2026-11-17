package io.github.k3vinrich4rd.altashoras.comum.valor;

/** CEP só com os 8 dígitos. O toString() mostra apenas os 3 últimos. */
public record Cep(String digitos) {

    public static Cep de(String bruto) {
        return new Cep(bruto.replaceAll("\\D", ""));
    }

    public boolean valido() {
        return digitos.length() == 8;
    }

    @Override
    public String toString() {
        return digitos.length() < 3 ? "***" : "*".repeat(digitos.length() - 3) + digitos.substring(digitos.length() - 3);
    }
}
