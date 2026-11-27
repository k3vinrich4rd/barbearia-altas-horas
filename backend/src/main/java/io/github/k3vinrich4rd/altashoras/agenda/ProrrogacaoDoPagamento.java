package io.github.k3vinrich4rd.altashoras.agenda;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * "Mais tempo para pagar" visto de fora da reserva (Fatia 14A, E9; Fatia 15, ADR 0015, E20): a janela, quantos pedidos a
 * reserva já usou e quantos restam. Função pura, sem Spring: a API do cliente, a do visitante e a agenda do painel contam
 * do mesmo jeito. A janela é sempre o último minuto do prazo, {@code [expiraEm - 60 s, expiraEm)}; cada pedido soma
 * {@code segundos} (a propriedade {@code app.agenda.prorrogacao-pagamento}) até {@code maximo} vezes.
 */
public record ProrrogacaoDoPagamento(boolean usada, int usadas, int maximo, int restantes, long segundos, Instant abreEm,
        Instant fechaEm) {

    /** O último minuto do prazo (E20): é aí que o botão aparece e o pedido é aceito. Não muda com {@code segundos}. */
    public static final Duration JANELA = Duration.ofSeconds(60);

    /**
     * Nulo quando não se aplica (E9): reserva manual, ou reserva que não está AGUARDANDO_PAGAMENTO dentro do prazo
     * (confirmada, cancelada, expirada, ou com o prazo já vencido e o job ainda não passado). Com os pedidos esgotados o
     * objeto continua presente, com {@code restantes} 0.
     */
    public static @Nullable ProrrogacaoDoPagamento de(String status, String origem, @Nullable Instant expiraEm,
            int usadas, Instant agora, Duration extra, int maximo) {
        if (!"AGUARDANDO_PAGAMENTO".equals(status) || "MANUAL".equals(origem) || expiraEm == null
                || !expiraEm.isAfter(agora)) {
            return null;
        }
        return new ProrrogacaoDoPagamento(usadas > 0, usadas, maximo, Math.max(0, maximo - usadas), extra.toSeconds(),
                expiraEm.minus(JANELA), expiraEm);
    }

    /**
     * Até quando o Pix precisa valer (E23, D191): o prazo de agora mais todos os pedidos que ainda restam. Na criação da
     * cobrança (nenhum pedido usado) é o início do prazo + {@code expiracaoPagamentoMin} + {@code maximo} × {@code extra}.
     */
    public static Instant prazoMaximo(Instant expiraEm, int usadas, Duration extra, int maximo) {
        return expiraEm.plus(extra.multipliedBy(Math.max(0, maximo - usadas)));
    }
}
