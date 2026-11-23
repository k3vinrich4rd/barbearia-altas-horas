package io.github.k3vinrich4rd.altashoras.conta.titular;

import java.time.Clock;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.conta.usuario.RefreshTokenRepository;
import io.github.k3vinrich4rd.altashoras.conta.usuario.Usuario;
import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;
import io.github.k3vinrich4rd.altashoras.privacidade.ExclusaoSolicitada;

/**
 * A conta ouvindo a privacidade (ADR 0002, E10, E16). Listener SÍNCRONO: roda dentro da transação da exclusão, depois
 * de a agenda ter liberado ({@code @Order} maior) e antes de a privacidade gravar a revogação final. Anonimiza o
 * usuário (a linha fica, sem dado pessoal), sobe a versão da credencial e apaga TODOS os refresh tokens da conta. Se
 * algo falhar, a transação inteira desfaz.
 */
@Component
class AnonimizaContaDaExclusao {

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    AnonimizaContaDaExclusao(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens, Clock clock) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @EventListener
    @Order(ExclusaoSolicitada.ORDEM_DA_CONTA)
    void aoSolicitarExclusao(ExclusaoSolicitada evento) {
        Usuario usuario = usuarios.findById(evento.usuarioId())
                .orElseThrow(() -> new ExcecaoDeNegocio(CodigoErro.NAO_AUTENTICADO));
        // Barbeiro ativo que também é cliente (H32): o dono desliga o acesso de barbeiro antes; nada é apagado (A29).
        if (usuario.isAtivo() && usuario.getPapeis().contains(Usuario.BARBEIRO)
                && !usuario.getPapeis().contains(Usuario.ADMIN)) {
            throw new ExcecaoDeNegocio(CodigoErro.USUARIO_JA_VINCULADO,
                    "Esta conta tem acesso de barbeiro. Peça ao dono para desligar o acesso antes de excluir a conta.");
        }
        // Segunda barreira (a primeira é o SegurancaConfig): conta de equipe não se exclui pelo app.
        if (!usuario.isAtivo() || usuario.getPapeis().stream().anyMatch(papel -> !Usuario.CLIENTE.equals(papel))) {
            throw new ExcecaoDeNegocio(CodigoErro.ACESSO_NEGADO);
        }
        refreshTokens.apagarDoUsuario(usuario.getId());
        usuario.anonimizar(clock.instant());
        usuarios.saveAndFlush(usuario);
    }
}
