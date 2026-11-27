package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Uma linha da projeção de leitura (ReservaRepository.PROJECAO). Tipos-objeto para o construtor do JPQL casar. */
record LinhaAgendamento(UUID id, StatusReserva status, Reserva.Origem origem, UUID servicoId, String servicoNome,
        Integer duracaoMin, UUID barbeiroId, String barbeiroNome, Instant inicio, Instant fim, Long precoCentavos,
        @Nullable Instant expiraEm, @Nullable String motivoCancelamento, Long version,
        Integer prorrogacoes) {
}
