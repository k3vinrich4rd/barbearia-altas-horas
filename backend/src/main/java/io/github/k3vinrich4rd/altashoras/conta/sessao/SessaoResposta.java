package io.github.k3vinrich4rd.altashoras.conta.sessao;

import java.util.List;
import java.util.UUID;

/** Par de tokens. O refresh vem no corpo só para o BFF, que o guarda em cookie HttpOnly (ADR 0006). */
public record SessaoResposta(String accessToken, String tipo, long expiraEmSegundos, String refreshToken,
        String refreshExpiraEm, UsuarioDaSessao usuario, boolean deveTrocarSenha) {

    public record UsuarioDaSessao(UUID id, String nome, List<String> papeis) {
    }

    /** Tokens nunca vão para o log. */
    @Override
    public String toString() {
        return "SessaoResposta[tokens omitidos]";
    }
}
