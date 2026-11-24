package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Para quem entregar. Canais "por aparelho" (Web Push) recebem {@code aparelho}; os de conta (e-mail) recebem
 * {@code email}. Endpoint, chaves e e-mail são dado pessoal do barbeiro: o toString não imprime nenhum (R30).
 */
public record DestinoDoAviso(UUID usuarioId, @Nullable Aparelho aparelho, @Nullable String email) {

    public record Aparelho(String endpoint, String chaveP256dh, String chaveAuth) {

        @Override
        public String toString() {
            return "Aparelho[omitido]";
        }
    }

    @Override
    public String toString() {
        return "DestinoDoAviso[" + usuarioId + ", dados omitidos]";
    }
}
