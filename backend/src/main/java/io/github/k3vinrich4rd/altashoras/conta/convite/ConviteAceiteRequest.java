package io.github.k3vinrich4rd.altashoras.conta.convite;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.github.k3vinrich4rd.altashoras.comum.validacao.Senha;

/**
 * O corpo de {@code POST /auth/convite}. A forma (código de 43 caracteres base64url, e-mail e senha de 10 a 72 bytes) é
 * conferida antes de qualquer consulta ao banco (E6): o {@code 400} nunca revela se o código existe. A senha só vale para
 * conta nova; para conta que já tem senha ela não vem (H32). Nunca imprime os dados.
 */
public record ConviteAceiteRequest(
        @NotBlank(message = "obrigatório") @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "código em formato inválido") String codigo,
        @NotBlank(message = "obrigatório") @Email(message = "deve ser um e-mail válido") @Size(max = 254) String email,
        @Senha String senha) {

    @Override
    public String toString() {
        return "ConviteAceiteRequest[dados pessoais omitidos]";
    }
}
