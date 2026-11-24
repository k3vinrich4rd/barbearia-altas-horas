package io.github.k3vinrich4rd.altashoras.conta.usuario;

import java.util.List;
import java.util.UUID;

/**
 * O que a API mostra de uma conta. Não existe campo de senha aqui: o que não está no record não vaza. Desde a Fatia 11 o CEP
 * não é guardado: {@code endereco.cep} fica {@code deprecated} e sempre nulo (sai na próxima major), e {@code regiaoAutorizada}
 * diz se a pessoa autoriza o uso do bairro e da cidade no alcance.
 */
public record PerfilResposta(UUID id, String nome, String email, List<String> papeis, Endereco endereco,
        boolean regiaoAutorizada, boolean deveTrocarSenha) {

    /** {@code completo} fica falso enquanto bairro e cidade não forem resolvidos pelo CEP (R31). {@code cep} é sempre nulo. */
    public record Endereco(String cep, String bairro, String cidade, String uf, boolean completo) {
    }

    public static PerfilResposta de(Usuario usuario, boolean regiaoAutorizada) {
        Endereco endereco = usuario.getCidade() == null && usuario.getBairro() == null ? null
                : new Endereco(null, usuario.getBairro(), usuario.getCidade(), usuario.getUf(),
                        usuario.getBairro() != null && usuario.getCidade() != null);
        return new PerfilResposta(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.getPapeis().stream().sorted().toList(), endereco, regiaoAutorizada, usuario.isDeveTrocarSenha());
    }
}
