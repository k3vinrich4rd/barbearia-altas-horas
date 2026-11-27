package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import java.util.List;
import java.util.UUID;

/** Resposta de GET /disponibilidade. Datas já como texto no formato do contrato (E7). */
public record DisponibilidadeResposta(String data, String fuso, UUID servicoId, int duracaoMin,
        List<HorariosDoBarbeiro> barbeiros) {

    /** {@code expediente} desde a 1.12.0: sempre enviado, por barbeiro (E16). */
    public record HorariosDoBarbeiro(UUID barbeiroId, String nomeExibicao, List<String> horarios,
            ExpedienteDoDia expediente) {
    }
}
