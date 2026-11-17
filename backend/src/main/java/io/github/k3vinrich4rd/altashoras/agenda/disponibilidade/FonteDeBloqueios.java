package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Bloqueio;

/**
 * Porta de saída (arquitetura hexagonal, ADR 0011): o caso de uso pergunta "o que bloqueia a agenda neste
 * período?" sem saber de onde vem a resposta. Hoje o adaptador é SQL (BloqueiosJdbc); nos testes do caso de uso
 * pode ser uma lista em memória.
 */
public interface FonteDeBloqueios {

    /**
     * Folgas e reservas que ocupam horário e tocam [inicio, fim), por barbeiro. Ocupa: CONFIRMADO, ou
     * AGUARDANDO_PAGAMENTO com o prazo ainda aberto em {@code agora} (R15).
     */
    Map<UUID, List<Bloqueio>> noPeriodo(Instant inicio, Instant fim, Instant agora);
}
