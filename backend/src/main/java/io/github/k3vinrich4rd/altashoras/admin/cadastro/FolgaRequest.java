package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * O corpo do POST e do PUT de folga: o período [inicio, fim) com offset, um motivo opcional e, desde a 1.12.0 (ADR
 * 0015), o {@code tipo} ({@code FOLGA}, o padrão, ou {@code FERIAS}) e o {@code manterReservas} (padrão {@code false},
 * nunca guardado). O motivo pode ser pessoal (consulta médica): o toString não o imprime (R30).
 */
record FolgaRequest(
        @NotNull(message = "obrigatório") OffsetDateTime inicio,
        @NotNull(message = "obrigatório") OffsetDateTime fim,
        @Size(max = 200, message = "deve ter no máximo 200 caracteres") @Nullable String motivo,
        @Pattern(regexp = "FOLGA|FERIAS", message = "deve ser FOLGA ou FERIAS") @Nullable String tipo,
        @Nullable Boolean manterReservas) {

    /** Sem o campo, a API grava FOLGA (o front da 1.11.0 não manda o tipo, A4). */
    static final String TIPO_PADRAO = "FOLGA";

    FolgaRequest {
        motivo = motivo == null || motivo.isBlank() ? null : motivo.strip();
    }

    String tipoOuPadrao() {
        return tipo == null ? TIPO_PADRAO : tipo;
    }

    boolean manter() {
        return Boolean.TRUE.equals(manterReservas);
    }

    @Override
    public String toString() {
        return "FolgaRequest[período]";
    }
}
