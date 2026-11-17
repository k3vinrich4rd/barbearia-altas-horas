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

    /** Todos, inclusive inativos, para o painel. Uma consulta só. */
    @Query("SELECT s FROM Servico s ORDER BY lower(s.nome)")
    List<Servico> listarTodos();

    /** Nome já usado (sem diferença de maiúsculas, como o índice {@code servico_nome_uk}). */
    @Query("SELECT count(s) > 0 FROM Servico s WHERE lower(s.nome) = lower(:nome)")
    boolean existeComNome(String nome);

    @Query("SELECT count(s) > 0 FROM Servico s WHERE lower(s.nome) = lower(:nome) AND s.id <> :id")
    boolean existeOutroComNome(String nome, UUID id);
}
