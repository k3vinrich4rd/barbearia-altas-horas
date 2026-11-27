package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import io.github.k3vinrich4rd.altashoras.admin.FormatoDeData;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.ConflitoDaFolga;

/**
 * Uma reserva ativa dentro de um período de folga (1.12.0, schema {@code ConflitoDeFolga}): a prévia, a extensão
 * {@code conflitos[]} do 422 e o {@code reservasMantidas[]}. Só para o ADMIN; sem e-mail, CPF, WhatsApp nem observação,
 * e o toString não imprime o nome do cliente (R30).
 */
record ConflitoDeFolga(UUID reservaId, String inicio, String fim, Servico servico, String clienteNome, String origem,
        String status) {

    record Servico(UUID id, String nome) {
    }

    static List<ConflitoDeFolga> de(List<ConflitoDaFolga> conflitos, ZoneId fuso) {
        return conflitos.stream().map(c -> new ConflitoDeFolga(c.reservaId(), FormatoDeData.formatar(c.inicio(), fuso),
                FormatoDeData.formatar(c.fim(), fuso), new Servico(c.servicoId(), c.servicoNome()), c.clienteNome(),
                c.origem(), c.status())).toList();
    }

    @Override
    public String toString() {
        return "ConflitoDeFolga[" + reservaId + ", " + status + "]";
    }
}
