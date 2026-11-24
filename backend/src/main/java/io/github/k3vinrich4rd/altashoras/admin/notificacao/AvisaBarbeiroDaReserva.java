package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.AvisoParaEnvio;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.DestinoDoAviso.Aparelho;
import io.github.k3vinrich4rd.altashoras.agenda.ReservaConfirmada;
import io.github.k3vinrich4rd.altashoras.agenda.Reservas;
import io.github.k3vinrich4rd.altashoras.agenda.Reservas.DadosDoAviso;
import io.github.k3vinrich4rd.altashoras.conta.Contas;

/**
 * O painel ouvindo a agenda por evento (ADR 0002, E4 a E6): o pagamento confirmou a reserva, então o barbeiro dela é avisado.
 * Só o barbeiro da reserva: barbeiro desligado, inativo ou sem conta ligada encerra o evento sem aviso e sem erro (A10).
 * <p>
 * Em três passos, porque o banco tem só 3 conexões (E5, A14): (1) transação curta: acha a conta, grava o aviso
 * (idempotente) e lê os aparelhos; (2) FORA de transação: chama os canais ligados; (3) transação curta: marca a entrega e
 * apaga as inscrições mortas. Falha passageira em todos os destinos faz o método lançar, o evento fica pendente no outbox e
 * a republicação tenta de novo; o aviso do painel já está gravado e a reserva continua CONFIRMADO (A12).
 */
@Component
class AvisaBarbeiroDaReserva {

    private static final Logger LOG = LoggerFactory.getLogger(AvisaBarbeiroDaReserva.class);

    /** O que o passo 1 achou para entregar. */
    private record Preparo(UUID usuarioId, UUID avisoId, AvisoParaEnvio aviso, List<Aparelho> aparelhos) {
    }

    private final ConcurrentHashMap<UUID, ReentrantLock> travas = new ConcurrentHashMap<>();
    private final Reservas reservas;
    private final Contas contas;
    private final Avisos avisos;
    private final Inscricoes inscricoes;
    private final Despachante despachante;
    private final Clock clock;
    private final ZoneId fuso;
    private final TransactionTemplate curta;
    private final TransactionTemplate semTransacao;

    AvisaBarbeiroDaReserva(Reservas reservas, Contas contas, Avisos avisos, Inscricoes inscricoes, Despachante despachante,
            PlatformTransactionManager gerenciador, Clock clock, @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.reservas = reservas;
        this.contas = contas;
        this.avisos = avisos;
        this.inscricoes = inscricoes;
        this.despachante = despachante;
        this.clock = clock;
        this.fuso = ZoneId.of(fuso);
        this.curta = new TransactionTemplate(gerenciador);
        this.curta.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.semTransacao = new TransactionTemplate(gerenciador);
        this.semTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @ApplicationModuleListener
    void aoConfirmar(ReservaConfirmada evento) {
        // Duas entregas do mesmo evento ao mesmo tempo (reentrega do outbox) passam uma de cada vez: a segunda já encontra o
        // aviso entregue (A9). Entre instâncias da API o push repetido seria juntado pelo aparelho, pela tag (E10).
        ReentrantLock trava = travas.computeIfAbsent(evento.reservaId(), id -> new ReentrantLock());
        trava.lock();
        try {
            entregar(evento);
        } finally {
            trava.unlock();
            travas.remove(evento.reservaId(), trava);
        }
    }

    private void entregar(ReservaConfirmada evento) {
        Preparo preparo = curta.execute(status -> preparar(evento));
        if (preparo == null) {
            return;
        }
        Despachante.Relatorio relatorio = semTransacao.execute(status -> despachante.entregar(preparo.aviso(),
                preparo.usuarioId(), preparo.aparelhos(), () -> emailDe(preparo.usuarioId())));
        if (relatorio == null) {
            return;
        }
        Instant agora = clock.instant();
        curta.executeWithoutResult(status -> {
            relatorio.invalidos().forEach(aparelho -> inscricoes.removerMorta(aparelho.endpoint()));
            relatorio.enviados().forEach(aparelho -> inscricoes.marcarEnvioOk(aparelho.endpoint(), agora));
            if (!relatorio.pendente()) {
                avisos.marcarEntregue(preparo.avisoId(), agora);
            }
        });
        if (relatorio.pendente()) {
            // Sem dado nenhum na mensagem: a republicação do outbox tenta de novo (E6).
            throw new IllegalStateException("entrega do aviso pendente: falha passageira em todos os destinos");
        }
    }

    private @Nullable Preparo preparar(ReservaConfirmada evento) {
        UUID usuarioId = reservas.contaDoBarbeiroAtivo(evento.barbeiroId()).orElse(null);
        if (usuarioId == null) {
            LOG.debug("reserva {} sem barbeiro ativo com conta: sem aviso", evento.reservaId());
            return null;
        }
        DadosDoAviso dados = reservas.paraAviso(evento.reservaId()).orElse(null);
        if (dados == null) {
            return null;
        }
        Avisos.Gravado gravado = avisos.gravar(usuarioId, evento.reservaId(), dados.servico(), dados.inicio(), clock.instant());
        if (gravado.jaEntregue()) {
            return null;
        }
        AvisoParaEnvio aviso = TextoDoAviso.doNovoHorario(dados.servico(), dados.inicio(), evento.reservaId(), fuso);
        return new Preparo(usuarioId, gravado.id(), aviso, inscricoes.daConta(usuarioId));
    }

    /** O e-mail da conta só é lido quando algum canal de e-mail está ligado (13D); fora de transação o Spring abre a dele. */
    private @Nullable String emailDe(UUID usuarioId) {
        return despachante.algumPorEmail() ? contas.emailDaConta(usuarioId).orElse(null) : null;
    }
}
