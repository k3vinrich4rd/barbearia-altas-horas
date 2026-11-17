package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.admin.auditoria.Auditor;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.FolgaCadastrada;

/**
 * Folgas do painel (E20, E21). A regra "nenhuma reserva ativa no período" e a trava contra a reserva simultânea são
 * da agenda; aqui só se chama a API pública e se grava a auditoria na MESMA transação (E11). O motivo da folga pode
 * ser pessoal e nunca entra no detalhe (R30).
 */
@Service
class CadastroDeFolgas {

    private final PainelDaAgenda agenda;
    private final Auditor auditor;
    private final ZoneId fuso;

    CadastroDeFolgas(PainelDaAgenda agenda, Auditor auditor, @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.agenda = agenda;
        this.auditor = auditor;
        this.fuso = ZoneId.of(fuso);
    }

    @Transactional(readOnly = true)
    List<FolgaAdmin> listar(UUID barbeiroId) {
        return agenda.folgas(barbeiroId).stream().map(f -> FolgaAdmin.de(f, fuso)).toList();
    }

    @Transactional
    FolgaAdmin criar(Ator ator, UUID barbeiroId, FolgaRequest pedido) {
        FolgaCadastrada criada = agenda.criarFolga(barbeiroId, ator.id(), pedido.inicio().toInstant(),
                pedido.fim().toInstant(), pedido.motivo());
        auditor.registrar(ator, "FOLGA_CRIADA", "folga", criada.id(), Map.of("barbeiroId", barbeiroId.toString()));
        return FolgaAdmin.de(criada, fuso);
    }

    @Transactional
    void remover(Ator ator, UUID barbeiroId, UUID folgaId) {
        agenda.removerFolga(barbeiroId, folgaId);
        auditor.registrar(ator, "FOLGA_REMOVIDA", "folga", folgaId, Map.of("barbeiroId", barbeiroId.toString()));
    }
}
