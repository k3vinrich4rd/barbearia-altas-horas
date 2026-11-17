package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * O corpo do PUT de serviço: o {@link ServicoRequest} mais {@code ativo} e a {@code version} que a tela viu. O PUT
 * substitui os campos (a descrição omitida limpa a descrição). Quem tira o serviço de cena usa {@code ativo = false}:
 * não existe DELETE (E16).
 */
record ServicoAlteracaoRequest(
        @NotNull(message = "obrigatório") @Size(min = 2, max = 80, message = "deve ter de 2 a 80 caracteres") String nome,
        @Size(max = 300, message = "deve ter no máximo 300 caracteres") @Nullable String descricao,
        @NotNull(message = "obrigatório") @Min(value = 5, message = "deve ser de pelo menos 5 minutos")
        @Max(value = 480, message = "deve ser de no máximo 480 minutos") Integer duracaoMin,
        @NotNull(message = "obrigatório") @Min(value = 1, message = "deve ser de pelo menos 1 centavo") Long precoCentavos,
        @NotNull(message = "obrigatório") Boolean ativo,
        @NotNull(message = "obrigatório") Long version) {

    ServicoAlteracaoRequest {
        nome = nome == null ? null : nome.strip();
        descricao = ServicoRequest.limpar(descricao);
    }
}
