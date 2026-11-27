package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.agenda.PrazoDaReservaProrrogado;
import io.github.k3vinrich4rd.altashoras.agenda.ProrrogacaoDoPagamento;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * "Mais tempo para pagar" no Pix (Fatia 14A, D188; Fatia 15, ADR 0015, E20 a E22, revoga a D190). Cada pedido soma um
 * tempo fixo ({@code app.agenda.prorrogacao-pagamento}, 120 s) ao prazo que está correndo, só no último minuto do prazo
 * e até {@code app.agenda.prorrogacao-maximo} (10) pedidos por reserva. A {@code X-Idempotency-Key} opcional do último
 * pedido faz a repetição devolver 200 sem somar nem contar (E21).
 * <p>
 * Concorrência (E4): tudo numa transação que começa com {@code SELECT ... FOR UPDATE} na linha da reserva e só então lê o
 * relógio e decide. O job de expiração pega só {@code expira_em <= agora}, com {@code SKIP LOCKED}, então pula a linha que
 * esta transação segura; a expiração preguiçosa de outro cliente e o webhook do pagamento esperam o commit e enxergam o
 * prazo novo. Duas prorrogações juntas sem chave: a segunda espera o lock e, com o prazo já acima de 60 s, cai em
 * 422 PRORROGACAO_FORA_DA_JANELA; com a mesma chave, em {@link Desfecho#JA_PRORROGADA}. O dono é conferido
 * antes de tudo, sem trava (E1), e a transação é aberta à mão para o deadlock poder ser refeito numa transação nova.
 * <p>
 * Ordem das checagens (E22, fixa para o teste ser determinístico e nada vazar sobre reserva alheia, R23): 404 do dono, 422
 * TRANSICAO_INVALIDA (status final), repetição com a mesma chave (200), 422 RESERVA_EXPIRADA, 422 PRORROGACAO_ESGOTADA,
 * 422 PRORROGACAO_FORA_DA_JANELA e, por fim, grava.
 */
@Service
class ProrrogarPagamento {

    /** O que aconteceu: a reserva ganhou o tempo agora (201) ou era a repetição do último pedido e nada mudou (200, E21). */
    enum Desfecho { PRORROGADA, JA_PRORROGADA }

    private static final Logger LOG = LoggerFactory.getLogger(ProrrogarPagamento.class);
    private static final String DEADLOCK = "40P01";
    private static final String HASH_INEXISTENTE = "0".repeat(64);

    private final ReservaRepository reservas;
    private final ReservaGuestRepository visitantes;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final TransactionTemplate transacao;
    private final Duration extra;
    private final int maximo;

    ProrrogarPagamento(ReservaRepository reservas, ReservaGuestRepository visitantes, JdbcClient jdbc,
            ApplicationEventPublisher eventos, Clock clock, PlatformTransactionManager gerenciador,
            @Value("${app.agenda.prorrogacao-pagamento:PT2M}") Duration extra,
            @Value("${app.agenda.prorrogacao-maximo:10}") int maximo) {
        this.reservas = reservas;
        this.visitantes = visitantes;
        this.jdbc = jdbc;
        this.eventos = eventos;
        this.clock = clock;
        this.transacao = new TransactionTemplate(gerenciador);
        this.extra = extra;
        this.maximo = maximo;
    }

    /** Cliente logado: o dono vem do token (claim sub). Reserva alheia, inexistente ou manual é 404. */
    Desfecho doCliente(UUID clienteId, UUID reservaId, @Nullable UUID chave) {
        if (reservas.doCliente(clienteId, reservaId).isEmpty()) {
            throw naoEncontrada();
        }
        return prorrogar(reservaId, chave);
    }

    /** Visitante: o X-Guest-Token da reserva. Token errado, ausente ou de outra reserva é o mesmo 404 (R23). */
    Desfecho doVisitante(UUID reservaId, @Nullable String token, @Nullable UUID chave) {
        // Compara sempre, mesmo sem visitante: o tempo de resposta não separa "id inexistente" de "token errado".
        String hashGuardado = visitantes.findById(reservaId).map(ReservaGuest::getTokenHash).orElse(HASH_INEXISTENTE);
        if (!TokenGuest.confere(token, hashGuardado) || HASH_INEXISTENTE.equals(hashGuardado)
                || reservas.doVisitante(reservaId).isEmpty()) {
            throw naoEncontrada();
        }
        return prorrogar(reservaId, chave);
    }

    private Desfecho prorrogar(UUID reservaId, @Nullable UUID chave) {
        for (int tentativa = 1; ; tentativa++) {
            try {
                return transacao.execute(status -> decidirEGravar(reservaId, chave));
            } catch (ExcecaoDeNegocio regra) {
                throw regra;
            } catch (RuntimeException falha) {
                // Deadlock (SQLSTATE 40P01): o PostgreSQL já desfez a transação que perdeu. Repete uma vez, como o ReservarHorario.
                if (DEADLOCK.equals(sqlState(falha))) {
                    if (tentativa == 1) {
                        continue;
                    }
                    throw new ExcecaoDeNegocio(CodigoErro.CONFLITO_CONCORRENTE);
                }
                throw falha;
            }
        }
    }

    private record Linha(String status, @Nullable Instant expiraEm, int prorrogacoes, @Nullable UUID chave) {
    }

    private Desfecho decidirEGravar(UUID reservaId, @Nullable UUID chave) {
        Linha linha = jdbc.sql("""
                        SELECT status, expira_em, prorrogacoes, prorrogacao_chave FROM reserva WHERE id = :id FOR UPDATE
                        """)
                .param("id", reservaId)
                .query((rs, n) -> new Linha(rs.getString("status"), instante(rs.getObject("expira_em", OffsetDateTime.class)),
                        rs.getInt("prorrogacoes"), rs.getObject("prorrogacao_chave", UUID.class)))
                .optional().orElseThrow(ProrrogarPagamento::naoEncontrada);
        // O relógio só é lido DEPOIS da trava: quem esperou o lock decide com o instante em que acordou.
        Instant agora = clock.instant();
        boolean aguardando = "AGUARDANDO_PAGAMENTO".equals(linha.status());
        if (!aguardando && !"EXPIRADO".equals(linha.status())) {
            throw new ExcecaoDeNegocio(CodigoErro.TRANSICAO_INVALIDA,
                    "Só uma reserva aguardando pagamento ganha mais tempo.");
        }
        Instant expiraEm = linha.expiraEm();
        boolean noPrazo = aguardando && expiraEm != null && expiraEm.isAfter(agora);
        // E21: a mesma chave do último pedido (e o prazo vivo) é repetição: o mesmo corpo, sem somar nem contar.
        if (noPrazo && chave != null && chave.equals(linha.chave())) {
            return Desfecho.JA_PRORROGADA;
        }
        if (!noPrazo) {
            // Sem carência e sem reabertura (E5): o prazo acabou, mesmo que o job ainda não tenha passado a reserva a EXPIRADO.
            throw new ExcecaoDeNegocio(CodigoErro.RESERVA_EXPIRADA);
        }
        if (linha.prorrogacoes() >= maximo) {
            throw new ExcecaoDeNegocio(CodigoErro.PRORROGACAO_ESGOTADA);
        }
        if (agora.isBefore(expiraEm.minus(ProrrogacaoDoPagamento.JANELA))) {
            throw new ExcecaoDeNegocio(CodigoErro.PRORROGACAO_FORA_DA_JANELA);
        }

        Instant novoPrazo = expiraEm.plus(extra);
        int linhas = jdbc.sql("""
                UPDATE reserva SET expira_em = :novo, prorrogada_em = :agora, prorrogacoes = prorrogacoes + 1,
                                   prorrogacao_chave = :chave, atualizado_em = :agora, version = version + 1
                WHERE id = :id AND prorrogacoes = :usadas AND status = 'AGUARDANDO_PAGAMENTO' AND expira_em > :agora
                """)
                .param("novo", utc(novoPrazo)).param("agora", utc(agora)).param("chave", chave)
                .param("usadas", linha.prorrogacoes()).param("id", reservaId).update();
        if (linhas != 1) {
            // A linha está travada por nós: só um UPDATE que não casa a condição chegaria aqui, e isso é bug, não regra.
            throw new IllegalStateException("A prorrogação não gravou a reserva " + reservaId);
        }
        eventos.publishEvent(new PrazoDaReservaProrrogado(reservaId, expiraEm, novoPrazo));
        // Rastro técnico sem dado pessoal (E11, R30): só o id da reserva.
        LOG.info("reserva.prorrogada id={}", reservaId);
        return Desfecho.PRORROGADA;
    }

    private static @Nullable Instant instante(@Nullable OffsetDateTime quando) {
        return quando == null ? null : quando.toInstant();
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static ExcecaoDeNegocio naoEncontrada() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Agendamento não encontrado.");
    }

    private static @Nullable String sqlState(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
