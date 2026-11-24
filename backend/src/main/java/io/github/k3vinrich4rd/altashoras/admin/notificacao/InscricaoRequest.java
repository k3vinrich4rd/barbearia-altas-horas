package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * O corpo do POST de inscrição (contrato 1.10.0): o {@code endpoint} do serviço de push e as chaves do aparelho, como o
 * navegador as entrega. Nada daqui vai para log e o toString não imprime nenhum valor (R30). A conta é a do token: o corpo
 * não escolhe de quem é o aparelho.
 */
record InscricaoRequest(
        @NotNull(message = "obrigatório") @NotBlank(message = "obrigatório")
        @Size(max = 600, message = "deve ter no máximo 600 caracteres") String endpoint,
        @NotNull(message = "obrigatório") @Valid Chaves chaves) {

    record Chaves(
            @NotNull(message = "obrigatório") @NotBlank(message = "obrigatório")
            @Size(max = 200, message = "deve ter no máximo 200 caracteres") String p256dh,
            @NotNull(message = "obrigatório") @NotBlank(message = "obrigatório")
            @Size(max = 100, message = "deve ter no máximo 100 caracteres") String auth) {

        @Override
        public String toString() {
            return "Chaves[omitidas]";
        }
    }

    @Override
    public String toString() {
        return "InscricaoRequest[omitida]";
    }
}
