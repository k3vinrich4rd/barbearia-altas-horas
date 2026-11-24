package io.github.k3vinrich4rd.altashoras.privacidade;

import java.util.UUID;

/**
 * Evento público da privacidade (ADR 0002, Fatia 11, E3): o titular retirou a autorização de região. É publicado dentro da
 * transação da revogação e entregue de forma SÍNCRONA aos {@code @EventListener} dos módulos que guardam a localidade dele:
 * a conta apaga bairro, cidade, uf e município do perfil, e a agenda apaga a região de todas as reservas da conta. Só o id:
 * nenhum dado pessoal no evento.
 */
public record RegiaoRevogada(UUID usuarioId) {
}
