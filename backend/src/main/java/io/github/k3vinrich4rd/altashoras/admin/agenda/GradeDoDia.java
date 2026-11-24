package io.github.k3vinrich4rd.altashoras.admin.agenda;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** A {@code GradeDoDia} do contrato (1.11.0, 14C): uma coluna por barbeiro visível, na ordem do nome. */
record GradeDoDia(String data, int granularidadeMin, List<Coluna> colunas) {

    record Coluna(UUID barbeiroId, String nomeExibicao, List<Horario> horarios, List<AgendamentoAdmin> agendamentos) {
    }

    /** {@code inicio} em "HH:mm" no fuso da barbearia; {@code agendamentoId} só em OCUPADO. */
    record Horario(String inicio, String situacao, @Nullable UUID agendamentoId) {
    }
}
