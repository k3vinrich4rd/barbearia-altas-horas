package io.github.k3vinrich4rd.altashoras.privacidade;

import java.time.Instant;
import java.util.function.IntSupplier;

import org.springframework.transaction.support.TransactionOperations;

/**
 * Porta da retenção (E18): o módulo dono do dado publica a limpeza dele e o job {@code RetencaoDeDados} só as chama.
 * Cada limpeza é idempotente (rodar de novo não muda nada), usa o relógio recebido e trabalha em lotes de até
 * {@value #TAMANHO_DO_LOTE} linhas, cada um na própria transação, para não travar a tabela inteira de uma vez.
 */
public interface LimpezaPorRetencao {

    int TAMANHO_DO_LOTE = 500;

    /** Nome técnico da limpeza: é o único texto que o job registra no log. */
    String nome();

    /** Limpa o que já passou do prazo em {@code agora} e devolve quantas linhas mudou. */
    int executar(Instant agora);

    /** Repete o lote (uma transação cada) até vir um lote incompleto; devolve o total de linhas afetadas. */
    static int emLotes(TransactionOperations transacao, IntSupplier lote) {
        int total = 0;
        int afetadas;
        do {
            Integer resultado = transacao.execute(status -> lote.getAsInt());
            afetadas = resultado == null ? 0 : resultado;
            total += afetadas;
        } while (afetadas == TAMANHO_DO_LOTE);
        return total;
    }
}
