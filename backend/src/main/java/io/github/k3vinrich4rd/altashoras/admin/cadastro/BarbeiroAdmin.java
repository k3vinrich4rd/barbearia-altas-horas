package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.BarbeiroCadastrado;

/** O barbeiro como o painel vê (contrato {@code BarbeiroAdmin}): com a conta ligada, o estado e a jornada. */
record BarbeiroAdmin(UUID id, String nomeExibicao, @Nullable UUID usuarioId, @Nullable String whatsapp, boolean ativo,
        long version, List<IntervaloJornada> jornada) {

    static BarbeiroAdmin de(BarbeiroCadastrado b) {
        return new BarbeiroAdmin(b.id(), b.nomeExibicao(), b.usuarioId(), b.whatsapp(), b.ativo(), b.version(),
                b.jornada().stream().map(IntervaloJornada::de).toList());
    }
}
