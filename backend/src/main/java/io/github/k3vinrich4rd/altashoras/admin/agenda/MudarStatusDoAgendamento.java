package io.github.k3vinrich4rd.altashoras.admin.agenda;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.admin.auditoria.Auditor;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;
import io.github.k3vinrich4rd.altashoras.pagamento.PendenciasDePagamento;

/**
 * Concluir, marcar falta ou cancelar pela barbearia. A mudança e a auditoria ficam na MESMA transação (E11): se a
 * gravação da auditoria falhar, a mudança (e o evento de cancelamento) desfaz; se a mudança falhar, nada é gravado.
 * O detalhe da auditoria leva só o status de antes e o de depois: o motivo fica na reserva (R30).
 */
@Service
class MudarStatusDoAgendamento {

    private final PainelDaAgenda agenda;
    private final PendenciasDePagamento pagamentos;
    private final Auditor auditor;
    private final ZoneId fuso;

    MudarStatusDoAgendamento(PainelDaAgenda agenda, PendenciasDePagamento pagamentos, Auditor auditor,
            @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.agenda = agenda;
        this.pagamentos = pagamentos;
        this.auditor = auditor;
        this.fuso = ZoneId.of(fuso);
    }

    @Transactional
    AgendamentoAdmin mudar(Ator ator, UUID id, MudancaStatusRequest pedido) {
        PainelDaAgenda.StatusAlvo alvo = PainelDaAgenda.StatusAlvo.valueOf(pedido.status());
        PainelDaAgenda.Mudanca mudanca = agenda.mudarStatus(new PainelDaAgenda.Escopo(ator.id(), ator.admin()), id, alvo,
                pedido.motivo(), pedido.version());
        auditor.registrar(ator, "AGENDAMENTO_" + alvo.name(), "agendamento", id,
                Map.of("de", mudanca.de(), "para", mudanca.item().status()));
        String cobranca = pagamentos.statusPorReserva(List.of(id)).get(id);
        return AgendamentoAdmin.de(mudanca.item(), cobranca, fuso);
    }
}
