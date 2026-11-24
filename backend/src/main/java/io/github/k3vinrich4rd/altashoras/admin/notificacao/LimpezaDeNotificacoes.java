package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.privacidade.LimpezaPorRetencao;

/**
 * A retenção da notificação (E13, LGPD, ADR 0009), pela porta {@link LimpezaPorRetencao}: o job diário da privacidade (3h)
 * chama esta limpeza. O aviso do painel vale 30 dias ({@code app.notificacao.retencao-avisos-dias}) e a inscrição que não
 * recebe um envio com sucesso há 90 dias ({@code retencao-inscricao-dias}) é apagada: o aparelho sumiu sem avisar.
 * Idempotente: rodar de novo não muda nada.
 */
@Component
class LimpezaDeNotificacoes implements LimpezaPorRetencao {

    private final Avisos avisos;
    private final Inscricoes inscricoes;
    private final ParametrosDeNotificacao parametros;
    private final TransactionTemplate transacao;

    LimpezaDeNotificacoes(Avisos avisos, Inscricoes inscricoes, ParametrosDeNotificacao parametros,
            PlatformTransactionManager gerenciador) {
        this.avisos = avisos;
        this.inscricoes = inscricoes;
        this.parametros = parametros;
        this.transacao = new TransactionTemplate(gerenciador);
    }

    @Override
    public String nome() {
        return "notificacoes";
    }

    @Override
    public int executar(Instant agora) {
        Integer total = transacao.execute(status -> avisos.apagarCriadosAntesDe(agora.minus(Duration.ofDays(parametros.retencaoAvisosDias())))
                + inscricoes.apagarSemUsoDesde(agora.minus(Duration.ofDays(parametros.retencaoInscricaoDias()))));
        return total == null ? 0 : total;
    }
}
