package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.admin.auditoria.Auditor;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.AlteracaoDeServico;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.NovoServico;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.ServicoCadastrado;

/**
 * Serviços do painel. O cadastro é da agenda; aqui só se chama a API pública e se grava a auditoria na MESMA
 * transação (E11): se a auditoria falhar, o serviço não é criado nem alterado. O detalhe leva só os NOMES dos campos
 * (nunca os valores).
 */
@Service
class CadastroDeServicos {

    private final PainelDaAgenda agenda;
    private final Auditor auditor;

    CadastroDeServicos(PainelDaAgenda agenda, Auditor auditor) {
        this.agenda = agenda;
        this.auditor = auditor;
    }

    @Transactional(readOnly = true)
    List<ServicoAdmin> listar() {
        return agenda.servicos().stream().map(ServicoAdmin::de).toList();
    }

    @Transactional
    ServicoAdmin criar(Ator ator, ServicoRequest pedido) {
        ServicoCadastrado criado = agenda.criarServico(
                new NovoServico(pedido.nome(), pedido.descricao(), pedido.duracaoMin(), pedido.precoCentavos()));
        auditor.registrar(ator, "SERVICO_CRIADO", "servico", criado.id(),
                Map.of("campos", "nome,descricao,duracaoMin,precoCentavos"));
        return ServicoAdmin.de(criado);
    }

    @Transactional
    ServicoAdmin alterar(Ator ator, UUID id, ServicoAlteracaoRequest pedido) {
        PainelDaAgenda.Alterado<ServicoCadastrado> alterado = agenda.alterarServico(id,
                new AlteracaoDeServico(pedido.nome(), pedido.descricao(), pedido.duracaoMin(), pedido.precoCentavos(),
                        pedido.ativo(), pedido.version()));
        auditor.registrar(ator, "SERVICO_ALTERADO", "servico", id, Map.of("campos", String.join(",", alterado.campos())));
        return ServicoAdmin.de(alterado.item());
    }
}
