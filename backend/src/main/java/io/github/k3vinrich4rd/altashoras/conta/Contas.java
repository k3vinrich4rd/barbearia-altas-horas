package io.github.k3vinrich4rd.altashoras.conta;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;

/**
 * API pública da conta para os outros módulos (ADR 0002, D74): "esta conta existe e tem o papel X?". O painel
 * pergunta aqui antes de ligar uma conta a um barbeiro, sem ler a tabela {@code usuario}. Responde só sim ou não:
 * nenhum dado da conta sai (nome, e-mail e WhatsApp são dados pessoais).
 */
@Service
public class Contas {

    private final UsuarioRepository usuarios;

    Contas(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /** Conta ativa com o papel pedido (CLIENTE, BARBEIRO ou ADMIN). Conta inexistente ou desativada: {@code false}. */
    @Transactional(readOnly = true)
    public boolean temPapel(UUID usuarioId, String papel) {
        return usuarios.ativoComPapel(usuarioId, papel);
    }
}
