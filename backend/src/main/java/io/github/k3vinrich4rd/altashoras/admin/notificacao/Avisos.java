package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * A tabela {@code aviso_barbeiro} (V13), do painel. Sem nome nem contato de cliente em nenhuma coluna (E2, E10). Cada
 * método roda na transação de quem chama.
 */
@Repository
class Avisos {

    /** Um aviso gravado agora, ou o que já existia: {@code jaEntregue} diz se o push desta reserva já saiu (E6). */
    record Gravado(UUID id, boolean jaEntregue) {
    }

    /** Uma linha da lista "Novidades". */
    record Linha(UUID id, UUID reservaId, String servico, Instant inicio, Instant criadoEm, boolean lido) {
    }

    private final JdbcClient jdbc;

    Avisos(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Um aviso por reserva e conta: a reentrega do outbox encontra a mesma linha (E6, A9). */
    Gravado gravar(UUID usuarioId, UUID reservaId, String servico, Instant inicio, Instant agora) {
        jdbc.sql("""
                INSERT INTO aviso_barbeiro (id, usuario_id, reserva_id, servico, inicio, criado_em)
                VALUES (:id, :usuario, :reserva, :servico, :inicio, :agora)
                ON CONFLICT (reserva_id, usuario_id) DO NOTHING
                """)
                .param("id", UUID.randomUUID()).param("usuario", usuarioId).param("reserva", reservaId)
                .param("servico", servico).param("inicio", utc(inicio)).param("agora", utc(agora)).update();
        return jdbc.sql("""
                SELECT id, push_enviado_em IS NOT NULL AS entregue FROM aviso_barbeiro
                WHERE reserva_id = :reserva AND usuario_id = :usuario
                """)
                .param("reserva", reservaId).param("usuario", usuarioId)
                .query((rs, n) -> new Gravado(rs.getObject("id", UUID.class), rs.getBoolean("entregue"))).single();
    }

    /** Soma uma tentativa falha de entrega e devolve o total (D178). */
    int registrarTentativa(UUID id) {
        return jdbc.sql("UPDATE aviso_barbeiro SET tentativas = tentativas + 1 WHERE id = :id RETURNING tentativas")
                .param("id", id).query(Integer.class).single();
    }

    void marcarEntregue(UUID id, Instant agora) {
        jdbc.sql("UPDATE aviso_barbeiro SET push_enviado_em = :agora WHERE id = :id AND push_enviado_em IS NULL")
                .param("agora", utc(agora)).param("id", id).update();
    }

    /** Uma consulta indexada ({@code aviso_barbeiro_lista_idx}); a contagem é outra, separada (E14). */
    List<Linha> listar(UUID usuarioId, int limite) {
        return jdbc.sql("""
                SELECT id, reserva_id, servico, inicio, criado_em, lido_em IS NOT NULL AS lido
                FROM aviso_barbeiro WHERE usuario_id = :usuario ORDER BY criado_em DESC, id LIMIT :limite
                """)
                .param("usuario", usuarioId).param("limite", limite)
                .query((rs, n) -> new Linha(rs.getObject("id", UUID.class), rs.getObject("reserva_id", UUID.class),
                        rs.getString("servico"), rs.getObject("inicio", OffsetDateTime.class).toInstant(),
                        rs.getObject("criado_em", OffsetDateTime.class).toInstant(), rs.getBoolean("lido")))
                .list();
    }

    long naoLidas(UUID usuarioId) {
        return jdbc.sql("SELECT count(*) FROM aviso_barbeiro WHERE usuario_id = :usuario AND lido_em IS NULL")
                .param("usuario", usuarioId).query(Long.class).single();
    }

    void marcarLidas(UUID usuarioId, Instant agora) {
        jdbc.sql("UPDATE aviso_barbeiro SET lido_em = :agora WHERE usuario_id = :usuario AND lido_em IS NULL")
                .param("agora", utc(agora)).param("usuario", usuarioId).update();
    }

    int apagarDaConta(UUID usuarioId) {
        return jdbc.sql("DELETE FROM aviso_barbeiro WHERE usuario_id = :usuario").param("usuario", usuarioId).update();
    }

    int apagarCriadosAntesDe(Instant limite) {
        return jdbc.sql("DELETE FROM aviso_barbeiro WHERE criado_em < :limite").param("limite", utc(limite)).update();
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
