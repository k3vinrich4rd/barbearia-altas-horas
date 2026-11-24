package io.github.k3vinrich4rd.altashoras.agenda.titular;

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
 * A agenda trocando o nome de exibição do barbeiro desligado por um pseudônimo (Fatia 10B, H33 revisada, A30): o nome fica
 * nas reservas por {@code app.privacidade.retencao-barbeiro-anos} (padrão 5) depois da última reserva dele (ou do desligamento,
 * se nunca atendeu), inclusive no instante exato. As reservas continuam, apontando para o mesmo id. Idempotente (o
 * pseudônimo não é trocado de novo), em lotes e sem travar quem está em uso. Não mexe em {@code version}: o prazo vem do
 * parecer do advogado (art. 10 da LGPD e o tipo de vínculo) e pode mudar só por configuração.
 */
@Component
class RetencaoDeBarbeiroDesligado implements LimpezaPorRetencao {

    static final String PREFIXO_DO_PSEUDONIMO = "Barbeiro desligado ";

    private final JdbcClient jdbc;
    private final TransactionTemplate transacao;
    private final int anos;

    RetencaoDeBarbeiroDesligado(JdbcClient jdbc, PlatformTransactionManager gerenciador,
            @Value("${app.privacidade.retencao-barbeiro-anos:5}") int anos) {
        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(gerenciador);
        this.anos = anos;
    }

    @Override
    public String nome() {
        return "barbeiro_desligado";
    }

    @Override
    public int executar(Instant agora) {
        OffsetDateTime limite = agora.atOffset(ZoneOffset.UTC).minusYears(anos);
        return LimpezaPorRetencao.emLotes(transacao, () -> jdbc.sql("""
                UPDATE barbeiro SET nome_exibicao = :prefixo || substr(id::text, 1, 8)
                WHERE id IN (SELECT b.id FROM barbeiro b
                             WHERE b.ativo = false AND b.nome_exibicao NOT LIKE :padrao
                               AND COALESCE((SELECT max(r.fim) FROM reserva r WHERE r.barbeiro_id = b.id), b.atualizado_em) <= :limite
                             ORDER BY b.id LIMIT :lote FOR UPDATE SKIP LOCKED)
                """)
                .param("prefixo", PREFIXO_DO_PSEUDONIMO).param("padrao", PREFIXO_DO_PSEUDONIMO + "%")
                .param("limite", limite).param("lote", LimpezaPorRetencao.TAMANHO_DO_LOTE).update());
    }
}
