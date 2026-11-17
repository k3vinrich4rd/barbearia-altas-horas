package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.admin.auditoria.Auditor;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.ConfiguracaoCadastrada;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.NovaConfiguracao;

/**
 * Lê e altera as regras da agenda sem deploy (D15). A mudança e a auditoria ficam na MESMA transação (E11); o
 * detalhe leva só os nomes dos campos que mudaram.
 */
@Service
class ConfigurarAgenda {

    private final PainelDaAgenda agenda;
    private final Auditor auditor;

    ConfigurarAgenda(PainelDaAgenda agenda, Auditor auditor) {
        this.agenda = agenda;
        this.auditor = auditor;
    }

    @Transactional(readOnly = true)
    ConfiguracaoAdmin ler() {
        return ConfiguracaoAdmin.de(agenda.configuracao());
    }

    @Transactional
    ConfiguracaoAdmin alterar(Ator ator, ConfiguracaoRequest pedido) {
        PainelDaAgenda.Alterado<ConfiguracaoCadastrada> alterado = agenda.alterarConfiguracao(ator.id(),
                new NovaConfiguracao(pedido.antecedenciaMinimaMin(), pedido.intervaloEntreAtendimentosMin(),
                        pedido.granularidadeMin(), pedido.horizonteDias(), pedido.expiracaoPagamentoMin(),
                        pedido.version()));
        auditor.registrar(ator, "CONFIGURACAO_ALTERADA", "configuracao", null,
                Map.of("campos", String.join(",", alterado.campos())));
        return ConfiguracaoAdmin.de(alterado.item());
    }
}
