package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

/** Os status da tabela cobranca (V1). A 4B usa os quatro primeiros; devolução é da Fatia 5. */
public enum StatusCobranca {
    PENDENTE,
    PAGA,
    PAGA_APOS_EXPIRACAO,
    CANCELADA,
    FALHOU,
    DEVOLUCAO_PENDENTE,
    DEVOLVIDA
}
