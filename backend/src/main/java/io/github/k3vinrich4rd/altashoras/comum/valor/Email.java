package io.github.k3vinrich4rd.altashoras.comum.valor;

import java.util.Locale;

/** E-mail normalizado (sem espaços, em minúsculas). O toString() mascara: um log descuidado não vaza o endereço. */
public record Email(String valor) {

    public static Email de(String bruto) {
        return new Email(bruto.strip().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        int arroba = valor.indexOf('@');
        return arroba <= 0 ? "***" : valor.charAt(0) + "***" + valor.substring(arroba);
    }
}
