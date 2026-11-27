package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Leitura por projeção: uma consulta com JOIN em serviço e barbeiro, em vez de carregar a entidade e buscar o
 * catálogo item a item (N+1, E12). O dono vem sempre do token, nunca de parâmetro da URL.
 */
interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    String PROJECAO = """
            SELECT new io.github.k3vinrich4rd.altashoras.agenda.reserva.LinhaAgendamento(
                r.id, r.status, r.origem, s.id, s.nome, s.duracaoMin, b.id, b.nomeExibicao,
                r.inicio, r.fim, r.precoCentavos, r.expiraEm, r.motivoCancelamento, r.version, r.prorrogacoes)
            FROM Reserva r
            JOIN Servico s ON s.id = r.servicoId
            JOIN Barbeiro b ON b.id = r.barbeiroId
            """;

    @Query(value = PROJECAO + " WHERE r.clienteId = :clienteId ORDER BY r.inicio DESC, r.id",
            countQuery = "SELECT count(r) FROM Reserva r WHERE r.clienteId = :clienteId")
    Page<LinhaAgendamento> doCliente(UUID clienteId, Pageable pagina);

    @Query(PROJECAO + " WHERE r.id = :id AND r.clienteId = :clienteId")
    Optional<LinhaAgendamento> doCliente(UUID clienteId, UUID id);

    @Query(PROJECAO + " WHERE r.id = :id AND r.origem = io.github.k3vinrich4rd.altashoras.agenda.reserva.Reserva.Origem.GUEST")
    Optional<LinhaAgendamento> doVisitante(UUID id);
}
