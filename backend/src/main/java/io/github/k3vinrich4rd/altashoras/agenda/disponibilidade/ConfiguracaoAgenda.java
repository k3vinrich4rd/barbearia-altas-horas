package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** As regras que a barbearia muda sem deploy (linha única, id = 1). Nenhuma delas é fixa no código (D15). */
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

    protected ConfiguracaoAgenda() {
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

    public int getHorizonteDias() {
        return horizonteDias;
    }
}
