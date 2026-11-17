package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.agenda.ReservaExpirada;

/**
 * AGUARDANDO_PAGAMENTO → EXPIRADO quando o prazo venceu, por SQL em lote (a única origem válida está no WHERE,
 * igual à máquina de estados). Chamado sempre DENTRO de uma transação: o evento ReservaExpirada vai para o outbox
 * na mesma transação da mudança (E11).
 */
@Component
class ExpiracaoDeReservas {

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher eventos;

    ExpiracaoDeReservas(JdbcClient jdbc, ApplicationEventPublisher eventos) {
        this.jdbc = jdbc;
        this.eventos = eventos;
    }

    /**
     * Expiração preguiçosa (R16, E3): as pendentes vencidas que sobrepõem o horário pedido saem do caminho antes do
     * INSERT. Sem isso, a constraint recusaria um horário que a tela mostrou livre (a disponibilidade já as ignora).
     * ORDER BY id no FOR UPDATE: duas transações travam as linhas na mesma ordem, o que diminui deadlock.
     */
    List<UUID> vencidasNoHorario(UUID barbeiroId, Instant inicio, Instant fimOcupacao, Instant agora) {
        List<UUID> ids = jdbc.sql("""
                UPDATE reserva SET status = 'EXPIRADO', atualizado_em = :agora, version = version + 1
                WHERE id IN (SELECT id FROM reserva
                             WHERE barbeiro_id = :barbeiro AND status = 'AGUARDANDO_PAGAMENTO' AND expira_em <= :agora
                               AND inicio < :fimOcupacao AND fim_ocupacao > :inicio
                             ORDER BY id FOR UPDATE)
                RETURNING id
                """)
                .param("barbeiro", barbeiroId)
                .param("inicio", utc(inicio))
                .param("fimOcupacao", utc(fimOcupacao))
                .param("agora", utc(agora))
                .query(UUID.class).list();
        publicar(ids);
        return ids;
    }

    /**
     * Um lote do job (R17, E10). SKIP LOCKED: linha travada por uma reserva em andamento fica para a próxima rodada,
     * em vez de o job esperar (ou entrar em deadlock com ela).
     */
    List<UUID> lote(Instant agora, int tamanho) {
        List<UUID> ids = jdbc.sql("""
                UPDATE reserva SET status = 'EXPIRADO', atualizado_em = :agora, version = version + 1
                WHERE id IN (SELECT id FROM reserva
                             WHERE status = 'AGUARDANDO_PAGAMENTO' AND expira_em <= :agora
                             ORDER BY id LIMIT :tamanho FOR UPDATE SKIP LOCKED)
                RETURNING id
                """)
                .param("agora", utc(agora))
                .param("tamanho", tamanho)
                .query(UUID.class).list();
        publicar(ids);
        return ids;
    }

    private void publicar(List<UUID> ids) {
        ids.forEach(id -> eventos.publishEvent(new ReservaExpirada(id)));
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
