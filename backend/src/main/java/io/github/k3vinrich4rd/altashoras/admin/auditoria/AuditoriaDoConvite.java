package io.github.k3vinrich4rd.altashoras.admin.auditoria;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.conta.ConviteAceito;
import io.github.k3vinrich4rd.altashoras.conta.ConviteRevogadoPorTentativas;

/**
 * A auditoria ouvindo a conta (Fatia 10B, E13): o aceite e a revogação por tentativas acontecem numa rota pública, sem dono
 * logado, então a conta publica o evento e o painel grava, na mesma transação. O autor do aceite é o próprio barbeiro; o da
 * revogação por tentativas é o dono que gerou o convite (a tabela exige um autor), com {@code origem=TENTATIVAS}. Nunca grava
 * o código, o e-mail, o nome nem a senha (R30).
 */
@Component
class AuditoriaDoConvite {

    private final Auditor auditor;

    AuditoriaDoConvite(Auditor auditor) {
        this.auditor = auditor;
    }

    @EventListener
    void aoAceitar(ConviteAceito evento) {
        auditor.registrar(new Ator(evento.usuarioId(), false, evento.ip()), "CONVITE_ACEITO", "barbeiro",
                evento.barbeiroId(), Map.of("origem", "BARBEIRO"));
    }

    @EventListener
    void aoRevogarPorTentativas(ConviteRevogadoPorTentativas evento) {
        auditor.registrar(new Ator(evento.geradoPor(), false, evento.ip()), "CONVITE_REVOGADO_POR_TENTATIVAS", "barbeiro",
                evento.barbeiroId(), Map.of("origem", "TENTATIVAS"));
    }
}
