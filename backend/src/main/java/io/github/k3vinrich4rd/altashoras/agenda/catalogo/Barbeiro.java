package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Barbeiro. Hoje a barbearia tem um só, mas a entidade fica: o mesmo código atende quem tiver equipe.
 * A jornada é LAZY: quem precisa dela pede com JOIN FETCH (BarbeiroRepository), numa consulta só.
 */
@Entity
@Table(name = "barbeiro")
public class Barbeiro {

    @Id
    private UUID id;

    @Column(name = "nome_exibicao", nullable = false)
    private String nomeExibicao;

    @Column(nullable = false)
    private boolean ativo;

    @OneToMany(mappedBy = "barbeiro", fetch = FetchType.LAZY)
    private List<Jornada> jornadas = new ArrayList<>();

    protected Barbeiro() {
    }

    public UUID getId() {
        return id;
    }

    public String getNomeExibicao() {
        return nomeExibicao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public List<Jornada> getJornadas() {
        return jornadas;
    }
}
