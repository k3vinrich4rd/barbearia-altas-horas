package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.time.ZoneId;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.admin.FormatoDeData;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.FolgaCadastrada;

/**
 * A folga como o contrato a escreve: início e fim no fuso da barbearia, com offset; desde a 1.12.0, o {@code tipo}
 * (sempre presente) e o {@code atualizadaEm} (nulo até a primeira edição).
 */
record FolgaAdmin(UUID id, String inicio, String fim, @Nullable String motivo, String tipo,
        @Nullable String atualizadaEm) {

    static FolgaAdmin de(FolgaCadastrada folga, ZoneId fuso) {
        return new FolgaAdmin(folga.id(), FormatoDeData.formatar(folga.inicio(), fuso),
                FormatoDeData.formatar(folga.fim(), fuso), folga.motivo(), folga.tipo(),
                FormatoDeData.formatar(folga.atualizadaEm(), fuso));
    }

    /** O motivo pode ser pessoal: o toString só mostra o id (R30). */
    @Override
    public String toString() {
        return "FolgaAdmin[" + id + "]";
    }
}
