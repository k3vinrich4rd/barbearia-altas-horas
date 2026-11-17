package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Serviço do catálogo. Só leitura nesta fatia; o admin edita na Fatia 5. Preço em centavos (ADR 0004). */
@Entity
@Table(name = "servico")
public class Servico {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "duracao_min", nullable = false)
    private int duracaoMin;

    @Column(name = "preco_centavos", nullable = false)
    private long precoCentavos;

    @Column(nullable = false)
    private boolean ativo;

    protected Servico() {
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public int getDuracaoMin() {
        return duracaoMin;
    }

    public long getPrecoCentavos() {
        return precoCentavos;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
