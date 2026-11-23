package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.conta.ConviteAceito;
import io.github.k3vinrich4rd.altashoras.conta.Contas;

/**
 * Quando o barbeiro aceita o convite, a conta que nasceu (ou ganhou o papel) é ligada ao cadastro dele (Fatia 10B). A agenda
 * escuta o evento da conta, que não sabe que a agenda existe (ADR 0002). O listener é SÍNCRONO: roda na transação do aceite,
 * e se o vínculo não puder ser feito tudo desfaz e o aceite responde o mesmo {@code 422 CONVITE_INVALIDO}.
 * Recusa quando o barbeiro foi desligado, ou quando já tem outra conta de barbeiro ativa; uma conta antiga sem o papel
 * (desligada) pode ser trocada pela nova (H31: reativar exige convite novo). A versão sobe, para o formulário que o dono
 * deixou aberto não apagar o vínculo com um PUT velho.
 */
@Component
class VinculoDoConvite {

    private final JdbcClient jdbc;
    private final Contas contas;
    private final Clock clock;

    VinculoDoConvite(JdbcClient jdbc, Contas contas, Clock clock) {
        this.jdbc = jdbc;
        this.contas = contas;
        this.clock = clock;
    }

    @EventListener
    void aoAceitarConvite(ConviteAceito evento) {
        var barbeiro = jdbc.sql("SELECT ativo, usuario_id FROM barbeiro WHERE id = :id FOR UPDATE")
                .param("id", evento.barbeiroId())
                .query((rs, n) -> new Object[] {rs.getBoolean("ativo"), rs.getObject("usuario_id", UUID.class)})
                .optional().orElseThrow(VinculoDoConvite::recusado);
        UUID atual = (UUID) barbeiro[1];
        boolean ativo = (Boolean) barbeiro[0];
        boolean temOutraContaAtiva = atual != null && !atual.equals(evento.usuarioId()) && contas.temPapel(atual, "BARBEIRO");
        if (!ativo || temOutraContaAtiva) {
            throw recusado();
        }
        jdbc.sql("UPDATE barbeiro SET usuario_id = :usuario, version = version + 1, atualizado_em = :agora WHERE id = :id")
                .param("usuario", evento.usuarioId()).param("agora", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .param("id", evento.barbeiroId()).update();
    }

    private static ExcecaoDeNegocio recusado() {
        return new ExcecaoDeNegocio(CodigoErro.CONVITE_INVALIDO);
    }
}
