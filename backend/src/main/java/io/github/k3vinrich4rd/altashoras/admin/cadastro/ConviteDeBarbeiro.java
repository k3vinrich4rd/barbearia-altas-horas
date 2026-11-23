package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.admin.FormatoDeData;
import io.github.k3vinrich4rd.altashoras.admin.auditoria.Auditor;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.BarbeiroCadastrado;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.conta.Contas;
import io.github.k3vinrich4rd.altashoras.conta.ConviteEmitido;

/**
 * Gerar, reenviar e revogar o convite do barbeiro (Fatia 10B): só o ADMIN, na MESMA transação da auditoria (E12). A agenda
 * diz quem é o barbeiro; a conta guarda o convite. A auditoria leva só ids e o vencimento, nunca o código (R30, E13).
 */
@Service
class ConviteDeBarbeiro {

    private final PainelDaAgenda agenda;
    private final Contas contas;
    private final Auditor auditor;
    private final ZoneId fuso;

    ConviteDeBarbeiro(PainelDaAgenda agenda, Contas contas, Auditor auditor,
            @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.agenda = agenda;
        this.contas = contas;
        this.auditor = auditor;
        this.fuso = ZoneId.of(fuso);
    }

    /** Também é o "reenviar": o convite pendente anterior é revogado e o código antigo deixa de valer. */
    @Transactional
    ConviteGerado gerar(Ator ator, UUID barbeiroId) {
        BarbeiroCadastrado barbeiro = agenda.barbeiro(barbeiroId);
        boolean temContaAtiva = barbeiro.usuarioId() != null && barbeiro.ativo()
                && contas.comPapel(List.of(barbeiro.usuarioId()), "BARBEIRO").contains(barbeiro.usuarioId());
        if (temContaAtiva) {
            throw new ExcecaoDeNegocio(CodigoErro.USUARIO_JA_VINCULADO, "Este barbeiro já tem acesso ao painel.");
        }
        ConviteEmitido convite = contas.emitirConvite(barbeiroId, barbeiro.nomeExibicao(), ator.id());
        String expiraEm = FormatoDeData.formatar(convite.expiraEm(), fuso);
        auditor.registrar(ator, "CONVITE_EMITIDO", "barbeiro", barbeiroId, Map.of("expiraEm", expiraEm));
        return new ConviteGerado(convite.codigo(), expiraEm);
    }

    /** Idempotente: sem convite pendente também responde sem erro, e nada é gravado na auditoria. */
    @Transactional
    void revogar(Ator ator, UUID barbeiroId) {
        agenda.barbeiro(barbeiroId);
        if (contas.revogarConvite(barbeiroId)) {
            auditor.registrar(ator, "CONVITE_REVOGADO", "barbeiro", barbeiroId, Map.of("origem", "DONO"));
        }
    }
}
