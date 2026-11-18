package io.github.k3vinrich4rd.altashoras.agenda.titular;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.privacidade.FonteDeDadosDoTitular;

/**
 * A agenda entregando as reservas DO titular para a exportação (E14): só as em que a conta é o cliente. Id, início, fim,
 * status, nome do serviço, preço e motivo do cancelamento. Nada de outra pessoa: sem barbeiro, sem visitante, sem cobrança.
 * Uma consulta só, com JOIN em serviço (sem N+1).
 */
@Component
class DadosDeAgendamentosDoTitular implements FonteDeDadosDoTitular {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final JdbcClient jdbc;
    private final ZoneId fuso;

    DadosDeAgendamentosDoTitular(JdbcClient jdbc, @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.jdbc = jdbc;
        this.fuso = ZoneId.of(fuso);
    }

    @Override
    public String chave() {
        return "agendamentos";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportar(UUID usuarioId) {
        List<Map<String, Object>> reservas = jdbc.sql("""
                SELECT r.id, r.inicio, r.fim, r.status, s.nome AS servico, r.preco_centavos, r.motivo_cancelamento
                FROM reserva r JOIN servico s ON s.id = r.servico_id
                WHERE r.cliente_id = :cliente
                ORDER BY r.inicio, r.id
                """)
                .param("cliente", usuarioId)
                .query((rs, n) -> {
                    Map<String, Object> reserva = new LinkedHashMap<>();
                    reserva.put("id", rs.getObject("id", UUID.class));
                    reserva.put("inicio", FORMATO.format(rs.getObject("inicio", java.time.OffsetDateTime.class).atZoneSameInstant(fuso)));
                    reserva.put("fim", FORMATO.format(rs.getObject("fim", java.time.OffsetDateTime.class).atZoneSameInstant(fuso)));
                    reserva.put("status", rs.getString("status"));
                    reserva.put("servico", rs.getString("servico"));
                    reserva.put("precoCentavos", rs.getLong("preco_centavos"));
                    reserva.put("motivoCancelamento", rs.getString("motivo_cancelamento"));
                    return reserva;
                })
                .list();
        return Map.of("agendamentos", reservas);
    }
}
