package io.github.k3vinrich4rd.altashoras.conta.titular;

import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;
import io.github.k3vinrich4rd.altashoras.plataforma.ValidadorDeConta;

/**
 * A conta respondendo à plataforma se o token ainda vale (E16): conta desativada (a exclusão anonimiza e desativa)
 * não passa, mesmo com o token de acesso ainda dentro dos 15 minutos. Uma consulta por chave primária por requisição.
 */
@Component
class ContaEmUso implements ValidadorDeConta {

    private final UsuarioRepository usuarios;

    ContaEmUso(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public boolean podeAcessar(UUID usuarioId) {
        return !usuarios.desativada(usuarioId);
    }
}
