package io.github.k3vinrich4rd.altashoras.agenda;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento público da agenda (ADR 0002, Fatia 13): o pagamento confirmou a reserva. Vai pelo outbox
 * (event_publication) na mesma transação que muda o status para CONFIRMADO, e só quando o status MUDA (a chamada
 * repetida não publica). O painel escuta para avisar o barbeiro da reserva. Só ids e o instante: nenhum dado pessoal
 * no evento (R30, o outbox grava o JSON).
 */
public record ReservaConfirmada(UUID reservaId, UUID barbeiroId, Instant inicio) {
}
