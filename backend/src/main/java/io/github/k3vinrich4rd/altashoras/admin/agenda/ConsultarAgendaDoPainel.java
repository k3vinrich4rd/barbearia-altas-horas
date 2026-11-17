package io.github.k3vinrich4rd.altashoras.admin.agenda;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;
import io.github.k3vinrich4rd.altashoras.pagamento.PendenciasDePagamento;

/**
 * A agenda do painel (E6): a agenda traz as reservas em uma consulta; o pagamento traz o status das cobranças delas
 * em outra, em lote; o admin junta em memória. O número de consultas não cresce com o número de reservas.
 */
@Service
class ConsultarAgendaDoPainel {

    private final PainelDaAgenda agenda;
    private final PendenciasDePagamento pagamentos;
    private final ZoneId fuso;

    ConsultarAgendaDoPainel(PainelDaAgenda agenda, PendenciasDePagamento pagamentos,
            @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.agenda = agenda;
        this.pagamentos = pagamentos;
        this.fuso = ZoneId.of(fuso);
    }

    List<AgendamentoAdmin> consultar(Ator ator, LocalDate data, PainelDaAgenda.Visao visao, @Nullable UUID barbeiroId) {
        List<PainelDaAgenda.Item> itens = agenda.agenda(new PainelDaAgenda.Escopo(ator.id(), ator.admin()), data, visao,
                barbeiroId);
        Map<UUID, String> cobrancas = pagamentos.statusPorReserva(itens.stream().map(PainelDaAgenda.Item::id).toList());
        return itens.stream().map(item -> AgendamentoAdmin.de(item, cobrancas.get(item.id()), fuso)).toList();
    }
}
