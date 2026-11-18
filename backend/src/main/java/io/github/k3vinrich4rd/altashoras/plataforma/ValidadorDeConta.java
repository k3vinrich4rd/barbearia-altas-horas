package io.github.k3vinrich4rd.altashoras.plataforma;

import java.util.UUID;

/**
 * Porta da conta para a validação do token de acesso (Fatia 6B, E16): um token válido na assinatura e na data só vale
 * se a conta continua em uso. A exclusão anonimiza e desativa a conta; sem esta conferência o token já emitido
 * continuaria aceito por até 15 minutos. Quem implementa (o módulo conta) responde só sim ou não.
 */
public interface ValidadorDeConta {

    /** Falso quando a conta existe e foi desativada (exclusão ou desligamento). Conta desconhecida não é barrada aqui. */
    boolean podeAcessar(UUID usuarioId);
}
