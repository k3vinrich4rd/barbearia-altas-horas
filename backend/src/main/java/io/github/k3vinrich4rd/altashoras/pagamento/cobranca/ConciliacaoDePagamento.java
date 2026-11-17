package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.agenda.ConciliadorDePagamento;

/**
 * Se o webhook atrasou (a API hibernou, a rede falhou), a leitura da reserva reconsulta o gateway e aplica o mesmo
 * fluxo do webhook (E25). No máximo uma reconsulta a cada 10 s por cobrança: a tela do cliente faz polling e não pode
 * virar uma chuva de chamadas ao gateway. O mapa fica em memória: numa reinicialização a primeira leitura reconsulta.
 */
@Component
class ConciliacaoDePagamento implements ConciliadorDePagamento {

    private static final Logger LOG = LoggerFactory.getLogger(ConciliacaoDePagamento.class);
    static final Duration INTERVALO = Duration.ofSeconds(10);
    private static final int LIMITE_DO_MAPA = 1000;

    private final CobrancaRepository cobrancas;
    private final ProcessarPagamento processar;
    private final Clock clock;
    private final Map<UUID, Instant> ultimaConsulta = new ConcurrentHashMap<>();

    ConciliacaoDePagamento(CobrancaRepository cobrancas, ProcessarPagamento processar, Clock clock) {
        this.cobrancas = cobrancas;
        this.processar = processar;
        this.clock = clock;
    }

    @Override
    public void conciliarReserva(UUID reservaId) {
        try {
            cobrancas.findFirstByReservaIdAndStatusAndIdCobrancaProvedorIsNotNull(reservaId, StatusCobranca.PENDENTE)
                    .ifPresent(this::reconsultar);
        } catch (RuntimeException falha) {
            // A leitura da reserva não pode quebrar por causa do gateway; só o tipo do erro vai ao log (R30).
            LOG.warn("Conciliação do pagamento falhou ({})", falha.getClass().getSimpleName());
        }
    }

    private void reconsultar(Cobranca cobranca) {
        Instant agora = clock.instant();
        AtomicBoolean podeConsultar = new AtomicBoolean();
        ultimaConsulta.compute(cobranca.getId(), (id, anterior) -> {
            if (anterior != null && agora.isBefore(anterior.plus(INTERVALO))) {
                return anterior;
            }
            podeConsultar.set(true);
            return agora;
        });
        if (ultimaConsulta.size() > LIMITE_DO_MAPA) {
            ultimaConsulta.values().removeIf(quando -> quando.plus(INTERVALO).isBefore(agora));
        }
        if (podeConsultar.get()) {
            processar.porIdProvedor(cobranca.getIdCobrancaProvedor());
        }
    }
}
