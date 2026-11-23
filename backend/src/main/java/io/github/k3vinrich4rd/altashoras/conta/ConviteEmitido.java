package io.github.k3vinrich4rd.altashoras.conta;

import java.time.Instant;

/**
 * O convite recém-gerado: o código aparece uma vez, na tela do dono, e nunca mais (só o SHA-256 fica no banco). O
 * {@code toString} não imprime o código, para um log descuidado não vazar o convite (R30).
 */
public record ConviteEmitido(String codigo, Instant expiraEm) {

    @Override
    public String toString() {
        return "ConviteEmitido[código omitido]";
    }
}
