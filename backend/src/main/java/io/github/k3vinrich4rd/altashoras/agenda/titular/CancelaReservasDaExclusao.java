package io.github.k3vinrich4rd.altashoras.agenda.titular;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.agenda.ReservaCancelada;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.privacidade.ExclusaoSolicitada;

/**
 * A agenda ouvindo a privacidade (ADR 0002, E10, E17). Listener SÍNCRONO, dentro da transação da exclusão, e o
 * PRIMEIRO da fila ({@code @Order} menor que o da conta): confere o bloqueio antes de qualquer dado sair.
 * <ul>
 *   <li>Reserva futura PAGA ({@code CONFIRMADO}): a exclusão é recusada (409 EXCLUSAO_BLOQUEADA_POR_RESERVA) e nada muda.
 *       Sem a conta e o WhatsApp, a barbearia não teria a quem devolver o Pix (R14, D33).</li>
 *   <li>Reserva futura NÃO paga ({@code AGUARDANDO_PAGAMENTO}): vira {@code CANCELADO} com o motivo da exclusão e o
 *       próprio titular como autor; o {@link ReservaCancelada} de sempre (D72) cancela a cobrança pendente e o horário
 *       volta a ficar livre.</li>
 *   <li>Passadas (concluída, falta, cancelada, expirada) ficam como estão: a reserva já não tem dado pessoal.</li>
 * </ul>
 * As linhas são travadas ({@code FOR UPDATE}): um pagamento que confirma no mesmo instante ou fecha antes (e a exclusão é
 * bloqueada) ou depois (e a reserva já estava cancelada).
 */
@Component
class CancelaReservasDaExclusao {

    static final String MOTIVO = "Conta excluída pelo titular";

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    CancelaReservasDaExclusao(JdbcClient jdbc, ApplicationEventPublisher eventos, Clock clock) {
        this.jdbc = jdbc;
        this.eventos = eventos;
        this.clock = clock;
    }

    @EventListener
    @Order(ExclusaoSolicitada.ORDEM_DA_AGENDA)
    void aoSolicitarExclusao(ExclusaoSolicitada evento) {
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        // Excluir a conta também retira a autorização de região (Fatia 11, E3): a localidade das reservas dela some.
        jdbc.sql("""
                UPDATE reserva SET regiao_bairro = NULL, regiao_cidade = NULL, regiao_uf = NULL,
                                   regiao_municipio_ibge = NULL, regiao_consentimento_versao = NULL
                WHERE cliente_id = :cliente AND (regiao_cidade IS NOT NULL OR regiao_uf IS NOT NULL)
                """).param("cliente", evento.usuarioId()).update();
        List<Futura> futuras = jdbc.sql("""
                SELECT id, status FROM reserva
                WHERE cliente_id = :cliente AND inicio > :agora AND status IN ('CONFIRMADO', 'AGUARDANDO_PAGAMENTO')
                ORDER BY id FOR UPDATE
                """)
                .param("cliente", evento.usuarioId()).param("agora", agora)
                .query((rs, n) -> new Futura(rs.getObject("id", UUID.class), rs.getString("status")))
                .list();
        if (futuras.stream().anyMatch(futura -> "CONFIRMADO".equals(futura.status()))) {
            throw new ExcecaoDeNegocio(CodigoErro.EXCLUSAO_BLOQUEADA_POR_RESERVA);
        }
        for (Futura futura : futuras) {
            jdbc.sql("""
                    UPDATE reserva SET status = 'CANCELADO', motivo_cancelamento = :motivo, cancelado_por = :cliente,
                                       cancelado_em = :agora, atualizado_em = :agora, version = version + 1
                    WHERE id = :id AND status = 'AGUARDANDO_PAGAMENTO'
                    """)
                    .param("motivo", MOTIVO).param("cliente", evento.usuarioId()).param("agora", agora)
                    .param("id", futura.id()).update();
            // Pelo outbox, na mesma transação: o pagamento cancela a cobrança pendente depois do commit (D72).
            eventos.publishEvent(new ReservaCancelada(futura.id()));
        }
    }

    private record Futura(UUID id, String status) {
    }
}
