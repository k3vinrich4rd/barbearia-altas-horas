package io.github.k3vinrich4rd.altashoras.privacidade;

import java.util.UUID;

/**
 * Evento público da privacidade (ADR 0002, E10): o titular pediu para excluir a própria conta. É publicado dentro da
 * transação da exclusão e entregue de forma SÍNCRONA aos {@code @EventListener} dos módulos que têm dado dele (não vai
 * pelo outbox: o {@code 204} precisa significar "acabou"). Quem não pode seguir lança exceção e a transação inteira
 * desfaz. A ordem é explícita com {@code @Order}: a agenda confere o bloqueio ANTES de a conta anonimizar. Só o id:
 * nenhum dado pessoal no evento.
 */
public record ExclusaoSolicitada(UUID usuarioId) {

    /** A agenda verifica reserva paga e cancela as não pagas. */
    public static final int ORDEM_DA_AGENDA = 10;

    /** A conta anonimiza e derruba as sessões, depois que a agenda liberou. */
    public static final int ORDEM_DA_CONTA = 20;
}
