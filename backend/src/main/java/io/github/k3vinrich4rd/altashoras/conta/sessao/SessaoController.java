package io.github.k3vinrich4rd.altashoras.conta.sessao;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.conta.usuario.PerfilResposta;
import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;

@RestController
class SessaoController {

    private final Sessoes sessoes;
    private final UsuarioRepository usuarios;

    SessaoController(Sessoes sessoes, UsuarioRepository usuarios) {
        this.sessoes = sessoes;
        this.usuarios = usuarios;
    }

    @PostMapping("/auth/login")
    SessaoResposta entrar(@Valid @RequestBody Pedidos.Login pedido, HttpServletRequest request) {
        return sessoes.entrar(pedido.email(), pedido.senha(), request.getRemoteAddr());
    }

    @PostMapping("/auth/refresh")
    SessaoResposta renovar(@Valid @RequestBody Pedidos.Refresh pedido) {
        return sessoes.renovar(pedido.refreshToken());
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> sair(@Valid @RequestBody Pedidos.Refresh pedido) {
        sessoes.encerrar(pedido.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** O id vem do token (claim sub), nunca de um parâmetro: ninguém pede o perfil de outra pessoa. */
    @GetMapping("/me")
    PerfilResposta meuPerfil(@AuthenticationPrincipal Jwt token) {
        return usuarios.findById(UUID.fromString(token.getSubject()))
                .map(PerfilResposta::de)
                .orElseThrow(() -> new ExcecaoDeNegocio(CodigoErro.NAO_AUTENTICADO));
    }

    @PutMapping("/me/senha")
    ResponseEntity<Void> trocarSenha(@AuthenticationPrincipal Jwt token, @Valid @RequestBody Pedidos.TrocaDeSenha pedido) {
        sessoes.trocarSenha(UUID.fromString(token.getSubject()), pedido.senhaAtual(), pedido.novaSenha());
        return ResponseEntity.noContent().build();
    }
}
