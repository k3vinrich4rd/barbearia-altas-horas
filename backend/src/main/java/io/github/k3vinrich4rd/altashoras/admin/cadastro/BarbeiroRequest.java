package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.github.k3vinrich4rd.altashoras.comum.validacao.TelefoneBr;

import org.jspecify.annotations.Nullable;

/**
 * O corpo do POST de barbeiro. {@code usuarioId} é opcional: a conta de login precisa existir e ter o papel BARBEIRO
 * (E18). {@code whatsapp} é o contato comercial do barbeiro (6D, D122): opcional, com ou sem máscara, de 10 a 13 dígitos; o
 * caso de uso grava só os dígitos. Campo fora do contrato é 400 CAMPO_DESCONHECIDO.
 */
record BarbeiroRequest(
        @NotNull(message = "obrigatório") @Size(min = 2, max = 80, message = "deve ter de 2 a 80 caracteres") String nomeExibicao,
        @Nullable UUID usuarioId,
        @TelefoneBr @Nullable String whatsapp) {

    BarbeiroRequest {
        nomeExibicao = nomeExibicao == null ? null : nomeExibicao.strip();
        whatsapp = whatsapp == null || whatsapp.isBlank() ? null : whatsapp.strip();
    }
}
