package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.FolgaGravada;

/**
 * A resposta do POST e do PUT de folga (1.12.0, schema {@code FolgaSalva}): a {@link FolgaAdmin} mais as reservas
 * ativas que ficaram dentro do período por {@code manterReservas} (vazio quando não houve conflito).
 */
record FolgaSalva(UUID id, String inicio, String fim, @Nullable String motivo, String tipo,
        @Nullable String atualizadaEm, List<ConflitoDeFolga> reservasMantidas) {

    static FolgaSalva de(FolgaGravada gravada, ZoneId fuso) {
        FolgaAdmin folga = FolgaAdmin.de(gravada.folga(), fuso);
        return new FolgaSalva(folga.id(), folga.inicio(), folga.fim(), folga.motivo(), folga.tipo(),
                folga.atualizadaEm(), ConflitoDeFolga.de(gravada.reservasMantidas(), fuso));
    }

    /** Motivo e nomes de clientes: o toString não imprime nenhum dos dois (R30). */
    @Override
    public String toString() {
        return "FolgaSalva[" + id + ", reservasMantidas=" + reservasMantidas.size() + "]";
    }
}
