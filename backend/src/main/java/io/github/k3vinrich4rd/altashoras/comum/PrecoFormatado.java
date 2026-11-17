package io.github.k3vinrich4rd.altashoras.comum;

/**
 * Centavos para "R$ 45,00". Feito à mão de propósito: o NumberFormat pt-BR põe um espaço não separável
 * (U+00A0) depois do R$, que quebra comparação de texto no front e nos testes (E8 da spec da Fatia 2).
 */
public final class PrecoFormatado {

    private PrecoFormatado() {
    }

    public static String de(long centavos) {
        if (centavos < 0) {
            throw new IllegalArgumentException("preço negativo");
        }
        String reais = Long.toString(centavos / 100);
        StringBuilder comMilhar = new StringBuilder();
        for (int i = 0; i < reais.length(); i++) {
            if (i > 0 && (reais.length() - i) % 3 == 0) {
                comMilhar.append('.');
            }
            comMilhar.append(reais.charAt(i));
        }
        long resto = centavos % 100;
        return "R$ " + comMilhar + "," + (resto < 10 ? "0" : "") + resto;
    }
}
