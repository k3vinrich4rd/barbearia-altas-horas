package io.github.k3vinrich4rd.altashoras.pagamento;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.pagamento.cobranca.TransicoesDeCobranca;

/**
 * API pública do pagamento para o painel (ADR 0002, E1): as pendências de devolução, a devolução registrada à mão
 * e os eventos de webhook que esgotaram as tentativas. O admin nunca lê a tabela da cobrança. No beta o estorno é
 * feito fora do app (no Pix de volta do Eduardo): o sistema só registra (E13).
 */
@Service
public class PendenciasDePagamento {

    public static final int TAMANHO_MAXIMO = 50;

    private static final String PENDENTES = "'PAGA_APOS_EXPIRACAO', 'DEVOLUCAO_PENDENTE'";
    private static final String STATUS_DA_COBRANCA = "PENDENTE, PAGA, PAGA_APOS_EXPIRACAO, CANCELADA, FALHOU, "
            + "DEVOLUCAO_PENDENTE ou DEVOLVIDA";

    /** Uma cobrança para o painel: sem Pix, sem cartão, sem CPF. O toString só mostra o id e o status (R30). */
    public record Cobranca(UUID id, UUID reservaId, String status, String metodo, long valorCentavos,
            Instant criadoEm, @Nullable Instant devolvidaEm) {

        @Override
        public String toString() {
            return "Cobranca[" + id + ", " + status + "]";
        }
    }

    /** Evento do gateway que ficou para revisão. Sem payload e sem dado pessoal (E14); {@code ultimoErro} é só o code. */
    public record Evento(UUID id, String tipo, Instant recebidoEm, int tentativas, @Nullable String ultimoErro,
            @Nullable String idCobrancaProvedor, String resultado, @Nullable Instant processadoEm) {
    }

