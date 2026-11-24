package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.DestinoDoAviso.Aparelho;

/**
 * A tabela {@code inscricao_push} (V13): o aparelho do BARBEIRO. Endpoint e chaves são dado pessoal dele e nunca vão
 * para log (R30). Cada método roda na transação de quem chama.
 */
@Repository
class Inscricoes {

    /** O máximo de aparelhos por conta: o 11º substitui o mais antigo (E11). */
    static final int MAXIMO_POR_CONTA = 10;

    private final JdbcClient jdbc;

    Inscricoes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<Aparelho> daConta(UUID usuarioId) {
        return jdbc.sql("""
                SELECT endpoint, chave_p256dh, chave_auth FROM inscricao_push
                WHERE usuario_id = :usuario ORDER BY criada_em, id LIMIT :maximo
                """)
                .param("usuario", usuarioId).param("maximo", MAXIMO_POR_CONTA)
                .query((rs, n) -> new Aparelho(rs.getString("endpoint"), rs.getString("chave_p256dh"),
                        rs.getString("chave_auth")))
                .list();
    }

    /**
     * Guarda o aparelho na conta de quem chama. O mesmo {@code endpoint} é idempotente; vindo de outra conta, passa para a
     * nova (quem logou por último usa o aparelho, E11). Passou de 10, sai o mais antigo.
     */
    void guardar(UUID usuarioId, Aparelho aparelho, Instant agora) {
        jdbc.sql("""
                INSERT INTO inscricao_push (id, usuario_id, endpoint, chave_p256dh, chave_auth, criada_em)
                VALUES (:id, :usuario, :endpoint, :p256dh, :auth, :agora)
                ON CONFLICT (endpoint) DO UPDATE SET
                    criada_em = CASE WHEN inscricao_push.usuario_id = EXCLUDED.usuario_id
                                     THEN inscricao_push.criada_em ELSE EXCLUDED.criada_em END,
                    usuario_id = EXCLUDED.usuario_id, chave_p256dh = EXCLUDED.chave_p256dh,
                    chave_auth = EXCLUDED.chave_auth
                """)
                .param("id", UUID.randomUUID()).param("usuario", usuarioId).param("endpoint", aparelho.endpoint())
                .param("p256dh", aparelho.chaveP256dh()).param("auth", aparelho.chaveAuth())
                .param("agora", utc(agora)).update();
        jdbc.sql("""
                DELETE FROM inscricao_push WHERE id IN (
                    SELECT id FROM inscricao_push WHERE usuario_id = :usuario ORDER BY criada_em DESC, id OFFSET :maximo)
                """)
                .param("usuario", usuarioId).param("maximo", MAXIMO_POR_CONTA).update();
    }

    /** Só apaga se o endpoint for DA CONTA; de outra conta ou inexistente não muda nada (A30). */
    void remover(UUID usuarioId, String endpoint) {
        jdbc.sql("DELETE FROM inscricao_push WHERE usuario_id = :usuario AND endpoint = :endpoint")
                .param("usuario", usuarioId).param("endpoint", endpoint).update();
    }

    /** Inscrição morta (404 ou 410 do serviço de push): apagada na hora pelo próprio envio (E13). */
    void removerMorta(String endpoint) {
        jdbc.sql("DELETE FROM inscricao_push WHERE endpoint = :endpoint").param("endpoint", endpoint).update();
    }

    void marcarEnvioOk(String endpoint, Instant agora) {
        jdbc.sql("UPDATE inscricao_push SET ultimo_envio_ok = :agora WHERE endpoint = :endpoint")
                .param("agora", utc(agora)).param("endpoint", endpoint).update();
    }

    int apagarDaConta(UUID usuarioId) {
        return jdbc.sql("DELETE FROM inscricao_push WHERE usuario_id = :usuario").param("usuario", usuarioId).update();
    }

    /** Sem nenhum envio com sucesso desde o limite (e criada antes dele): o aparelho sumiu sem avisar (E13). */
    int apagarSemUsoDesde(Instant limite) {
        return jdbc.sql("DELETE FROM inscricao_push WHERE COALESCE(ultimo_envio_ok, criada_em) < :limite")
                .param("limite", utc(limite)).update();
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
