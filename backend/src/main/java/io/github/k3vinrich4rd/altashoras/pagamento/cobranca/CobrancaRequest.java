package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.pagamento.gateway.MetodoPagamento;

/**
 * O corpo de POST /pagamentos/cobrancas. O CPF é pedido só aqui (R9). {@code aceitePolitica} e
 * {@code versaoPolitica} não têm {@code @NotNull} de propósito: ausentes são 422 ACEITE_POLITICA_OBRIGATORIO (R18),
 * não 400. O toString() não imprime o CPF (R30).
 */
record CobrancaRequest(
        @NotNull(message = "obrigatório") UUID reservaId,
        @NotNull(message = "obrigatório") MetodoPagamento metodo,
        @NotBlank(message = "obrigatório") @Size(max = 20, message = "deve ter no máximo 20 caracteres") String cpf,
        @Nullable Boolean aceitePolitica,
        @Nullable Integer versaoPolitica) {

    @Override
    public String toString() {
        return "CobrancaRequest[" + reservaId + ", " + metodo + ", dados pessoais omitidos]";
    }
}
