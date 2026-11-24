package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import org.jspecify.annotations.Nullable;

/**
 * Bairro, cidade e uf copiados para a reserva quando ela nasce (Fatia 6C, E30): do cadastro do cliente ou do CEP que o
 * visitante autorizou, junto com a versão do texto aceito (a prova, Fatia 11). O bairro pode faltar (CEP "geral" de cidade pequena). Não identifica ninguém sozinho (E34), mas o
 * toString não imprime nada: região de uma pessoa não vai para log (R30).
 */
record RegiaoDaReserva(@Nullable String bairro, String cidade, String uf, @Nullable String municipioIbge,
        String versaoConsentimento) {

    @Override
    public String toString() {
        return "RegiaoDaReserva[omitida]";
    }
}
