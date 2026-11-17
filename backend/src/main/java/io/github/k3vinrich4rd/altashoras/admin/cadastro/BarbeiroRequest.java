package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * O corpo do POST de barbeiro. {@code usuarioId} é opcional: a conta de login precisa existir e ter o papel BARBEIRO
 * (E18). Campo fora do contrato é 400 CAMPO_DESCONHECIDO.
 */
record BarbeiroRequest(
        @NotNull(message = "obrigatório") @Size(min = 2, max = 80, message = "deve ter de 2 a 80 caracteres") String nomeExibicao,
        @Nullable UUID usuarioId) {

    BarbeiroRequest {
        nomeExibicao = nomeExibicao == null ? null : nomeExibicao.strip();
    }
}
