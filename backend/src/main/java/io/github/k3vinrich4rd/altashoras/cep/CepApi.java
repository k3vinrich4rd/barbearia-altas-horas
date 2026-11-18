package io.github.k3vinrich4rd.altashoras.cep;

import java.util.Optional;

import io.github.k3vinrich4rd.altashoras.comum.valor.Cep;

/**
 * Porta do módulo CEP (ADR 0002). Quem quer um endereço (a rota pública e o cadastro) usa só isto, e com isto ganha o
 * mesmo cache e a mesma degradação.
 */
public interface CepApi {

    /**
     * @return o endereço, ou vazio quando o CEP não existe
     * @throws CepIndisponivel quando nenhum provedor conseguiu responder (nada é guardado em cache)
     * @throws IllegalArgumentException se o CEP não tem 8 dígitos
     */
    Optional<Endereco> buscar(Cep cep);
}
