package io.github.k3vinrich4rd.altashoras.conta.titular;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.conta.usuario.Usuario;
import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;
import io.github.k3vinrich4rd.altashoras.privacidade.FonteDeDadosDoTitular;

/**
 * A conta entregando os dados DELA para a exportação (E14): nome, e-mail, WhatsApp e endereço. Nunca sai o hash da senha,
 * a versão da credencial, o refresh token nem o CPF (que não existe no banco).
 */
@Component
class DadosDaContaDoTitular implements FonteDeDadosDoTitular {

    private final UsuarioRepository usuarios;

    DadosDaContaDoTitular(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public String chave() {
        return "conta";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportar(UUID usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
        Map<String, Object> endereco = null;
        if (usuario.getCep() != null) {
            endereco = new LinkedHashMap<>();
            endereco.put("cep", usuario.getCep());
            endereco.put("bairro", usuario.getBairro());
            endereco.put("cidade", usuario.getCidade());
            endereco.put("uf", usuario.getUf());
        }
        Map<String, Object> conta = new LinkedHashMap<>();
        conta.put("nome", usuario.getNome());
        conta.put("email", usuario.getEmail());
        conta.put("whatsapp", usuario.getWhatsapp());
        conta.put("endereco", endereco);
        return Map.of("conta", conta);
    }
}
