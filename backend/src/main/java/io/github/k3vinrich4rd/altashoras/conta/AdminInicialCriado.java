package io.github.k3vinrich4rd.altashoras.conta;

import java.util.UUID;

/**
 * Evento do módulo conta: o primeiro admin acabou de ser criado. Quem quiser reagir, escuta (a agenda vincula o
 * usuário ao cadastro do barbeiro). A conta não conhece a agenda: a dependência aponta só de lá para cá.
 */
public record AdminInicialCriado(UUID usuarioId) {
}
