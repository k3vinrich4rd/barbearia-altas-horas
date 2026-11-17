package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import io.github.k3vinrich4rd.altashoras.pagamento.gateway.Provedor;

/**
 * Registrar e processar um aviso de pagamento (R11, R12, E20). O evento é gravado ANTES de processar, com
 * {@code ON CONFLICT DO NOTHING}: o gateway entrega "pelo menos uma vez", e o id do evento é único. Evento já
 * processado não faz nada (nem reconsulta o gateway); evento gravado e não processado (a API caiu no meio) é
 * processado de novo. A mesma classe serve o webhook, a simulação (E28) e o job de reprocessamento (E26).
 * Nada de payload no banco nem no log: só o id do evento, o tipo e o id da cobrança no provedor (R30).
 */
@Service
class ReceberWebhook {

    /** Os únicos tipos que mexem em pagamento; qualquer outro é gravado como IGNORADO. */
    static final Set<String> EVENTOS_DE_PAGAMENTO = Set.of("PAYMENT_RECEIVED", "PAYMENT_CONFIRMED");

    /** Resultado gravado em evento_webhook quando a cobrança ainda não existe: fica para o job tentar de novo. */
    static final String SEM_COBRANCA = "SEM_COBRANCA";

    record Evento(UUID id, String tipo, @Nullable String idCobranca, boolean processado) {
    }

    private final JdbcClient jdbc;
    private final ProcessarPagamento processarPagamento;
    private final Clock clock;

    ReceberWebhook(JdbcClient jdbc, ProcessarPagamento processarPagamento, Clock clock) {
        this.jdbc = jdbc;
        this.processarPagamento = processarPagamento;
        this.clock = clock;
    }

    /** Sem transação: o evento fica gravado mesmo que o processamento falhe, e o reenvio o processa (A37). */
    void receber(Provedor provedor, String idEvento, String tipo, @Nullable String idCobrancaProvedor) {
        jdbc.sql("""
                INSERT INTO evento_webhook (provedor, id_evento_provedor, tipo, id_cobranca_provedor, recebido_em)
                VALUES (:provedor, :idEvento, :tipo, :idCobranca, :agora)
                ON CONFLICT (provedor, id_evento_provedor) DO NOTHING
                """)
                .param("provedor", provedor.name()).param("idEvento", idEvento).param("tipo", tipo)
                .param("idCobranca", idCobrancaProvedor).param("agora", utc(clock))
                .update();
        Evento evento = jdbc.sql("""
                SELECT id, tipo, id_cobranca_provedor, processado_em IS NOT NULL AS processado
                FROM evento_webhook WHERE provedor = :provedor AND id_evento_provedor = :idEvento
                """)
                .param("provedor", provedor.name()).param("idEvento", idEvento)
                .query((rs, n) -> new Evento(rs.getObject("id", UUID.class), rs.getString("tipo"),
                        rs.getString("id_cobranca_provedor"), rs.getBoolean("processado")))
                .single();
        if (!evento.processado()) {
            processar(evento);
        }
    }

    /** Processa um evento gravado e marca o resultado. Exceção do gateway ou do banco sobe: o evento continua pendente. */
    ProcessarPagamento.Resultado processar(Evento evento) {
        ProcessarPagamento.Resultado resultado = ProcessarPagamento.Resultado.IGNORADO;
        if (EVENTOS_DE_PAGAMENTO.contains(evento.tipo()) && evento.idCobranca() != null) {
            resultado = processarPagamento.porIdProvedor(evento.idCobranca());
        }
        if (resultado == ProcessarPagamento.Resultado.SEM_COBRANCA) {
            jdbc.sql("UPDATE evento_webhook SET resultado = :resultado WHERE id = :id")
                    .param("resultado", SEM_COBRANCA).param("id", evento.id()).update();
            return resultado;
        }
        jdbc.sql("UPDATE evento_webhook SET processado_em = :agora, resultado = :resultado WHERE id = :id")
                .param("agora", utc(clock)).param("resultado", resultado.name()).param("id", evento.id()).update();
        return resultado;
    }

    private static OffsetDateTime utc(Clock clock) {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
