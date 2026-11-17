package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;

/**
 * Serviço do catálogo. Preço em centavos (ADR 0004). Nunca se apaga: sai de cena com {@code ativo = false}, porque
 * reservas e cobranças antigas apontam para ele (E16). {@code @Version} protege duas telas editando o mesmo serviço.
 */
@Entity
@Table(name = "servico")
public class Servico {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nome;

    private @Nullable String descricao;

    @Column(name = "duracao_min", nullable = false)
    private int duracaoMin;

    @Column(name = "preco_centavos", nullable = false)
    private long precoCentavos;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    /** Objeto, não primitivo: nulo = entidade nova, e o persist não confunde com um serviço que já existe. */
    @Version
    private @Nullable Long version;

    protected Servico() {
    }

    public static Servico novo(String nome, @Nullable String descricao, int duracaoMin, long precoCentavos,
            Instant agora) {
        Servico servico = new Servico();
        servico.id = UUID.randomUUID();
        servico.nome = nome;
        servico.descricao = descricao;
        servico.duracaoMin = duracaoMin;
        servico.precoCentavos = precoCentavos;
        servico.ativo = true;
        servico.criadoEm = agora;
        servico.atualizadoEm = agora;
        return servico;
    }

    public void alterar(String nome, @Nullable String descricao, int duracaoMin, long precoCentavos, boolean ativo,
            Instant agora) {
        this.nome = nome;
        this.descricao = descricao;
        this.duracaoMin = duracaoMin;
        this.precoCentavos = precoCentavos;
        this.ativo = ativo;
        this.atualizadoEm = agora;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public @Nullable String getDescricao() {
        return descricao;
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

    public long getVersion() {
        return version == null ? 0 : version;
    }
}
