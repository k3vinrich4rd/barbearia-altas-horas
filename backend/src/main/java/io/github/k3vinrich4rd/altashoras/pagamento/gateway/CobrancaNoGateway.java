package io.github.k3vinrich4rd.altashoras.pagamento.gateway;

import org.jspecify.annotations.Nullable;

/**
 * O que o gateway devolve ao criar uma cobrança. {@code pixQrCodeBase64} pode ser nulo (no modo simulado o front
 * gera o QR a partir do copia e cola, D68); {@code urlCheckout} só existe no cartão.
 */
public record CobrancaNoGateway(String idProvedor, String idClienteProvedor, @Nullable String pixCopiaECola,
        @Nullable String pixQrCodeBase64, @Nullable String urlCheckout) {
}
