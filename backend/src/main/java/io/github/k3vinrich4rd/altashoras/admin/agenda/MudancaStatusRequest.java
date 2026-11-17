package io.github.k3vinrich4rd.altashoras.admin.agenda;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * O corpo do PATCH de status (E15): só CONCLUIDO, NAO_COMPARECEU ou CANCELADO, sempre com a {@code version} que a
 * tela viu. Campo fora do contrato é 400 CAMPO_DESCONHECIDO. O motivo é dado pessoal em potencial: o toString não o
 * imprime (R30).
 */
record MudancaStatusRequest(
        @NotNull(message = "obrigatório")
        @Pattern(regexp = "CONCLUIDO|NAO_COMPARECEU|CANCELADO", message = "deve ser CONCLUIDO, NAO_COMPARECEU ou CANCELADO")
        String status,
        @Size(max = 300, message = "deve ter no máximo 300 caracteres") @Nullable String motivo,
        @NotNull(message = "obrigatório") Long version) {

    @Override
    public String toString() {
        return "MudancaStatusRequest[" + status + "]";
    }
}
