package io.github.k3vinrich4rd.altashoras.agenda;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.agenda.reserva.Reserva;
import io.github.k3vinrich4rd.altashoras.agenda.reserva.StatusReserva;
import io.github.k3vinrich4rd.altashoras.agenda.reserva.TokenGuest;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * API pública da agenda para o módulo de pagamento (ADR 0002): ler os dados para cobrar, gravar o aceite da política
 * e confirmar a reserva depois do pagamento. O pagamento nunca toca a tabela nem as classes internas da reserva.
 */
@Service
public class Reservas {

    /** SQLSTATE do PostgreSQL para violação de EXCLUDE: o horário já é de outra reserva. */
    private static final String EXCLUSAO_VIOLADA = "23P01";
    private static final String HASH_INEXISTENTE = "0".repeat(64);

    /** Quem está pedindo: o cliente logado (id do token) ou o visitante (token da reserva). */
    public record Dono(@Nullable UUID clienteId, @Nullable String tokenGuest) {

        public static Dono cliente(UUID clienteId) {
            return new Dono(clienteId, null);
        }

        public static Dono visitante(String tokenGuest) {
            return new Dono(null, tokenGuest);
        }

        @Override
        public String toString() {
            return "Dono[" + (clienteId != null ? clienteId : "visitante, token omitido") + "]";
        }
    }

    /** O que a cobrança precisa saber da reserva. {@code nomePagador} vai só ao gateway; nunca para log. */
    public record DadosParaCobranca(UUID id, boolean aguardandoPagamento, long precoCentavos, @Nullable Instant expiraEm,
            String nomePagador, boolean vencida) {

        @Override
        public String toString() {
            return "DadosParaCobranca[" + id + ", aguardandoPagamento=" + aguardandoPagamento + "]";
        }
    }

    private final JdbcClient jdbc;
    private final EntityManager em;
    private final TransactionTemplate transacao;
    private final Clock clock;

    Reservas(JdbcClient jdbc, EntityManager em, PlatformTransactionManager gerenciador, Clock clock) {
        this.jdbc = jdbc;
        this.em = em;
        this.transacao = new TransactionTemplate(gerenciador);
        this.clock = clock;
    }

    /**
     * Reserva inexistente, de outro dono ou com token errado respondem igual: 404 (R23). Para o cliente logado o
     * nome do pagador vem de {@code usuario.nome}: é só leitura do nome; a Fatia 6 pode trocar por uma API da conta.
     */
    @Transactional(readOnly = true)
    public DadosParaCobranca paraCobranca(UUID reservaId, Dono dono) {
        Linha linha = jdbc.sql("""
                SELECT r.status, r.preco_centavos, r.expira_em, r.cliente_id, g.nome AS nome_guest, g.token_hash
                FROM reserva r LEFT JOIN reserva_guest g ON g.reserva_id = r.id
                WHERE r.id = :id
                """)
                .param("id", reservaId)
                .query((rs, n) -> new Linha(StatusReserva.valueOf(rs.getString("status")), rs.getLong("preco_centavos"),
                        rs.getObject("expira_em", OffsetDateTime.class), rs.getObject("cliente_id", UUID.class),
                        rs.getString("nome_guest"), rs.getString("token_hash")))
                .optional().orElseThrow(Reservas::naoEncontrada);

        String nome;
        if (dono.clienteId() != null) {
            if (!dono.clienteId().equals(linha.clienteId())) {
                throw naoEncontrada();
            }
            nome = jdbc.sql("SELECT nome FROM usuario WHERE id = :id").param("id", dono.clienteId())
                    .query(String.class).optional().orElseThrow(Reservas::naoEncontrada);
        } else {
            // Compara sempre, mesmo sem visitante: o tempo de resposta não separa "id inexistente" de "token errado".
            String hash = linha.tokenHash() == null ? HASH_INEXISTENTE : linha.tokenHash();
            if (!TokenGuest.confere(dono.tokenGuest(), hash) || linha.tokenHash() == null) {
                throw naoEncontrada();
            }
            nome = linha.nomeGuest();
        }

        Instant expiraEm = linha.expiraEm() == null ? null : linha.expiraEm().toInstant();
        boolean vencida = linha.status() == StatusReserva.EXPIRADO || (linha.status() == StatusReserva.AGUARDANDO_PAGAMENTO
                && expiraEm != null && !expiraEm.isAfter(clock.instant()));
        return new DadosParaCobranca(reservaId, linha.status() == StatusReserva.AGUARDANDO_PAGAMENTO, linha.precoCentavos(), expiraEm, nome, vencida);
    }

    private record Linha(StatusReserva status, long precoCentavos, @Nullable OffsetDateTime expiraEm,
            @Nullable UUID clienteId, @Nullable String nomeGuest, @Nullable String tokenHash) {
    }

    /** Prova do aceite da política (R18): versão e data na própria reserva. Entra na transação de quem chama. */
    @Transactional
    public void registrarAceite(UUID reservaId, int versao, Instant agora) {
        int linhas = jdbc.sql("""
                UPDATE reserva SET aceite_politica_versao = :versao, aceite_politica_em = :agora,
                                   atualizado_em = :agora, version = version + 1
                WHERE id = :id
                """)
                .param("versao", versao).param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
                .param("id", reservaId).update();
        if (linhas != 1) {
            throw naoEncontrada();
        }
    }

    /**
     * Pagamento confirmado (R13, R14), numa transação própria: AGUARDANDO_PAGAMENTO ou EXPIRADO vira CONFIRMADO
     * pela máquina de estados; já CONFIRMADO é a mesma resposta (idempotente). Se a reserva expirou e o horário já
     * é de outra pessoa, a constraint recusa (23P01), a transação desfaz e a reserva continua EXPIRADO.
     */
    public ResultadoConfirmacao confirmarPagamento(UUID reservaId) {
        try {
            return transacao.execute(status -> {
                Reserva reserva = em.find(Reserva.class, reservaId, LockModeType.PESSIMISTIC_WRITE);
                if (reserva == null) {
                    throw naoEncontrada();
                }
                if (reserva.getStatus() == StatusReserva.CONFIRMADO) {
                    return ResultadoConfirmacao.CONFIRMADA;
                }
                reserva.mudarPara(StatusReserva.CONFIRMADO, clock.instant());
                em.flush();
                return ResultadoConfirmacao.CONFIRMADA;
            });
        } catch (ExcecaoDeNegocio regra) {
            throw regra;
        } catch (RuntimeException falha) {
            if (EXCLUSAO_VIOLADA.equals(sqlState(falha))) {
                return ResultadoConfirmacao.HORARIO_OCUPADO;
            }
            throw falha;
        }
    }

    private static ExcecaoDeNegocio naoEncontrada() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Reserva não encontrada.");
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
