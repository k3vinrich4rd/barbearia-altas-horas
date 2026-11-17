package io.github.k3vinrich4rd.altashoras.conta.usuario;

import java.util.List;
import java.util.UUID;

/** O que a API mostra de uma conta. Não existe campo de senha aqui: o que não está no record não vaza. */
public record PerfilResposta(UUID id, String nome, String email, List<String> papeis, Endereco endereco,
        boolean deveTrocarSenha) {

    /** {@code completo} fica falso enquanto bairro e cidade não forem resolvidos pelo CEP (Fatia 6, R31). */
    public record Endereco(String cep, String bairro, String cidade, String uf, boolean completo) {
    }

    public static PerfilResposta de(Usuario usuario) {
        Endereco endereco = usuario.getCep() == null ? null
                : new Endereco(usuario.getCep(), usuario.getBairro(), usuario.getCidade(), usuario.getUf(),
                        usuario.getBairro() != null && usuario.getCidade() != null);
        return new PerfilResposta(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.getPapeis().stream().sorted().toList(), endereco, usuario.isDeveTrocarSenha());
    }
}
