package io.github.k3vinrich4rd.altashoras.agenda;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * "Acrescentar 1 minuto" visto de fora da reserva (Fatia 14A, E9): a janela e se já foi usada. Função pura, sem Spring:
 * a API do cliente, a do visitante e a agenda do painel contam a janela do mesmo jeito. A janela é o último trecho do prazo,
 * {@code [expiraEm - extra, expiraEm)}, e o mesmo {@code extra} é o tempo que a prorrogação soma.
 */
public record ProrrogacaoDoPagamento(boolean usada, long segundos, Instant abreEm, Instant fechaEm) {

    /**
     * Nulo quando não se aplica (E9): reserva manual, ou reserva que não está AGUARDANDO_PAGAMENTO dentro do prazo
     * (confirmada, cancelada, expirada, ou com o prazo já vencido e o job ainda não passado).
     */
    public static @Nullable ProrrogacaoDoPagamento de(String status, String origem, @Nullable Instant expiraEm,
            @Nullable Instant prorrogadaEm, Instant agora, Duration extra) {
        if (!"AGUARDANDO_PAGAMENTO".equals(status) || "MANUAL".equals(origem) || expiraEm == null
                || !expiraEm.isAfter(agora)) {
            return null;
        }
        return new ProrrogacaoDoPagamento(prorrogadaEm != null, extra.toSeconds(), expiraEm.minus(extra), expiraEm);
    }
}
