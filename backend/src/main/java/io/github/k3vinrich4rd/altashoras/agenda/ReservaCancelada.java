package io.github.k3vinrich4rd.altashoras.agenda;

import java.util.UUID;

/**
 * Evento público da agenda (ADR 0002): a barbearia cancelou a reserva pelo painel (R22). Vai pelo outbox
 * (event_publication) na mesma transação do cancelamento, como {@link ReservaExpirada}; o módulo de pagamento escuta
 * para mandar a cobrança paga para devolução ou cancelar a pendente (E10). Só o id: nenhum dado pessoal no evento,
 * nem o motivo do cancelamento.
 */
public record ReservaCancelada(UUID reservaId) {
}
