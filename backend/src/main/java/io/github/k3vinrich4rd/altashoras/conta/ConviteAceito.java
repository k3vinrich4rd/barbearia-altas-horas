package io.github.k3vinrich4rd.altashoras.conta;

import java.util.UUID;

/**
 * Publicado dentro da transação do aceite (Fatia 10B): a conta do barbeiro nasceu (ou ganhou o papel BARBEIRO). A agenda
 * escuta e liga o cadastro do barbeiro à conta; o painel escuta e grava a auditoria. A conta não sabe quem escuta
 * (ADR 0002). O {@code ip} só vai para a auditoria, que o apaga em 1 ano (D99).
 */
public record ConviteAceito(UUID barbeiroId, UUID usuarioId, String ip) {
}
