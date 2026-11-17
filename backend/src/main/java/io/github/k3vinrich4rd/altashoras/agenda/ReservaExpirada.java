package io.github.k3vinrich4rd.altashoras.agenda;

import java.util.UUID;

/**
 * Evento público da agenda (ADR 0002): o prazo de pagamento da reserva acabou e o horário foi liberado.
 * Vai pelo outbox (event_publication) na mesma transação que muda o status; o módulo de pagamento escuta
 * para cancelar a cobrança pendente (E24). Só o id: nenhum dado pessoal no evento.
 */
public record ReservaExpirada(UUID reservaId) {
}
