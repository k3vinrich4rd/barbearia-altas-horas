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
}
