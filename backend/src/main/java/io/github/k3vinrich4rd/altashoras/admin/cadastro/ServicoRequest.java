package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * O corpo do POST de serviço (E16). Só os campos do contrato: {@code id}, {@code ativo} e {@code version} não vêm do
 * cliente (400 CAMPO_DESCONHECIDO). Preço de 1 centavo para cima (a reserva exige {@code preco_centavos > 0}) e
 * duração de 5 a 480 minutos, como a V1. O nome e a descrição chegam sem espaços nas pontas.
 */
record ServicoRequest(
        @NotNull(message = "obrigatório") @Size(min = 2, max = 80, message = "deve ter de 2 a 80 caracteres") String nome,
        @Size(max = 300, message = "deve ter no máximo 300 caracteres") @Nullable String descricao,
        @NotNull(message = "obrigatório") @Min(value = 5, message = "deve ser de pelo menos 5 minutos")
        @Max(value = 480, message = "deve ser de no máximo 480 minutos") Integer duracaoMin,
        @NotNull(message = "obrigatório") @Min(value = 1, message = "deve ser de pelo menos 1 centavo") Long precoCentavos) {

    ServicoRequest {
        nome = nome == null ? null : nome.strip();
        descricao = limpar(descricao);
    }

    static @Nullable String limpar(@Nullable String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.strip();
    }
}
