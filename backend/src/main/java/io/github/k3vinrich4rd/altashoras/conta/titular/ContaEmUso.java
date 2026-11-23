package io.github.k3vinrich4rd.altashoras.conta.titular;

import java.util.Collection;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.conta.usuario.Usuario;
import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;
import io.github.k3vinrich4rd.altashoras.plataforma.ValidadorDeConta;

/**
 * A conta respondendo à plataforma se o token ainda vale (E16): conta desativada (a exclusão anonimiza e desativa)
 * não passa, mesmo com o token de acesso ainda dentro dos 15 minutos. Uma consulta por requisição (duas quando o token traz o
 * papel BARBEIRO, para o desligamento valer na hora).
 */
@Component
class ContaEmUso implements ValidadorDeConta {

    private final UsuarioRepository usuarios;

    ContaEmUso(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public boolean podeAcessar(UUID usuarioId, Collection<String> papeisDoToken) {
        if (usuarios.desativada(usuarioId)) {
            return false;
        }
        // O desligamento tira o papel BARBEIRO de quem também é cliente: o token antigo, que ainda o carrega, para de valer
        // na hora (H31). Só confere quando o token traz o papel; conta desconhecida não é barrada aqui.
        if (papeisDoToken.contains(Usuario.BARBEIRO) && !usuarios.ativoComPapel(usuarioId, Usuario.BARBEIRO)) {
            return !usuarios.existsById(usuarioId);
        }
        return true;
    }
}
