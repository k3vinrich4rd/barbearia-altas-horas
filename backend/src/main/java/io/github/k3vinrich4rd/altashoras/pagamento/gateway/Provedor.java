package io.github.k3vinrich4rd.altashoras.pagamento.gateway;

/** Quem processa o pagamento. SIMULADO é o modo do beta (D59): nenhum serviço externo e nenhum dinheiro real. */
public enum Provedor {
    ASAAS,
    SIMULADO
}
