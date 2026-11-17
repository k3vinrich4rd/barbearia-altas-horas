package io.github.k3vinrich4rd.altashoras.banco;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Monta as linhas mínimas para testar o banco direto (sem regra de aplicação, que chega nas próximas fatias).
 * Cada teste cria o próprio barbeiro: os testes compartilham o mesmo banco e não pisam uns nos outros.
 */
final class ReservaSql {

    static final String INSERIR = """
            INSERT INTO reserva (id, barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status,
                                 preco_centavos, expira_em, aceite_politica_versao, aceite_politica_em)
            VALUES (?, ?, ?, 'GUEST', ?, ?, ?, ?, 5500, ?, ?, ?)
            """;

    private ReservaSql() {
    }

    static UUID novoBarbeiro(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO barbeiro (id, nome_exibicao) VALUES (?, ?)", id, "Barbeiro Teste " + id);
        return id;
    }

    static UUID novoServico(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO servico (id, nome, duracao_min, preco_centavos) VALUES (?, ?, 30, 5500)",
                id, "Serviço Teste " + id);
        return id;
    }

    /** Reserva de 30 min em {@code status}; CONFIRMADO já com o aceite da política (reserva_aceite_ck). */
    static Object[] parametros(UUID barbeiro, UUID servico, OffsetDateTime inicio, String status) {
        OffsetDateTime fim = inicio.plusMinutes(30);
        boolean aguardando = "AGUARDANDO_PAGAMENTO".equals(status);
        boolean pago = !aguardando && !"EXPIRADO".equals(status) && !"CANCELADO".equals(status);
        return new Object[] {UUID.randomUUID(), barbeiro, servico, inicio, fim, fim, status,
                aguardando ? inicio.minusHours(3) : null,
                pago ? 1 : null,
                pago ? inicio.minusDays(1) : null};
    }
}
