package io.github.k3vinrich4rd.altashoras.comum.valor;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * CPF só com dígitos, já validado pelos dígitos verificadores. Só passa em trânsito até o gateway e nunca é
 * guardado (ADR 0009, R9). O toString() mostra só os 2 últimos dígitos: um log ou uma exceção não vaza o número (R30).
 */
public record Cpf(String digitos) {

    /** Tira a máscara e valida; CPF errado é 400 CPF_INVALIDO. Rejeita 11 dígitos iguais (111.111.111-11 etc.). */
    public static Cpf de(String bruto) {
        String digitos = bruto == null ? "" : bruto.replaceAll("\\D", "");
        if (!valido(digitos)) {
            throw new ExcecaoDeNegocio(CodigoErro.CPF_INVALIDO);
        }
        return new Cpf(digitos);
    }

    /** ***.***.***-DD, o único formato que sai da API (cpfMascarado). */
    public String mascarado() {
        return "***.***.***-" + digitos.substring(9);
    }

    private static boolean valido(String d) {
        if (d.length() != 11 || d.chars().distinct().count() == 1) {
            return false;
        }
        return digito(d, 9) == d.charAt(9) - '0' && digito(d, 10) == d.charAt(10) - '0';
    }

    /** Dígito verificador das {@code quantos} primeiras posições: pesos de quantos+1 até 2, resto de 11. */
    private static int digito(String d, int quantos) {
        int soma = 0;
        for (int i = 0; i < quantos; i++) {
            soma += (d.charAt(i) - '0') * (quantos + 1 - i);
        }
        int resto = soma * 10 % 11;
        return resto == 10 ? 0 : resto;
    }

    @Override
    public String toString() {
        return mascarado();
    }
}