    public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total) {
    }

    private static final RowMapper<Cobranca> COBRANCA = (rs, n) -> new Cobranca(rs.getObject("id", UUID.class),
            rs.getObject("reserva_id", UUID.class), rs.getString("status"), rs.getString("metodo"),
            rs.getLong("valor_centavos"), instante(rs.getObject("criado_em", OffsetDateTime.class)),
            instante(rs.getObject("devolvida_em", OffsetDateTime.class)));

    private static final RowMapper<Evento> EVENTO = (rs, n) -> new Evento(rs.getObject("id", UUID.class),
            rs.getString("tipo"), instante(rs.getObject("recebido_em", OffsetDateTime.class)), rs.getInt("tentativas"),
            rs.getString("ultimo_erro"), rs.getString("id_cobranca_provedor"), rs.getString("resultado"),
            instante(rs.getObject("processado_em", OffsetDateTime.class)));

    private static final String COLUNAS_DA_COBRANCA = "id, reserva_id, status, metodo, valor_centavos, criado_em, devolvida_em";
    private static final String COLUNAS_DO_EVENTO = "id, tipo, recebido_em, tentativas, ultimo_erro, id_cobranca_provedor, "
            + "resultado, processado_em";

    private final JdbcClient jdbc;
    private final Clock clock;
    private final TransicoesDeCobranca transicoes;

    PendenciasDePagamento(JdbcClient jdbc, Clock clock, TransicoesDeCobranca transicoes) {
        this.transicoes = transicoes;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Sem {@code status}: as pendências (pagamento tardio sem horário e cancelamento pago), as mais antigas primeiro,
     * pelo índice parcial da V1 (E12). Com {@code status}: aquele status, as mais recentes primeiro. Duas consultas
     * (a página e a contagem), qualquer que seja o tamanho.
     */
    @Transactional(readOnly = true)
    public Pagina<Cobranca> listar(@Nullable String status, int pagina, int tamanho) {
        validarPagina(pagina, tamanho);
        if (status != null && !status.isBlank() && !statusValido(status)) {
            throw new ExcecaoDeNegocio(CodigoErro.VALIDACAO_INVALIDA, "status deve ser " + STATUS_DA_COBRANCA + ".");
        }
        boolean filtrado = status != null && !status.isBlank();
        String filtro = filtrado ? "status = :status" : "status IN (" + PENDENTES + ")";
        String ordem = filtrado ? "criado_em DESC, id" : "criado_em, id";

        JdbcClient.StatementSpec itens = jdbc.sql("SELECT " + COLUNAS_DA_COBRANCA + " FROM cobranca WHERE " + filtro
                + " ORDER BY " + ordem + " LIMIT :limite OFFSET :deslocamento")
                .param("limite", tamanho).param("deslocamento", (long) pagina * tamanho);
        JdbcClient.StatementSpec contagem = jdbc.sql("SELECT count(*) FROM cobranca WHERE " + filtro);
        if (filtrado) {
            itens = itens.param("status", status);
            contagem = contagem.param("status", status);
        }
        return new Pagina<>(itens.query(COBRANCA).list(), pagina, tamanho, contagem.query(Long.class).single());
    }

    /**
     * Registra a devolução feita fora do app: {@code DEVOLUCAO_PENDENTE} ou {@code PAGA_APOS_EXPIRACAO} vira
     * {@code DEVOLVIDA} num único UPDATE com o status de origem no WHERE (E13). Dois cliques ao mesmo tempo: um
     * atualiza, o outro encontra zero linhas, e a data original fica como está. Roda na transação de quem chama.
     */
    @Transactional
    public Cobranca devolver(UUID id, UUID por) {
        if (transicoes.devolver(id, por, clock.instant()) == 0) {
            throw existe("cobranca", id)
                    ? new ExcecaoDeNegocio(CodigoErro.TRANSICAO_INVALIDA,
                            "Só uma cobrança com devolução pendente ou paga depois de expirar pode ser devolvida.")
                    : new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Cobrança não encontrada.");
        }
        return jdbc.sql("SELECT " + COLUNAS_DA_COBRANCA + " FROM cobranca WHERE id = :id").param("id", id)
                .query(COBRANCA).single();
    }

    /** Eventos em REVISAO_MANUAL (H20), os mais antigos primeiro. */
    @Transactional(readOnly = true)
    public Pagina<Evento> eventosEmRevisao(int pagina, int tamanho) {
        validarPagina(pagina, tamanho);
        List<Evento> itens = jdbc.sql("SELECT " + COLUNAS_DO_EVENTO + " FROM evento_webhook "
                + "WHERE resultado = 'REVISAO_MANUAL' ORDER BY recebido_em, id LIMIT :limite OFFSET :deslocamento")
                .param("limite", tamanho).param("deslocamento", (long) pagina * tamanho).query(EVENTO).list();
        long total = jdbc.sql("SELECT count(*) FROM evento_webhook WHERE resultado = 'REVISAO_MANUAL'")
                .query(Long.class).single();
        return new Pagina<>(itens, pagina, tamanho, total);
    }

    /**
     * "Marcar revisado" tira o evento da fila sem reprocessar (E14, D-5): o Eduardo conferiu o gateway e acertou à
     * mão. Já revisado (ou em qualquer outro resultado) é 422.
     */
    @Transactional
    public Evento revisar(UUID id) {
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return jdbc.sql("""
                UPDATE evento_webhook SET resultado = 'REVISADO', processado_em = :agora
                WHERE id = :id AND resultado = 'REVISAO_MANUAL'
                RETURNING %s
                """.formatted(COLUNAS_DO_EVENTO))
                .param("agora", agora).param("id", id)
                .query(EVENTO).optional()
                .orElseThrow(() -> existe("evento_webhook", id)
                        ? new ExcecaoDeNegocio(CodigoErro.TRANSICAO_INVALIDA, "Este evento não está esperando revisão.")
                        : new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Evento não encontrado."));
    }

    /**
     * O status da cobrança de cada reserva, em lote (E6): a "viva" (PENDENTE ou PAGA) se houver, senão a mais
     * recente. Reserva sem cobrança não aparece no mapa.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> statusPorReserva(Collection<UUID> reservaIds) {
        if (reservaIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> status = new HashMap<>();
        jdbc.sql("""
                SELECT DISTINCT ON (reserva_id) reserva_id, status FROM cobranca
                WHERE reserva_id IN (:ids)
                ORDER BY reserva_id, (status IN ('PENDENTE', 'PAGA')) DESC, criado_em DESC
                """)
                .param("ids", reservaIds)
                .query((rs, n) -> {
                    status.put(rs.getObject("reserva_id", UUID.class), rs.getString("status"));
                    return rs.getString("status");
                }).list();
        return status;
    }

    private boolean existe(String tabela, UUID id) {
        // O nome da tabela vem só dos dois chamadores acima, nunca do pedido.
        return jdbc.sql("SELECT count(*) FROM " + tabela + " WHERE id = :id").param("id", id)
                .query(Long.class).single() > 0;
    }

    private static boolean statusValido(String status) {
        return List.of("PENDENTE", "PAGA", "PAGA_APOS_EXPIRACAO", "CANCELADA", "FALHOU", "DEVOLUCAO_PENDENTE", "DEVOLVIDA")
                .contains(status);
    }

    private static void validarPagina(int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new ExcecaoDeNegocio(CodigoErro.VALIDACAO_INVALIDA,
                    "pagina deve ser >= 0 e tamanho de 1 a " + TAMANHO_MAXIMO + ".");
        }
    }

    private static @Nullable Instant instante(@Nullable OffsetDateTime valor) {
        return valor == null ? null : valor.toInstant();
    }
}
