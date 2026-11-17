package io.github.k3vinrich4rd.altashoras.pagamento.gateway;

import java.time.Instant;
import java.util.UUID;

import io.github.k3vinrich4rd.altashoras.comum.valor.Cpf;

/**
 * O pedido de cobrança que a API faz ao gateway. O CPF e o nome do pagador só existem aqui, a caminho do gateway
 * (ADR 0009); o toString() não imprime nenhum dos dois (R30).
 */
public record NovaCobranca(UUID cobrancaId, MetodoPagamento metodo, long valorCentavos, Cpf cpf, String nomePagador,
        Instant expiraEm) {

    @Override
    public String toString() {
        return "NovaCobranca[" + cobrancaId + ", " + metodo + ", " + valorCentavos + " centavos]";
    }
}
