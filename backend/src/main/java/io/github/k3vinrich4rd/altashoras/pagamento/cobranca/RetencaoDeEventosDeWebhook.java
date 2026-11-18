package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.privacidade.LimpezaPorRetencao;

/**
 * O pagamento limpando os eventos de webhook antigos (E21, ADR 0009). Só os JÁ PROCESSADOS ({@code processado_em}
 * preenchido) com mais de {@code app.privacidade.retencao-webhook-dias} (padrão 365) desde o recebimento: os que ainda
 * aguardam ou estão em revisão manual ficam, porque alguém ainda precisa deles. A tabela só guarda ids e resultado (sem
 * payload), então isto é limpeza de tamanho de tabela (o Aiven free tem 1 GB), não de dado pessoal. Em lotes de até 500.
 */
@Component
class RetencaoDeEventosDeWebhook implements LimpezaPorRetencao {

    private final JdbcClient jdbc;
    private final TransactionTemplate transacao;
    private final Duration prazo;

    RetencaoDeEventosDeWebhook(JdbcClient jdbc, PlatformTransactionManager gerenciador,
            @Value("${app.privacidade.retencao-webhook-dias:365}") int dias) {
        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(gerenciador);
        this.prazo = Duration.ofDays(dias);
    }

    @Override
    public String nome() {
        return "evento_webhook";
    }

    @Override
    public int executar(Instant agora) {
        OffsetDateTime limite = OffsetDateTime.ofInstant(agora.minus(prazo), ZoneOffset.UTC);
        return LimpezaPorRetencao.emLotes(transacao, () -> jdbc.sql("""
                DELETE FROM evento_webhook
                WHERE id IN (SELECT id FROM evento_webhook WHERE processado_em IS NOT NULL AND recebido_em <= :limite
                             ORDER BY recebido_em, id LIMIT :lote FOR UPDATE SKIP LOCKED)
                """)
                .param("limite", limite).param("lote", LimpezaPorRetencao.TAMANHO_DO_LOTE)
                .update());
    }
}
