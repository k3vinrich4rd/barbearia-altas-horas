package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.time.Instant;
import java.util.UUID;

/** O horário pedido, já validado: [inicio, fim) é o atendimento; até fimOcupacao entra o intervalo (R3). */
record Horario(UUID barbeiroId, UUID servicoId, Instant inicio, Instant fim, Instant fimOcupacao) {
}
