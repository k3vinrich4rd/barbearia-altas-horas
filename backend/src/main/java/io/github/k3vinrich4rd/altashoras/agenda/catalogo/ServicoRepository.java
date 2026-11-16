package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServicoRepository extends JpaRepository<Servico, UUID> {

    /** Projeção JPQL direto no DTO: o banco devolve só as 4 colunas e nenhuma entidade é gerenciada (E15). */
    @Query("""
            SELECT new io.github.k3vinrich4rd.altashoras.agenda.catalogo.ServicoResumo(s.id, s.nome, s.duracaoMin, s.precoCentavos)
            FROM Servico s
            WHERE s.ativo = true
            ORDER BY lower(s.nome)
            """)
    List<ServicoResumo> listarAtivos();
}
