package io.github.k3vinrich4rd.altashoras.admin.agenda;

import java.time.ZoneId;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.admin.auditoria.Auditor;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;

/**
 * Travar um horário combinado por WhatsApp (Fatia 6D). Esta classe NÃO é {@code @Transactional}: a agenda abre a
 * transação da reserva (para refazer o deadlock) e chama a auditoria lá dentro, então a reserva e a auditoria ficam na
 * mesma transação (E11): se a auditoria falhar, a reserva desfaz. O detalhe da auditoria leva só o que é técnico: nunca
 * o nome nem a observação (R30).
 */
@Service
class CriarReservaManual {

    private final PainelDaAgenda agenda;
    private final Auditor auditor;
    private final ZoneId fuso;

    CriarReservaManual(PainelDaAgenda agenda, Auditor auditor, @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.agenda = agenda;
        this.auditor = auditor;
        this.fuso = ZoneId.of(fuso);
    }

    AgendamentoAdmin criar(Ator ator, ReservaManualRequest pedido) {
        PainelDaAgenda.Item item = agenda.criarReservaManual(new PainelDaAgenda.Escopo(ator.id(), ator.admin()),
                new PainelDaAgenda.NovaReservaManual(pedido.servicoId(), pedido.barbeiroId(), pedido.inicio(),
                        pedido.nomeCliente(), pedido.observacao()),
                id -> auditor.registrar(ator, "AGENDAMENTO_MANUAL_CRIADO", "agendamento", id,
                        Map.of("origem", "MANUAL", "para", "CONFIRMADO", "barbeiroId", pedido.barbeiroId().toString())));
        return AgendamentoAdmin.de(item, null, fuso);
    }
}
