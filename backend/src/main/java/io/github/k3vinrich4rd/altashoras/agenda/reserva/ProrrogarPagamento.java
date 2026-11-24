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
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * "Acrescentar 1 minuto" no Pix (Fatia 14A, D188 a D190). Uma vez por reserva, só no último trecho do prazo, somando um
 * tempo fixo ({@code app.agenda.prorrogacao-pagamento}) ao prazo que está correndo.
 * <p>
 * Concorrência (E4): tudo numa transação que começa com {@code SELECT ... FOR UPDATE} na linha da reserva e só então lê o
 * relógio e decide. O job de expiração pega só {@code expira_em <= agora}, com {@code SKIP LOCKED}, então pula a linha que
 * esta transação segura; a expiração preguiçosa de outro cliente e o webhook do pagamento esperam o commit e enxergam o
 * prazo novo. Duas prorrogações juntas: a segunda espera o lock e cai em {@link Desfecho#JA_PRORROGADA}. O dono é conferido
 * antes de tudo, sem trava (E1), e a transação é aberta à mão para o deadlock poder ser refeito numa transação nova.
 * <p>
 * Ordem das checagens (E8, fixa para o teste ser determinístico e nada vazar sobre reserva alheia, R23): 404 do dono, 422
 * TRANSICAO_INVALIDA (status final), repetição (200), 422 RESERVA_EXPIRADA, 422 PRORROGACAO_FORA_DA_JANELA e, por fim, grava.
 */
@Service
class ProrrogarPagamento {

    /** O que aconteceu: a reserva ganhou o tempo agora (201) ou já o tinha ganhado e nada mudou (200, E3). */
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

    ProrrogarPagamento(ReservaRepository reservas, ReservaGuestRepository visitantes, JdbcClient jdbc,
            ApplicationEventPublisher eventos, Clock clock, PlatformTransactionManager gerenciador,
            @Value("${app.agenda.prorrogacao-pagamento:PT60S}") Duration extra) {
        this.reservas = reservas;
        this.visitantes = visitantes;
        this.jdbc = jdbc;
        this.eventos = eventos;
        this.clock = clock;
        this.transacao = new TransactionTemplate(gerenciador);
        this.extra = extra;
    }

    /** Cliente logado: o dono vem do token (claim sub). Reserva alheia, inexistente ou manual é 404. */
    Desfecho doCliente(UUID clienteId, UUID reservaId) {
        if (reservas.doCliente(clienteId, reservaId).isEmpty()) {
            throw naoEncontrada();
        }
        return prorrogar(reservaId);
    }

    /** Visitante: o X-Guest-Token da reserva. Token errado, ausente ou de outra reserva é o mesmo 404 (R23). */
    Desfecho doVisitante(UUID reservaId, @Nullable String token) {
        // Compara sempre, mesmo sem visitante: o tempo de resposta não separa "id inexistente" de "token errado".
        String hashGuardado = visitantes.findById(reservaId).map(ReservaGuest::getTokenHash).orElse(HASH_INEXISTENTE);
        if (!TokenGuest.confere(token, hashGuardado) || HASH_INEXISTENTE.equals(hashGuardado)
                || reservas.doVisitante(reservaId).isEmpty()) {
            throw naoEncontrada();
        }
        return prorrogar(reservaId);
    }

    private Desfecho prorrogar(UUID reservaId) {
        for (int tentativa = 1; ; tentativa++) {
            try {
                return transacao.execute(status -> decidirEGravar(reservaId));
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

    private record Linha(String status, @Nullable Instant expiraEm, @Nullable Instant prorrogadaEm) {
    }

    private Desfecho decidirEGravar(UUID reservaId) {
        Linha linha = jdbc.sql("SELECT status, expira_em, prorrogada_em FROM reserva WHERE id = :id FOR UPDATE")
                .param("id", reservaId)
                .query((rs, n) -> new Linha(rs.getString("status"), instante(rs.getObject("expira_em", OffsetDateTime.class)),
                        instante(rs.getObject("prorrogada_em", OffsetDateTime.class))))
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
        if (noPrazo && linha.prorrogadaEm() != null) {
            return Desfecho.JA_PRORROGADA;
        }
        if (!noPrazo) {
            // Sem carência e sem reabertura (E5): o prazo acabou, mesmo que o job ainda não tenha passado a reserva a EXPIRADO.
            throw new ExcecaoDeNegocio(CodigoErro.RESERVA_EXPIRADA);
        }
        if (agora.isBefore(expiraEm.minus(extra))) {
            throw new ExcecaoDeNegocio(CodigoErro.PRORROGACAO_FORA_DA_JANELA);
        }

        Instant novoPrazo = expiraEm.plus(extra);
        int linhas = jdbc.sql("""
                UPDATE reserva SET expira_em = :novo, prorrogada_em = :agora, atualizado_em = :agora, version = version + 1
                WHERE id = :id AND prorrogada_em IS NULL AND status = 'AGUARDANDO_PAGAMENTO' AND expira_em > :agora
                """)
                .param("novo", utc(novoPrazo)).param("agora", utc(agora)).param("id", reservaId).update();
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
