package io.github.k3vinrich4rd.altashoras.conta;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;

/**
 * API pública da conta para os outros módulos (ADR 0002, D74): "esta conta existe e tem o papel X?". O painel
 * pergunta aqui antes de ligar uma conta a um barbeiro, sem ler a tabela {@code usuario}. Responde sim ou não e, para a
 * agenda copiar na reserva (6C), bairro, cidade e uf: nome, e-mail, WhatsApp e CEP nunca saem.
 */
@Service
public class Contas {

    private final UsuarioRepository usuarios;

    Contas(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /**
     * Onde mora o cliente, só bairro, cidade e uf (Fatia 6C, E30): a reserva copia isto no momento em que nasce. O CEP, o
     * nome e o contato não saem. O bairro pode faltar (CEP "geral"); sem cidade ou uf a região não se conhece.
     */
    public record Regiao(@Nullable String bairro, String cidade, String uf) {

        @Override
        public String toString() {
            return "Regiao[omitida]";
        }
    }

    /** A região da conta, ou vazio quando o endereço não foi resolvido (R31) ou a conta foi excluída. */
    @Transactional(readOnly = true)
    public Optional<Regiao> regiaoDoCliente(UUID usuarioId) {
        return usuarios.findById(usuarioId)
                .filter(u -> u.getCidade() != null && u.getUf() != null)
                .map(u -> new Regiao(u.getBairro(), u.getCidade(), u.getUf()));
    }

    /** Conta ativa com o papel pedido (CLIENTE, BARBEIRO ou ADMIN). Conta inexistente ou desativada: {@code false}. */
    @Transactional(readOnly = true)
    public boolean temPapel(UUID usuarioId, String papel) {
        return usuarios.ativoComPapel(usuarioId, papel);
    }
}
