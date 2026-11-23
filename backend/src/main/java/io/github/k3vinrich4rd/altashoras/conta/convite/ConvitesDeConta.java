package io.github.k3vinrich4rd.altashoras.conta.convite;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A tabela {@code convite_conta} (V9). Cada operação é uma instrução só, para o banco decidir quem vence quando dois pedidos
 * chegam juntos (E4). Nada aqui guarda ou devolve o código: só o SHA-256.
 */
@Component
public class ConvitesDeConta {

    /** O convite pendente já travado (FOR UPDATE) para o aceite: dois aceites ao mesmo tempo esperam um pelo outro. */
    public record Pendente(UUID id, UUID barbeiroId, String nome, UUID criadoPor) {

        @Override
        public String toString() {
            return "Pendente[" + id + "]";
        }
    }

    private final JdbcClient jdbc;

    ConvitesDeConta(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Revoga o convite pendente do barbeiro, se houver. Devolve se havia. */
    public boolean revogarPendente(UUID barbeiroId, Instant agora) {
        return jdbc.sql("""
                UPDATE convite_conta SET revogado_em = :agora
                WHERE barbeiro_id = :barbeiro AND usado_em IS NULL AND revogado_em IS NULL
                """).param("agora", utc(agora)).param("barbeiro", barbeiroId).update() > 0;
    }

    public void inserir(UUID barbeiroId, String nome, String hash, Instant expiraEm, UUID criadoPor) {
        jdbc.sql("""
                INSERT INTO convite_conta (barbeiro_id, nome, token_hash, expira_em, criado_por)
                VALUES (:barbeiro, :nome, :hash, :expira, :criador)
                """).param("barbeiro", barbeiroId).param("nome", nome).param("hash", hash)
                .param("expira", utc(expiraEm)).param("criador", criadoPor).update();
    }

    /** O convite desse código, só se ainda vale em {@code agora}: não usado, não revogado e não vencido (E4). */
    public Optional<Pendente> travarValido(String hash, Instant agora) {
        return jdbc.sql("""
                SELECT id, barbeiro_id, nome, criado_por FROM convite_conta
                WHERE token_hash = :hash AND usado_em IS NULL AND revogado_em IS NULL AND expira_em > :agora
                FOR UPDATE
                """).param("hash", hash).param("agora", utc(agora))
                .query((rs, n) -> new Pendente(rs.getObject("id", UUID.class), rs.getObject("barbeiro_id", UUID.class),
                        rs.getString("nome"), rs.getObject("criado_por", UUID.class)))
                .optional();
    }

    /** Marca como usado. Só uma instrução afeta a linha: quem não afetou perdeu a disputa. */
    public boolean consumir(UUID id, Instant agora) {
        return jdbc.sql("""
                UPDATE convite_conta SET usado_em = :agora
                WHERE id = :id AND usado_em IS NULL AND revogado_em IS NULL AND expira_em > :agora
                """).param("agora", utc(agora)).param("id", id).update() == 1;
    }

    /** Soma uma falha com o código certo; na última permitida o convite é revogado. Devolve se revogou. */
    public boolean registrarFalha(UUID id, int maximo, Instant agora) {
        return Boolean.TRUE.equals(jdbc.sql("""
                UPDATE convite_conta
                SET tentativas = tentativas + 1,
                    revogado_em = CASE WHEN tentativas + 1 >= :maximo THEN :agora ELSE revogado_em END
                WHERE id = :id
                RETURNING revogado_em IS NOT NULL
                """).param("maximo", maximo).param("agora", utc(agora)).param("id", id).query(Boolean.class).single());
    }

    /** Quando vence o convite pendente (e ainda válido) de cada barbeiro: uma consulta só para a lista do painel. */
    public Map<UUID, Instant> pendentes(Instant agora) {
        Map<UUID, Instant> mapa = new HashMap<>();
        jdbc.sql("""
                SELECT barbeiro_id, expira_em FROM convite_conta
                WHERE usado_em IS NULL AND revogado_em IS NULL AND expira_em > :agora
                """).param("agora", utc(agora)).query((rs, n) -> {
                    mapa.put(rs.getObject("barbeiro_id", UUID.class), rs.getObject("expira_em", OffsetDateTime.class).toInstant());
                    return null;
                }).list();
        return mapa;
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
