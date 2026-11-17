package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * O corpo do POST de folga: o período [inicio, fim) com offset e um motivo opcional. O motivo pode ser pessoal
 * (consulta médica): o toString não o imprime (R30).
 */
record FolgaRequest(
        @NotNull(message = "obrigatório") OffsetDateTime inicio,
        @NotNull(message = "obrigatório") OffsetDateTime fim,
        @Size(max = 200, message = "deve ter no máximo 200 caracteres") @Nullable String motivo) {

    FolgaRequest {
        motivo = motivo == null || motivo.isBlank() ? null : motivo.strip();
    }

    @Override
    public String toString() {
        return "FolgaRequest[período]";
    }
}
