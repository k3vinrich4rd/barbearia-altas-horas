package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;

/**
 * As regras que a barbearia muda sem deploy (linha única, id = 1). Nenhuma delas é fixa no código (D15).
 * {@code @Version}: duas telas salvando a configuração ao mesmo tempo não se sobrescrevem (409 CONFLITO_DE_VERSAO).
 */
@Entity
@Table(name = "configuracao_agenda")
public class ConfiguracaoAgenda {

    public static final short ID_UNICO = 1;

    @Id
    private short id;

    @Column(nullable = false)
    private String fuso;

    @Column(name = "antecedencia_minima_min", nullable = false)
    private int antecedenciaMinimaMin;

    @Column(name = "intervalo_entre_atendimentos_min", nullable = false)
    private int intervaloEntreAtendimentosMin;

    @Column(name = "granularidade_min", nullable = false)
    private int granularidadeMin;

    @Column(name = "horizonte_dias", nullable = false)
    private int horizonteDias;

    @Column(name = "expiracao_pagamento_min", nullable = false)
    private int expiracaoPagamentoMin;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "atualizado_por")
    private @Nullable UUID atualizadoPor;

    @Version
    private long version;

    protected ConfiguracaoAgenda() {
    }

    /** O fuso não muda pelo painel (só leitura, E22). Valores já validados pelo chamador. */
    public void alterar(int antecedenciaMinimaMin, int intervaloEntreAtendimentosMin, int granularidadeMin,
            int horizonteDias, int expiracaoPagamentoMin, UUID atualizadoPor, Instant agora) {
        this.antecedenciaMinimaMin = antecedenciaMinimaMin;
        this.intervaloEntreAtendimentosMin = intervaloEntreAtendimentosMin;
        this.granularidadeMin = granularidadeMin;
        this.horizonteDias = horizonteDias;
        this.expiracaoPagamentoMin = expiracaoPagamentoMin;
        this.atualizadoPor = atualizadoPor;
        this.atualizadoEm = agora;
    }

    public String getFuso() {
        return fuso;
    }

    public int getAntecedenciaMinimaMin() {
        return antecedenciaMinimaMin;
    }

    public int getIntervaloEntreAtendimentosMin() {
        return intervaloEntreAtendimentosMin;
    }

    public int getGranularidadeMin() {
        return granularidadeMin;
    }

    public int getExpiracaoPagamentoMin() {
        return expiracaoPagamentoMin;
    }

    public int getHorizonteDias() {
        return horizonteDias;
    }

    public long getVersion() {
        return version;
    }
}
