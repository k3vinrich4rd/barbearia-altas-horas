package io.github.k3vinrich4rd.altashoras.conta.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /** O e-mail é gravado já normalizado (minúsculas), então a comparação é direta. */
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT count(u) > 0 FROM Usuario u JOIN u.papeis p WHERE p = :papel")
    boolean existeComPapel(String papel);

    /** A conta existe, está ativa e tem o papel (a API pública {@code conta.Contas} usa). */
    @Query("SELECT count(u) > 0 FROM Usuario u JOIN u.papeis p WHERE u.id = :id AND u.ativo = true AND p = :papel")
    boolean ativoComPapel(UUID id, String papel);

    /** Das contas pedidas, as que estão ativas e têm o papel: uma consulta para a lista do painel (sem N+1). */
    @Query("SELECT DISTINCT u.id FROM Usuario u JOIN u.papeis p WHERE u.id IN :ids AND u.ativo = true AND p = :papel")
    java.util.List<UUID> ativasComPapel(java.util.Collection<UUID> ids, String papel);

    /** O desligamento apaga a identidade social da conta de equipe (H33): nativo porque não há entidade. */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM identidade_social WHERE usuario_id = :usuario", nativeQuery = true)
    int apagarIdentidadesSociais(UUID usuario);

    /**
     * Conta que NÃO pode mais usar o token: existe e foi desativada, ou (com {@code exigeBarbeiro}) o token carrega o papel
     * BARBEIRO e a conta já não o tem (o desligamento de quem também é cliente, H31). Uma consulta só por requisição.
     */
    @Query("""
            SELECT count(u) > 0 FROM Usuario u
            WHERE u.id = :id AND (u.ativo = false
                OR (:exigeBarbeiro = true AND NOT EXISTS (SELECT 1 FROM Usuario u2 JOIN u2.papeis p WHERE u2.id = u.id AND p = 'BARBEIRO')))
            """)
    boolean barrada(UUID id, boolean exigeBarbeiro);

    /** A conta existe e foi desativada (exclusão ou desligamento): o token dela deixa de valer (E16). */
    @Query("SELECT count(u) > 0 FROM Usuario u WHERE u.id = :id AND u.ativo = false")
    boolean desativada(UUID id);
}
