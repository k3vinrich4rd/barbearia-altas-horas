package io.github.k3vinrich4rd.altashoras.admin.agenda;

import java.util.List;

import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;

/** O {@code ResumoDoMes} do contrato (1.11.0, 14C): um item por dia do mês, em ordem. */
record AgendaDoMes(String mes, List<Dia> dias) {

    record Dia(String data, int reservas, boolean folga, boolean expediente) {
    }

    static AgendaDoMes de(PainelDaAgenda.ResumoDoMes resumo) {
        return new AgendaDoMes(resumo.mes().toString(), resumo.dias().stream()
                .map(d -> new Dia(d.data().toString(), d.reservas(), d.folga(), d.expediente())).toList());
    }
}
