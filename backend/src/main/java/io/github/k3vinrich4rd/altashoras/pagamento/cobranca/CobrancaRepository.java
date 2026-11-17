package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.k3vinrich4rd.altashoras.pagamento.gateway.Provedor;

interface CobrancaRepository extends JpaRepository<Cobranca, UUID> {

    Optional<Cobranca> findByChaveIdempotenciaHash(String chaveIdempotenciaHash);

    Optional<Cobranca> findFirstByReservaIdAndStatusIn(UUID reservaId, Collection<StatusCobranca> status);

    Optional<Cobranca> findByProvedorAndIdCobrancaProvedor(Provedor provedor, String idCobrancaProvedor);

    Optional<Cobranca> findFirstByReservaIdAndStatusAndIdCobrancaProvedorIsNotNull(UUID reservaId, StatusCobranca status);
}
