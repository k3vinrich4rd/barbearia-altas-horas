package io.github.k3vinrich4rd.altashoras.pagamento.gateway;

import java.util.Optional;
import java.util.UUID;

/**
 * A porta do gateway (E13, ADR 0007, ADR 0011): o que a regra de pagamento precisa de um provedor, sem saber qual.
 * Trocar Asaas por Mercado Pago é escrever outro adaptador. Interface sem framework: nenhuma importação do Spring.
 * Toda chamada pode lançar {@link GatewayIndisponivelException} e nunca roda dentro de transação (E27).
 */
public interface GatewayDePagamento {

    Provedor provedor();

    /** Cria o cliente (com o CPF) e a cobrança no gateway, com {@code externalReference} = id da nossa cobrança. */
    CobrancaNoGateway criar(NovaCobranca pedido);

    /** Acha pelo nosso id (externalReference) uma cobrança criada cuja resposta se perdeu (E14). */
    Optional<CobrancaNoGateway> buscarPorReferencia(UUID cobrancaId);

    /** A fonte da verdade do pagamento: o corpo do webhook nunca manda (E20). */
    StatusNoGateway consultar(String idProvedor);
}
