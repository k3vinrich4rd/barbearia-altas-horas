package io.github.k3vinrich4rd.altashoras.privacidade.titular;

/**
 * Formata o canal de contato da política quando ele é um telefone brasileiro (celular de 11 dígitos ou
 * fixo de 10). E-mail e qualquer outro texto (inclusive o fallback de "ainda não configurado") ficam iguais.
 * Nenhum número comercial entra no código: o valor vem de {@code APP_PRIVACIDADE_CONTATO}.
 */
final class FormatadorDeCanalDeContato {

    private FormatadorDeCanalDeContato() {
    }

    static String formatar(String canal) {
        if (canal == null || canal.isBlank()) {
            return canal;
        }
        String bruto = canal.strip();
        if (bruto.indexOf('@') >= 0) {
            return bruto;
        }
        String digitos = bruto.replaceAll("\\D", "");
        // 13 dígitos (celular) ou 12 (fixo) começando com 55: o prefixo do país, com ou sem +.
        if ((digitos.length() == 13 || digitos.length() == 12) && digitos.startsWith("55")) {
            digitos = digitos.substring(2);
        }
        if (digitos.length() == 11) {
            return "(%s) %s-%s".formatted(digitos.substring(0, 2), digitos.substring(2, 7), digitos.substring(7));
        }
        if (digitos.length() == 10) {
            return "(%s) %s-%s".formatted(digitos.substring(0, 2), digitos.substring(2, 6), digitos.substring(6));
        }
        return bruto;
    }
}
