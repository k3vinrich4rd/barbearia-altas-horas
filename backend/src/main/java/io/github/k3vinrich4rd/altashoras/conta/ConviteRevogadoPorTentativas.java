package io.github.k3vinrich4rd.altashoras.conta;

import java.util.UUID;

/**
 * O convite chegou ao limite de tentativas erradas com o código certo e foi revogado (E8). Publicado dentro da transação
 * da tentativa; o painel escuta e grava a auditoria com o dono que gerou o convite como autor (a tabela exige um autor).
 */
public record ConviteRevogadoPorTentativas(UUID barbeiroId, UUID geradoPor, String ip) {
}
