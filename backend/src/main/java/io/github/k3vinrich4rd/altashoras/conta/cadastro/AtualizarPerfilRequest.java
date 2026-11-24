package io.github.k3vinrich4rd.altashoras.conta.cadastro;

import jakarta.validation.constraints.Size;

import io.github.k3vinrich4rd.altashoras.comum.validacao.CepBr;

/**
 * O corpo de {@code PUT /me}: só a região (Fatia 11). Qualquer outro campo é 400 CAMPO_DESCONHECIDO. O CEP só vale com
 * {@code autorizaRegiao = true}; sem isso (ou sem CEP) a chamada é uma revogação. Nunca imprime os dados (R30).
 */
public record AtualizarPerfilRequest(
        @CepBr String cep,
        Boolean autorizaRegiao,
        @Size(max = 20, message = "deve ter no máximo 20 caracteres") String versaoTextoRegiao) {

    @Override
    public String toString() {
        return "AtualizarPerfilRequest[dados pessoais omitidos]";
    }
}
