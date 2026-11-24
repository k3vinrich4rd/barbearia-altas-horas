package io.github.k3vinrich4rd.altashoras.conta.titular;

import java.util.Collection;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.conta.usuario.Usuario;
import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;
import io.github.k3vinrich4rd.altashoras.plataforma.ValidadorDeConta;

/**
 * A conta respondendo à plataforma se o token ainda vale (E16): conta desativada (a exclusão anonimiza e desativa)
 * não passa, mesmo com o token de acesso ainda dentro dos 15 minutos. Uma consulta por requisição.
 */
@Component
class ContaEmUso implements ValidadorDeConta {

    private final UsuarioRepository usuarios;

    ContaEmUso(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public boolean podeAcessar(UUID usuarioId, Collection<String> papeisDoToken) {
        // Desativada, ou com o papel BARBEIRO no token e não na conta (o desligamento de quem também é cliente derruba o token
        // antigo na hora, H31). Conta desconhecida não é barrada aqui.
        return !usuarios.barrada(usuarioId, papeisDoToken.contains(Usuario.BARBEIRO));
    }
}
