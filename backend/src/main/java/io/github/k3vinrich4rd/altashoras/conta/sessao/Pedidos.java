package io.github.k3vinrich4rd.altashoras.conta.sessao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.github.k3vinrich4rd.altashoras.comum.validacao.Senha;

/** Corpos das rotas de sessão. Os toString() não imprimem senha nem token. */
final class Pedidos {

    private Pedidos() {
    }

    record Login(@NotBlank(message = "obrigatório") String email, @NotBlank(message = "obrigatória") String senha) {

        @Override
        public String toString() {
            return "Login[omitido]";
        }
    }

    record Refresh(@NotBlank(message = "obrigatório") String refreshToken) {

        @Override
        public String toString() {
            return "Refresh[omitido]";
        }
    }

    record TrocaDeSenha(@NotBlank(message = "obrigatória") String senhaAtual,
            @NotNull(message = "obrigatória") @Senha String novaSenha) {

        @Override
        public String toString() {
            return "TrocaDeSenha[omitido]";
        }
    }
}
