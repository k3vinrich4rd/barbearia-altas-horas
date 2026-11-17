package io.github.k3vinrich4rd.altashoras.admin.pagamento;

import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.admin.FormatoDeData;
import io.github.k3vinrich4rd.altashoras.admin.auditoria.Auditor;
import io.github.k3vinrich4rd.altashoras.pagamento.PendenciasDePagamento;

/** Registra a devolução feita fora do app (E13) e a auditoria na mesma transação (E11). */
@Service
class RegistrarDevolucao {

    /** A resposta do contrato: a cobrança já DEVOLVIDA, com a data da devolução. */
    record Devolvida(UUID id, UUID reservaId, String status, String metodo, long valorCentavos, String devolvidaEm) {
    }

    private final PendenciasDePagamento pagamentos;
    private final Auditor auditor;
    private final ZoneId fuso;

    RegistrarDevolucao(PendenciasDePagamento pagamentos, Auditor auditor, @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.pagamentos = pagamentos;
        this.auditor = auditor;
        this.fuso = ZoneId.of(fuso);
    }

    @Transactional
    Devolvida devolver(Ator ator, UUID id) {
        PendenciasDePagamento.Cobranca cobranca = pagamentos.devolver(id, ator.id());
        auditor.registrar(ator, "DEVOLUCAO_REGISTRADA", "cobranca", id,
                Map.of("para", cobranca.status(), "reservaId", cobranca.reservaId().toString()));
        return new Devolvida(cobranca.id(), cobranca.reservaId(), cobranca.status(), cobranca.metodo(),
                cobranca.valorCentavos(), FormatoDeData.formatar(cobranca.devolvidaEm(), fuso));
    }
}
