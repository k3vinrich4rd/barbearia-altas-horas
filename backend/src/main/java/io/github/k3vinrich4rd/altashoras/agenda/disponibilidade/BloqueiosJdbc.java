package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Bloqueio;

/**
 * Adaptador SQL da porta FonteDeBloqueios. Uma consulta só para folgas e reservas (UNION ALL), com a mesma
 * definição de "ocupa horário" da constraint reserva_sem_sobreposicao, mais o prazo de pagamento (R15):
 * a pendente vencida já não bloqueia, mesmo antes de o job de expiração rodar.
 * SQL direto de propósito: é leitura de duas tabelas numa consulta só, sem precisar da entidade Reserva.
 */
@Repository
class BloqueiosJdbc implements FonteDeBloqueios {

    private static final String SQL = """
            SELECT barbeiro_id, inicio, fim, true AS folga
            FROM folga
            WHERE inicio < :fim AND fim > :inicio
            UNION ALL
            SELECT barbeiro_id, inicio, fim_ocupacao, false
            FROM reserva
            WHERE inicio < :fim AND fim_ocupacao > :inicio
              AND (status = 'CONFIRMADO' OR (status = 'AGUARDANDO_PAGAMENTO' AND expira_em > :agora))
            """;

    private final JdbcClient jdbc;

    BloqueiosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, List<Bloqueio>> noPeriodo(Instant inicio, Instant fim, Instant agora) {
        Map<UUID, List<Bloqueio>> porBarbeiro = new HashMap<>();
        jdbc.sql(SQL)
                .param("inicio", utc(inicio))
                .param("fim", utc(fim))
                .param("agora", utc(agora))
                .query((rs, linha) -> {
                    UUID barbeiro = rs.getObject(1, UUID.class);
                    Bloqueio bloqueio = new Bloqueio(rs.getObject(2, OffsetDateTime.class).toInstant(),
                            rs.getObject(3, OffsetDateTime.class).toInstant(), rs.getBoolean(4));
                    porBarbeiro.computeIfAbsent(barbeiro, id -> new ArrayList<>()).add(bloqueio);
                    return bloqueio;
                })
                .list();
        return porBarbeiro;
    }

    @Override
    public Map<UUID, List<Bloqueio>> folgasNoPeriodo(Instant inicio, Instant fim) {
        Map<UUID, List<Bloqueio>> porBarbeiro = new HashMap<>();
        jdbc.sql("SELECT barbeiro_id, inicio, fim FROM folga WHERE inicio < :fim AND fim > :inicio")
                .param("inicio", utc(inicio))
                .param("fim", utc(fim))
                .query((rs, linha) -> porBarbeiro.computeIfAbsent(rs.getObject(1, UUID.class), id -> new ArrayList<>())
                        .add(new Bloqueio(rs.getObject(2, OffsetDateTime.class).toInstant(),
                                rs.getObject(3, OffsetDateTime.class).toInstant(), true)))
                .list();
        return porBarbeiro;
    }

    /** O driver do PostgreSQL não aceita Instant direto; OffsetDateTime em UTC é o tipo de timestamptz. */
    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
