package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.github.k3vinrich4rd.altashoras.comum.validacao.TelefoneBr;

import org.jspecify.annotations.Nullable;

/**
 * O corpo do PUT de barbeiro: o {@link BarbeiroRequest} mais {@code ativo} e a {@code version} que a tela viu. O PUT
 * substitui os campos: sem {@code usuarioId}, o barbeiro fica sem conta ligada, e sem {@code whatsapp} (contato comercial, 6D), sem número.
 */
record BarbeiroAlteracaoRequest(
        @NotNull(message = "obrigatório") @Size(min = 2, max = 80, message = "deve ter de 2 a 80 caracteres") String nomeExibicao,
        @Nullable UUID usuarioId,
        @TelefoneBr @Nullable String whatsapp,
        @NotNull(message = "obrigatório") Boolean ativo,
        @NotNull(message = "obrigatório") Long version) {

    BarbeiroAlteracaoRequest {
        nomeExibicao = nomeExibicao == null ? null : nomeExibicao.strip();
        whatsapp = whatsapp == null || whatsapp.isBlank() ? null : whatsapp.strip();
    }
}
