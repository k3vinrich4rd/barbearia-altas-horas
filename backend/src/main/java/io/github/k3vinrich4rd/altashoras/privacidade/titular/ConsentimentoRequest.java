package io.github.k3vinrich4rd.altashoras.privacidade.titular;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Só os campos do contrato: a finalidade vem da rota, nunca do corpo (campo extra é 400 CAMPO_DESCONHECIDO). */
record ConsentimentoRequest(
        @NotNull(message = "obrigatório") Boolean aceito,
        @NotBlank(message = "obrigatória") @Size(max = 20, message = "deve ter no máximo 20 caracteres") String versaoTexto) {
}
