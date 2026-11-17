package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;

/**
 * Barbeiro. Hoje a barbearia tem um só, mas a entidade fica: o mesmo código atende quem tiver equipe.
 * A jornada é LAZY: quem precisa dela pede com JOIN FETCH (BarbeiroRepository), numa consulta só.
 * {@code usuario_id} liga o cadastro à conta de login (papel BARBEIRO), uma conta por barbeiro (E18).
 */
@Entity
@Table(name = "barbeiro")
public class Barbeiro {

    @Id
    private UUID id;

    @Column(name = "nome_exibicao", nullable = false)
    private String nomeExibicao;

    @Column(name = "usuario_id")
    private @Nullable UUID usuarioId;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    /** Objeto, não primitivo: nulo = entidade nova, e o persist não confunde com um barbeiro que já existe. */
    @Version
    private @Nullable Long version;

    @OneToMany(mappedBy = "barbeiro", fetch = FetchType.LAZY)
    private List<Jornada> jornadas = new ArrayList<>();

    protected Barbeiro() {
    }

    public static Barbeiro novo(String nomeExibicao, @Nullable UUID usuarioId, Instant agora) {
        Barbeiro barbeiro = new Barbeiro();
        barbeiro.id = UUID.randomUUID();
        barbeiro.nomeExibicao = nomeExibicao;
        barbeiro.usuarioId = usuarioId;
        barbeiro.ativo = true;
        barbeiro.criadoEm = agora;
        barbeiro.atualizadoEm = agora;
        return barbeiro;
    }

    public void alterar(String nomeExibicao, @Nullable UUID usuarioId, boolean ativo, Instant agora) {
        this.nomeExibicao = nomeExibicao;
        this.usuarioId = usuarioId;
        this.ativo = ativo;
        this.atualizadoEm = agora;
    }

    public UUID getId() {
        return id;
    }

    public String getNomeExibicao() {
        return nomeExibicao;
    }

    public @Nullable UUID getUsuarioId() {
        return usuarioId;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public long getVersion() {
        return version == null ? 0 : version;
    }

    public List<Jornada> getJornadas() {
        return jornadas;
    }
}
