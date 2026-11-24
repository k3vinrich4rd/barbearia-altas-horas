package io.github.k3vinrich4rd.altashoras.agenda;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento público da agenda (ADR 0002, Fatia 14A, E6): o cliente (ou o visitante) acrescentou 1 minuto ao prazo do Pix.
 * Vai pelo outbox (event_publication) na mesma transação que muda o {@code expira_em} da reserva; o módulo de pagamento
 * escuta e acompanha o prazo da cobrança pendente, sem chamar o gateway. Só o id e os dois prazos: nenhum dado pessoal
 * (R30, o outbox grava o JSON).
 */
public record PrazoDaReservaProrrogado(UUID reservaId, Instant expiraEmAnterior, Instant expiraEmNovo) {
}
