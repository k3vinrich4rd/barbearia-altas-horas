package io.github.k3vinrich4rd.altashoras.pagamento.politica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** O texto que o cliente aceita antes de pagar. Mudou o texto, nasce uma versão; a reserva guarda qual aceitou. */
@Entity
@Table(name = "politica_pagamento")
public class PoliticaPagamento {

    @Id
    private int versao;

    @Column(nullable = false)
    private String texto;

    protected PoliticaPagamento() {
    }

    public int getVersao() {
        return versao;
    }

    public String getTexto() {
        return texto;
    }
}
