package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.privacidade.ExclusaoSolicitada;

/**
 * O ciclo de vida das notificações é o da conta do barbeiro (E11, H33): desligar o barbeiro (Fatia 10) e excluir a conta
 * (LGPD) apagam os aparelhos e os avisos dela. O desligamento chama {@link #apagar} na transação do painel; a exclusão
 * chega pelo evento síncrono da privacidade, antes de a conta anonimizar. O {@code ON DELETE CASCADE} da V13 cobre só a
 * exclusão física da linha, que a conta nunca faz (ela anonimiza e mantém a linha).
 */
@Component
public class ApagaNotificacoesDaConta {

    private final Avisos avisos;
    private final Inscricoes inscricoes;

    ApagaNotificacoesDaConta(Avisos avisos, Inscricoes inscricoes) {
        this.avisos = avisos;
        this.inscricoes = inscricoes;
    }

    /** Apaga os aparelhos e os avisos da conta, na transação de quem chama. */
    @Transactional
    public void apagar(UUID usuarioId) {
        inscricoes.apagarDaConta(usuarioId);
        avisos.apagarDaConta(usuarioId);
    }

    @EventListener
    @Order(ExclusaoSolicitada.ORDEM_DAS_NOTIFICACOES)
    void aoSolicitarExclusao(ExclusaoSolicitada evento) {
        apagar(evento.usuarioId());
    }
}
