package io.github.k3vinrich4rd.altashoras.pagamento.politica;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PoliticaPagamentoRepository extends JpaRepository<PoliticaPagamento, Integer> {

    /** A vigente é a de maior versão (E13). */
    Optional<PoliticaPagamento> findFirstByOrderByVersaoDesc();
}
