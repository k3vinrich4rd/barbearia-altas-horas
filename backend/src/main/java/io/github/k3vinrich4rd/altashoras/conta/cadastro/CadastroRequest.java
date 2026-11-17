package io.github.k3vinrich4rd.altashoras.conta.cadastro;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.github.k3vinrich4rd.altashoras.comum.validacao.CepBr;
import io.github.k3vinrich4rd.altashoras.comum.validacao.Senha;
import io.github.k3vinrich4rd.altashoras.comum.validacao.TelefoneBr;

/**
 * Só os campos do contrato. Não existe "papeis", "id" nem "ativo" aqui: mandar um deles é 400
 * CAMPO_DESCONHECIDO, e ninguém se cadastra como ADMIN (E16).
 */
public record CadastroRequest(
        @NotBlank(message = "obrigatório") @Size(min = 2, max = 120, message = "deve ter de 2 a 120 caracteres") String nome,
        @NotBlank(message = "obrigatório") @Email(message = "deve ser um e-mail válido") @Size(max = 254) String email,
        @NotBlank(message = "obrigatório") @TelefoneBr String whatsapp,
        @NotNull(message = "obrigatória") @Senha String senha,
        @CepBr String cep,
        Boolean aceitaMarketing) {

    /** Nunca imprime os dados: um log descuidado do request não vaza nada. */
    @Override
    public String toString() {
        return "CadastroRequest[dados pessoais omitidos]";
    }
}
