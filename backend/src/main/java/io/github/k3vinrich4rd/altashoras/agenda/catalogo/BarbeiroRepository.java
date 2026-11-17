package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BarbeiroRepository extends JpaRepository<Barbeiro, UUID> {

    @Query("""
            SELECT new io.github.k3vinrich4rd.altashoras.agenda.catalogo.BarbeiroResumo(b.id, b.nomeExibicao)
            FROM Barbeiro b
            WHERE b.ativo = true
            ORDER BY lower(b.nomeExibicao)
            """)
    List<BarbeiroResumo> listarAtivos();

    /**
     * Barbeiros ativos já com as jornadas, em UMA consulta (E15). Sem o JOIN FETCH, cada getJornadas() faria
     * outra consulta: o N+1. LEFT para o barbeiro sem jornada continuar na lista (E10).
     * Sem DISTINCT: desde o Hibernate 6 cada entidade já volta uma vez só, e o PostgreSQL recusa
     * SELECT DISTINCT com ORDER BY lower(...) fora da lista de colunas.
     */
    @Query("""
            SELECT b
            FROM Barbeiro b
            LEFT JOIN FETCH b.jornadas
            WHERE b.ativo = true
            ORDER BY lower(b.nomeExibicao)
            """)
    List<Barbeiro> listarAtivosComJornadas();
}
