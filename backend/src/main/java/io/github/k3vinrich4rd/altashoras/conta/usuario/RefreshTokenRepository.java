package io.github.k3vinrich4rd.altashoras.conta.usuario;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByHash(String hash);

    /**
     * A rotação inteira numa instrução: só marca como usado se ainda estiver válido. Devolve 1 para quem venceu e
     * 0 para todos os outros, mesmo com várias requisições no mesmo instante: quem decide é o banco (E6).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken r SET r.usadoEm = :agora
            WHERE r.hash = :hash AND r.usadoEm IS NULL AND r.revogadoEm IS NULL AND r.expiraEm > :agora
            """)
    int marcarUsado(String hash, Instant agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revogadoEm = :agora WHERE r.familiaId = :familia AND r.revogadoEm IS NULL")
    int revogarFamilia(UUID familia, Instant agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revogadoEm = :agora WHERE r.usuarioId = :usuario AND r.revogadoEm IS NULL")
    int revogarDoUsuario(UUID usuario, Instant agora);

    /** Exclusão da conta (E16): os refresh tokens saem do banco, não só ficam revogados. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.usuarioId = :usuario")
    int apagarDoUsuario(UUID usuario);
}
