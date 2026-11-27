package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.admin.auditoria.Auditor;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.FolgaComReservas;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.ConflitoDaFolga;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.FolgaGravada;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.PedidoDeFolga;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * Folgas e férias do painel (Fatia 5, E20 e E21; Fatia 15, ADR 0015). A regra dos conflitos e a trava contra a reserva
 * simultânea são da agenda; aqui só se chama a API pública, se formata a resposta e se grava a auditoria na MESMA
 * transação (E11). O motivo da folga e o nome dos clientes nunca entram no detalhe da auditoria nem no log (R30): o
 * detalhe guarda só o barbeiro, o tipo e, com {@code manterReservas}, a quantidade e os ids das reservas mantidas.
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
    FolgaSalva criar(Ator ator, UUID barbeiroId, FolgaRequest pedido) {
        FolgaGravada criada = comConflitosFormatados(() -> agenda.criarFolga(barbeiroId, ator.id(), pedidoDe(pedido)));
        auditor.registrar(ator, "FOLGA_CRIADA", "folga", criada.folga().id(), detalhe(barbeiroId, criada));
        return FolgaSalva.de(criada, fuso);
    }

    @Transactional
    FolgaSalva alterar(Ator ator, UUID barbeiroId, UUID folgaId, FolgaRequest pedido) {
        FolgaGravada alterada = comConflitosFormatados(
                () -> agenda.alterarFolga(barbeiroId, folgaId, ator.id(), pedidoDe(pedido)));
        auditor.registrar(ator, "FOLGA_ALTERADA", "folga", folgaId, detalhe(barbeiroId, alterada));
        return FolgaSalva.de(alterada, fuso);
    }

    @Transactional(readOnly = true)
    List<ConflitoDeFolga> conflitos(UUID barbeiroId, OffsetDateTime inicio, OffsetDateTime fim) {
        return ConflitoDeFolga.de(agenda.conflitosDaFolga(barbeiroId, inicio.toInstant(), fim.toInstant()), fuso);
    }

    @Transactional
    void remover(Ator ator, UUID barbeiroId, UUID folgaId) {
        agenda.removerFolga(barbeiroId, folgaId);
        auditor.registrar(ator, "FOLGA_REMOVIDA", "folga", folgaId, Map.of("barbeiroId", barbeiroId.toString()));
    }

    private static PedidoDeFolga pedidoDe(FolgaRequest pedido) {
        return new PedidoDeFolga(pedido.inicio().toInstant(), pedido.fim().toInstant(), pedido.motivo(),
                pedido.tipoOuPadrao(), pedido.manter());
    }

    /** O 422 sai com a extensão {@code conflitos[]} no formato do contrato (datas no fuso, serviço aninhado). */
    private FolgaGravada comConflitosFormatados(java.util.function.Supplier<FolgaGravada> gravar) {
        try {
            return gravar.get();
        } catch (FolgaComReservas recusada) {
            throw new ExcecaoDeNegocio(CodigoErro.FOLGA_COM_RESERVAS)
                    .comExtensao("conflitos", ConflitoDeFolga.de(recusada.conflitos(), fuso));
        }
    }

    /** Só ids e contagem: nem o motivo nem o nome dos clientes (R30, E11). */
    private static Map<String, String> detalhe(UUID barbeiroId, FolgaGravada gravada) {
        Map<String, String> detalhe = new LinkedHashMap<>();
        detalhe.put("barbeiroId", barbeiroId.toString());
        detalhe.put("tipo", gravada.folga().tipo());
        List<ConflitoDaFolga> mantidas = gravada.reservasMantidas();
        if (!mantidas.isEmpty()) {
            detalhe.put("reservasMantidas", Integer.toString(mantidas.size()));
            detalhe.put("reservasMantidasIds",
                    mantidas.stream().map(c -> c.reservaId().toString()).collect(Collectors.joining(",")));
        }
        return detalhe;
    }
}
