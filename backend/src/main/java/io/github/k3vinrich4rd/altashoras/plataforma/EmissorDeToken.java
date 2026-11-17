package io.github.k3vinrich4rd.altashoras.plataforma;

import java.util.Collection;
import java.util.UUID;

/**
 * API pública do módulo plataforma: emite o token de acesso (JWT) sem que quem pede conheça chave ou algoritmo.
 * O módulo conta decide QUEM entra; a plataforma decide COMO o token é assinado e validado.
 */
public interface EmissorDeToken {

    TokenEmitido emitir(UUID usuarioId, Collection<String> papeis, int versaoCredencial, boolean deveTrocarSenha);

    record TokenEmitido(String valor, long expiraEmSegundos) {
    }
}
