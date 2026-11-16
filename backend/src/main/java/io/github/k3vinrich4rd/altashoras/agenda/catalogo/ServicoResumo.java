package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import java.util.UUID;

import io.github.k3vinrich4rd.altashoras.comum.PrecoFormatado;

/** O que o contrato mostra de um serviço. Nada de "ativo" ou "version": o que não está aqui não vaza. */
public record ServicoResumo(UUID id, String nome, int duracaoMin, long precoCentavos, String precoFormatado) {

    /** Construtor usado pela projeção JPQL (SELECT new ...), que não conhece o preço formatado. */
    public ServicoResumo(UUID id, String nome, int duracaoMin, long precoCentavos) {
        this(id, nome, duracaoMin, precoCentavos, PrecoFormatado.de(precoCentavos));
    }
}
