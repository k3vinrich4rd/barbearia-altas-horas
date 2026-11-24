package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

/**
 * O que um canal entrega (E10): título, texto e link relativo; {@code tag} junta repetidos no aparelho. Nunca nome,
 * WhatsApp, e-mail, CPF nem valor do cliente.
 */
public record AvisoParaEnvio(String titulo, String texto, String link, String tag) {
}
