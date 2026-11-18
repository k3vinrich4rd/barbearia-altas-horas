package io.github.k3vinrich4rd.altashoras.admin.agenda;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * O corpo de {@code POST /admin/agendamentos} (Fatia 6D): só o que a barbearia sabe do horário combinado por WhatsApp.
 * Não há campo de WhatsApp, e-mail nem CPF do cliente: o sistema não guarda (R30, D125). Campo fora do contrato é 400
 * CAMPO_DESCONHECIDO. Nome e observação vêm aparados e, se ficarem vazios, viram nulos. São dado pessoal em potencial:
 * o toString não os imprime (R30).
 */
record ReservaManualRequest(
        @NotNull(message = "obrigatório") UUID servicoId,
        @NotNull(message = "obrigatório") UUID barbeiroId,
        @NotNull(message = "obrigatório") OffsetDateTime inicio,
        @Size(max = 80, message = "deve ter no máximo 80 caracteres") @Nullable String nomeCliente,
        @Size(max = 200, message = "deve ter no máximo 200 caracteres") @Nullable String observacao) {

    ReservaManualRequest {
        nomeCliente = vazioViraNulo(nomeCliente);
        observacao = vazioViraNulo(observacao);
    }

    private static @Nullable String vazioViraNulo(@Nullable String texto) {
        if (texto == null) {
            return null;
        }
        String aparado = texto.strip();
        return aparado.isEmpty() ? null : aparado;
    }

    @Override
    public String toString() {
        return "ReservaManualRequest[dados pessoais omitidos]";
    }
}
