package io.github.k3vinrich4rd.altashoras.pagamento.gateway;

/** Como o cliente paga: Pix ou cartão de crédito (D31). O cartão é pago no checkout do gateway, nunca na API (D32). */
public enum MetodoPagamento {
    PIX,
    CARTAO_CREDITO
}
