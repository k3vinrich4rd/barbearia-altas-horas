package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.ServicoCadastrado;
import io.github.k3vinrich4rd.altashoras.comum.PrecoFormatado;

/** O serviço como o painel vê (contrato {@code ServicoAdmin}): o público mais descrição, {@code ativo} e {@code version}. */
record ServicoAdmin(UUID id, String nome, @Nullable String descricao, int duracaoMin, long precoCentavos,
        String precoFormatado, boolean ativo, long version) {

    static ServicoAdmin de(ServicoCadastrado s) {
        return new ServicoAdmin(s.id(), s.nome(), s.descricao(), s.duracaoMin(), s.precoCentavos(),
                PrecoFormatado.de(s.precoCentavos()), s.ativo(), s.version());
    }
}
