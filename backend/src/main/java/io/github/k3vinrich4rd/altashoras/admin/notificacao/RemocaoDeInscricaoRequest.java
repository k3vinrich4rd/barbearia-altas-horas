package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** O corpo do DELETE de inscrição: o endpoint vai no corpo e não na URL, para não cair em log de acesso (R30). */
record RemocaoDeInscricaoRequest(
        @NotNull(message = "obrigatório") @NotBlank(message = "obrigatório")
        @Size(max = 600, message = "deve ter no máximo 600 caracteres") String endpoint) {

    @Override
    public String toString() {
        return "RemocaoDeInscricaoRequest[omitida]";
    }
}
