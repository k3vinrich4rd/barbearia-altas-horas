package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.agenda.ReservaExpirada;

/**
 * O pagamento ouvindo a agenda por evento, sem chamar o código dela (ADR 0002, E24): reserva expirada não pode
 * ficar com cobrança viva. Roda depois do commit da expiração, numa transação própria; o outbox marca o evento
 * como concluído só quando este método termina sem erro (senão ele é reenviado).
 */
@Component
class CancelaCobrancaDaReservaExpirada {

    private final JdbcClient jdbc;
    private final Clock clock;

    CancelaCobrancaDaReservaExpirada(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void aoExpirar(ReservaExpirada evento) {
        jdbc.sql("""
                UPDATE cobranca SET status = 'CANCELADA', atualizado_em = :agora, version = version + 1
                WHERE reserva_id = :reserva AND status = 'PENDENTE'
                """)
                .param("reserva", evento.reservaId())
                .param("agora", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
    }
}
