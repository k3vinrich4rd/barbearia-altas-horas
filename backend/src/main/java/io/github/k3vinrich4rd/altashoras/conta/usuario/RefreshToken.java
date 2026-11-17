package io.github.k3vinrich4rd.altashoras.conta.usuario;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Refresh token: o banco guarda só o SHA-256 do valor (vazou o banco, não vazou a sessão). A família liga todos
 * os tokens de uma mesma sessão: reuso de um já usado derruba a família inteira (R27).
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "familia_id", nullable = false)
    private UUID familiaId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64, nullable = false)
    private String hash;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    protected RefreshToken() {
    }

    public RefreshToken(UUID usuarioId, UUID familiaId, String hash, Instant criadoEm, Instant expiraEm) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.familiaId = familiaId;
        this.hash = hash;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getFamiliaId() {
        return familiaId;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }
}
