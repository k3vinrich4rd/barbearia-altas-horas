package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O único lugar onde a cobrança troca de status depois de criada. Cada troca é um UPDATE com o status de origem no
 * WHERE: duas transações (webhook, cancelamento pelo painel, devolução) nunca sobrescrevem uma à outra, e quem chega
 * por último encontra zero linhas em vez de desfazer o trabalho do outro (D77).
 */
@Component
public class TransicoesDeCobranca {

    private final JdbcClient jdbc;

    TransicoesDeCobranca(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Pagamento confirmado: só PENDENTE (ou CANCELADA por expiração/cancelamento, que ainda pode receber) vira PAGA. */
    public int pagar(UUID id, Instant agora) {
        return atualizar(id, "PAGA", agora, "'PENDENTE', 'CANCELADA'");
    }

    /** Dinheiro entrou sem horário (R14, D69) ou a reserva já estava cancelada: vira pendência de devolução do painel. */
    public int pagarAposExpiracao(UUID id, Instant agora) {
        return atualizar(id, "PAGA_APOS_EXPIRACAO", agora, "'PENDENTE', 'CANCELADA', 'PAGA'");
    }

    /** Reserva cancelada pela barbearia: paga vira devolução pendente; pendente vira cancelada (E10). */
    public void aoCancelarReserva(UUID reservaId, Instant agora) {
        OffsetDateTime quando = OffsetDateTime.ofInstant(agora, ZoneOffset.UTC);
        for (String[] troca : new String[][] {{"PAGA", "DEVOLUCAO_PENDENTE"}, {"PENDENTE", "CANCELADA"}}) {
            jdbc.sql("""
                    UPDATE cobranca SET status = :para, atualizado_em = :agora, version = version + 1
                    WHERE reserva_id = :reserva AND status = :de
                    """)
                    .param("para", troca[1]).param("de", troca[0]).param("reserva", reservaId).param("agora", quando)
                    .update();
        }
    }

    /** Pendente por expiração da reserva (E24). */
    public void aoExpirarReserva(UUID reservaId, Instant agora) {
        jdbc.sql("""
                UPDATE cobranca SET status = 'CANCELADA', atualizado_em = :agora, version = version + 1
                WHERE reserva_id = :reserva AND status = 'PENDENTE'
                """)
                .param("reserva", reservaId).param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC)).update();
    }

    /** Devolução registrada à mão (E13). Zero linhas = não existe, ou já não está devolvível. */
    public int devolver(UUID id, UUID por, Instant agora) {
        OffsetDateTime quando = OffsetDateTime.ofInstant(agora, ZoneOffset.UTC);
        return jdbc.sql("""
                UPDATE cobranca SET status = 'DEVOLVIDA', devolvida_em = :agora, devolvida_por = :por,
                                    atualizado_em = :agora, version = version + 1
                WHERE id = :id AND status IN ('DEVOLUCAO_PENDENTE', 'PAGA_APOS_EXPIRACAO')
                """)
                .param("agora", quando).param("por", por).param("id", id).update();
    }

    private int atualizar(UUID id, String para, Instant agora, String origens) {
        OffsetDateTime quando = OffsetDateTime.ofInstant(agora, ZoneOffset.UTC);
        return jdbc.sql("UPDATE cobranca SET status = :para, paga_em = :agora, atualizado_em = :agora, "
                + "version = version + 1 WHERE id = :id AND status IN (" + origens + ")")
                .param("para", para).param("agora", quando).param("id", id).update();
    }
}
