package io.github.k3vinrich4rd.altashoras.admin.auditoria;

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
 * O painel limpando o IP da auditoria (D77, E20). Passou {@code app.privacidade.retencao-ip-dias} (padrão 365) da data da
 * ação, o IP vira nulo. A LINHA fica (ator, ação, recurso, detalhe e data): continua provando quem fez o quê e já não tem
 * dado pessoal. Idempotente ({@code ip IS NOT NULL}), em lotes de até 500 linhas por transação.
 */
@Component
class RetencaoDoIpDaAuditoria implements LimpezaPorRetencao {

    private final JdbcClient jdbc;
    private final TransactionTemplate transacao;
    private final Duration prazo;

    RetencaoDoIpDaAuditoria(JdbcClient jdbc, PlatformTransactionManager gerenciador,
            @Value("${app.privacidade.retencao-ip-dias:365}") int dias) {
        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(gerenciador);
        this.prazo = Duration.ofDays(dias);
    }

    @Override
    public String nome() {
        return "auditoria_ip";
    }

    @Override
    public int executar(Instant agora) {
        OffsetDateTime limite = OffsetDateTime.ofInstant(agora.minus(prazo), ZoneOffset.UTC);
        return LimpezaPorRetencao.emLotes(transacao, () -> jdbc.sql("""
                UPDATE auditoria_admin SET ip = NULL
                WHERE id IN (SELECT id FROM auditoria_admin WHERE ip IS NOT NULL AND ocorrido_em <= :limite
                             ORDER BY ocorrido_em, id LIMIT :lote FOR UPDATE SKIP LOCKED)
                """)
                .param("limite", limite).param("lote", LimpezaPorRetencao.TAMANHO_DO_LOTE)
                .update());
    }
}
