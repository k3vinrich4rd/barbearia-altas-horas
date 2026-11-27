package io.github.k3vinrich4rd.altashoras.agenda;

import java.util.List;

import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.ConflitoDaFolga;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * 422 FOLGA_COM_RESERVAS com as reservas ativas que impedem gravar o período (ADR 0015, E4). Quem monta a resposta é o
 * admin, que formata cada conflito como o contrato pede. Os conflitos têm nome de cliente: nunca vão para o log (R30).
 */
public class FolgaComReservas extends ExcecaoDeNegocio {

    private final transient List<ConflitoDaFolga> conflitos;

    FolgaComReservas(List<ConflitoDaFolga> conflitos) {
        super(CodigoErro.FOLGA_COM_RESERVAS);
        this.conflitos = List.copyOf(conflitos);
    }

    public List<ConflitoDaFolga> conflitos() {
        return conflitos;
    }
}
