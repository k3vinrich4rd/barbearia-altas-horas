package io.github.k3vinrich4rd.altashoras.conta.titular;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;
import io.github.k3vinrich4rd.altashoras.privacidade.RegiaoRevogada;

/**
 * A conta ouvindo a privacidade (Fatia 11, E3): a pessoa retirou a autorização de região, então bairro, cidade, uf e município
 * somem do perfil. Listener SÍNCRONO, na mesma transação da revogação; se algo falhar, a revogação inteira desfaz. Idempotente.
 */
@Component
class ApagaRegiaoDaContaRevogada {

    private final UsuarioRepository usuarios;

    ApagaRegiaoDaContaRevogada(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @EventListener
    void aoRevogarRegiao(RegiaoRevogada evento) {
        usuarios.findById(evento.usuarioId()).ifPresent(usuario -> {
            usuario.apagarRegiao();
            usuarios.saveAndFlush(usuario);
        });
    }
}
