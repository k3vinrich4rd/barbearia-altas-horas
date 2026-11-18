package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.util.List;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.agenda.Reservas;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.privacidade.ExclusaoSolicitada;

/**
 * O pagamento ouvindo a privacidade (ADR 0002, D-1 estendida): quem tem uma cobrança esperando devolução
 * ({@code DEVOLUCAO_PENDENTE}) ou um pagamento tardio ainda sem devolução ({@code PAGA_APOS_EXPIRACAO}) não pode excluir
 * a conta. É o mesmo motivo da reserva futura paga: sem a conta e o WhatsApp, a barbearia não teria a quem devolver o
 * valor. Resposta: o mesmo {@code 409 EXCLUSAO_BLOQUEADA_POR_RESERVA}, e nada muda. Listener SÍNCRONO, o primeiro da fila.
 * Os ids das reservas vêm da API pública da agenda ({@link Reservas}); aqui só se olha a tabela da própria cobrança.
 */
@Component
class BloqueiaExclusaoComDevolucaoPendente {

    private final Reservas reservas;
    private final JdbcClient jdbc;

    BloqueiaExclusaoComDevolucaoPendente(Reservas reservas, JdbcClient jdbc) {
        this.reservas = reservas;
        this.jdbc = jdbc;
    }

    @EventListener
    @Order(ExclusaoSolicitada.ORDEM_DO_PAGAMENTO)
    void aoSolicitarExclusao(ExclusaoSolicitada evento) {
        List<UUID> minhas = reservas.idsDasReservasDoCliente(evento.usuarioId());
        if (minhas.isEmpty()) {
            return;
        }
        long esperandoDevolucao = jdbc.sql("""
                SELECT count(*) FROM cobranca
                WHERE reserva_id IN (:reservas) AND status IN ('DEVOLUCAO_PENDENTE', 'PAGA_APOS_EXPIRACAO')
                """)
                .param("reservas", minhas).query(Long.class).single();
        if (esperandoDevolucao > 0) {
            throw new ExcecaoDeNegocio(CodigoErro.EXCLUSAO_BLOQUEADA_POR_RESERVA,
                    "Há um valor esperando devolução. Combine com a barbearia e peça a exclusão de novo.");
        }
    }
}
