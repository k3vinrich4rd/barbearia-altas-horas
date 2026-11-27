package io.github.k3vinrich4rd.altashoras.admin.agenda;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
    private final Clock clock;
    private final Duration prorrogacao;
    private final int maximoDeProrrogacoes;

    ConsultarAgendaDoPainel(PainelDaAgenda agenda, PendenciasDePagamento pagamentos,
            @Value("${app.fuso:America/Sao_Paulo}") String fuso, Clock clock,
            @Value("${app.agenda.prorrogacao-pagamento:PT2M}") Duration prorrogacao,
            @Value("${app.agenda.prorrogacao-maximo:10}") int maximoDeProrrogacoes) {
        this.agenda = agenda;
        this.pagamentos = pagamentos;
        this.fuso = ZoneId.of(fuso);
        this.clock = clock;
        this.prorrogacao = prorrogacao;
        this.maximoDeProrrogacoes = maximoDeProrrogacoes;
    }

    List<AgendamentoAdmin> consultar(Ator ator, LocalDate data, PainelDaAgenda.Visao visao, @Nullable UUID barbeiroId) {
        List<PainelDaAgenda.Item> itens = agenda.agenda(new PainelDaAgenda.Escopo(ator.id(), ator.admin()), data, visao,
                barbeiroId);
        Map<UUID, String> cobrancas = pagamentos.statusPorReserva(itens.stream().map(PainelDaAgenda.Item::id).toList());
        return itens.stream().map(item -> AgendamentoAdmin.de(item, cobrancas.get(item.id()), fuso, clock.instant(), prorrogacao,
                maximoDeProrrogacoes)).toList();
    }

    /** O calendário do mês (14C). */
    AgendaDoMes mes(Ator ator, YearMonth mes, @Nullable UUID barbeiroId) {
        return AgendaDoMes.de(agenda.resumoDoMes(new PainelDaAgenda.Escopo(ator.id(), ator.admin()), mes, barbeiroId));
    }

    /** A grade do dia (14C): as reservas de todas as colunas ganham o status da cobrança numa consulta só (E16). */
    GradeDoDia dia(Ator ator, LocalDate data, @Nullable UUID barbeiroId) {
        PainelDaAgenda.GradeDoDia grade = agenda.gradeDoDia(new PainelDaAgenda.Escopo(ator.id(), ator.admin()), data,
                barbeiroId);
        Map<UUID, String> cobrancas = pagamentos.statusPorReserva(grade.colunas().stream()
                .flatMap(c -> c.agendamentos().stream()).map(PainelDaAgenda.Item::id).toList());
        DateTimeFormatter hora = DateTimeFormatter.ofPattern("HH:mm");
        return new GradeDoDia(grade.data().toString(), grade.granularidadeMin(), grade.colunas().stream()
                .map(c -> new GradeDoDia.Coluna(c.barbeiroId(), c.nomeExibicao(),
                        c.horarios().stream().map(h -> new GradeDoDia.Horario(h.inicio().format(hora), h.situacao(),
                                h.agendamentoId())).toList(),
                        c.agendamentos().stream().map(item -> AgendamentoAdmin.de(item, cobrancas.get(item.id()), fuso,
                                clock.instant(), prorrogacao, maximoDeProrrogacoes)).toList()))
                .toList());
    }
}
