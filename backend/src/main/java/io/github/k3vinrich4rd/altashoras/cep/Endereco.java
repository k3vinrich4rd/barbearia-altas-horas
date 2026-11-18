package io.github.k3vinrich4rd.altashoras.cep;

import org.jspecify.annotations.Nullable;

/**
 * Endereço de um CEP, do jeito que o contrato mostra. Logradouro e bairro podem faltar (CEP "geral" de cidade pequena).
 * O CEP aparece aqui só como dígitos para o contrato; em log, nunca (R30).
 */
public record Endereco(String cep, @Nullable String logradouro, @Nullable String bairro, @Nullable String cidade,
        @Nullable String uf, Fonte fonte) {

    /** Quem respondeu: um provedor ou a memória da API (CACHE). */
    public enum Fonte {
        VIACEP, BRASILAPI, CACHE
    }

    public Endereco comFonte(Fonte nova) {
        return new Endereco(cep, logradouro, bairro, cidade, uf, nova);
    }

    /** Nunca imprime o CEP nem o endereço: um log descuidado do objeto não vaza nada (R30). */
    @Override
    public String toString() {
        return "Endereco[dados pessoais omitidos, fonte=" + fonte + "]";
    }
}
