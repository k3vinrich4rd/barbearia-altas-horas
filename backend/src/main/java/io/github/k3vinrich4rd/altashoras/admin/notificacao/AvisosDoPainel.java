package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.admin.FormatoDeData;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * O selo e a lista "Novidades" do painel (E14): SEMPRE da conta de quem chama (o id vem do token, nunca de parâmetro).
 * A lista são exatamente 2 consultas indexadas (a página e a contagem) e o limite de 50 impede lista enorme.
 */
@Service
class AvisosDoPainel {

    static final int LIMITE_PADRAO = 20;
    static final int LIMITE_MAXIMO = 50;

    /** Um item da lista. Sem nome nem WhatsApp do cliente (D173). */
    record Item(UUID id, UUID reservaId, String servico, String inicio, String criadoEm, boolean lido) {
    }

    record Lista(long naoLidas, List<Item> itens) {
    }

    private final Avisos avisos;
    private final Clock clock;
    private final ZoneId fuso;

    AvisosDoPainel(Avisos avisos, Clock clock, @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.avisos = avisos;
        this.clock = clock;
        this.fuso = ZoneId.of(fuso);
    }

    @Transactional(readOnly = true)
    Lista listar(UUID usuarioId, int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw ExcecaoDeNegocio.campoInvalido("limite", "Informe de 1 a " + LIMITE_MAXIMO + ".");
        }
        List<Item> itens = avisos.listar(usuarioId, limite).stream()
                .map(l -> new Item(l.id(), l.reservaId(), l.servico(), FormatoDeData.formatar(l.inicio(), fuso),
                        FormatoDeData.formatar(l.criadoEm(), fuso), l.lido()))
                .toList();
        return new Lista(avisos.naoLidas(usuarioId), itens);
    }

    @Transactional
    void marcarLidas(UUID usuarioId) {
        avisos.marcarLidas(usuarioId, clock.instant());
    }
}
