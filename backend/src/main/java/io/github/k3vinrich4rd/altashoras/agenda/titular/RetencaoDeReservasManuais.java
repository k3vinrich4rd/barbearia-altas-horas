package io.github.k3vinrich4rd.altashoras.agenda.titular;

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
 * A agenda limpando o nome e a observação das reservas manuais (Fatia 6D, D126): o mesmo prazo do contato do visitante
 * ({@code app.privacidade.retencao-guest-dias}, padrão 90) contado do FIM da reserva, inclusive no dia exato. As duas
 * colunas viram NULL; a reserva, o serviço, o horário e o preço (referência) ficam, porque não identificam ninguém. Não
 * mexe em {@code version} nem em {@code atualizado_em}: a tela que estiver aberta não ganha um 409 por causa da limpeza.
 * Idempotente (só pega quem ainda guarda nome ou observação), em lotes de até 500 linhas por transação e sem travar
 * quem estiver sendo usado ({@code SKIP LOCKED}).
 */
@Component
class RetencaoDeReservasManuais implements LimpezaPorRetencao {

    private final JdbcClient jdbc;
    private final TransactionTemplate transacao;
    private final Duration prazo;

    RetencaoDeReservasManuais(JdbcClient jdbc, PlatformTransactionManager gerenciador,
            @Value("${app.privacidade.retencao-guest-dias:90}") int dias) {
        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(gerenciador);
        this.prazo = Duration.ofDays(dias);
    }

    @Override
    public String nome() {
        return "reserva_manual";
    }

    @Override
    public int executar(Instant agora) {
        OffsetDateTime limite = OffsetDateTime.ofInstant(agora.minus(prazo), ZoneOffset.UTC);
        return LimpezaPorRetencao.emLotes(transacao, () -> jdbc.sql("""
                UPDATE reserva SET nome_cliente = NULL, observacao = NULL
                WHERE id IN (SELECT id FROM reserva
                             WHERE origem = 'MANUAL' AND (nome_cliente IS NOT NULL OR observacao IS NOT NULL)
                               AND fim <= :limite
                             ORDER BY fim, id LIMIT :lote FOR UPDATE SKIP LOCKED)
                """)
                .param("limite", limite).param("lote", LimpezaPorRetencao.TAMANHO_DO_LOTE).update());
    }
}
