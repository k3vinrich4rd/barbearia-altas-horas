package io.github.k3vinrich4rd.altashoras.privacidade.titular;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.privacidade.Consentimentos;
import io.github.k3vinrich4rd.altashoras.privacidade.ExclusaoSolicitada;

/**
 * A exclusão da conta (E15), numa ÚNICA transação: grava o pedido, publica {@link ExclusaoSolicitada} (os listeners
 * síncronos da agenda e da conta rodam aqui dentro, na ordem de {@code @Order}) e fecha com a revogação final do
 * consentimento. Se qualquer passo falhar, inclusive o bloqueio por reserva paga, nada fica gravado: o {@code 204}
 * só existe quando acabou de verdade. O registro do pedido guarda tipo, conta e datas, nada do conteúdo (E22).
 */
@Service
class ExcluirConta {

    private final ApplicationEventPublisher eventos;
    private final Consentimentos consentimentos;
    private final JdbcClient jdbc;
    private final Clock clock;

    ExcluirConta(ApplicationEventPublisher eventos, Consentimentos consentimentos, JdbcClient jdbc, Clock clock) {
        this.eventos = eventos;
        this.consentimentos = consentimentos;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    void excluir(UUID usuarioId) {
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        UUID pedido = jdbc.sql("""
                INSERT INTO solicitacao_titular (usuario_id, tipo, solicitada_em)
                VALUES (:usuario, 'EXCLUSAO', :agora) RETURNING id
                """)
                .param("usuario", usuarioId).param("agora", agora)
                .query(UUID.class).single();
        eventos.publishEvent(new ExclusaoSolicitada(usuarioId));
        consentimentos.registrarRevogacaoFinal(usuarioId);
        jdbc.sql("UPDATE solicitacao_titular SET concluida_em = :agora WHERE id = :id")
                .param("agora", agora).param("id", pedido).update();
    }
}
