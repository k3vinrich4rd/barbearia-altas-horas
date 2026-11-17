package io.github.k3vinrich4rd.altashoras;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** Monta dados direto no banco. Cada teste cria os próprios: os testes dividem o mesmo PostgreSQL. */
public final class DadosDeTeste {

    private DadosDeTeste() {
    }

    /** Barbeiro com a jornada real: terça (2) a sábado (6), 07h-12h e 13h-20h. */
    public static UUID barbeiro(JdbcTemplate jdbc, boolean ativo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO barbeiro (id, nome_exibicao, ativo) VALUES (?, ?, ?)", id, "Barbeiro " + id, ativo);
        for (int dia = 2; dia <= 6; dia++) {
            jdbc.update("INSERT INTO jornada (barbeiro_id, dia_semana, hora_inicio, hora_fim) VALUES (?, ?, ?, ?)",
                    id, dia, LocalTime.of(7, 0), LocalTime.of(12, 0));
            jdbc.update("INSERT INTO jornada (barbeiro_id, dia_semana, hora_inicio, hora_fim) VALUES (?, ?, ?, ?)",
                    id, dia, LocalTime.of(13, 0), LocalTime.of(20, 0));
        }
        return id;
    }

    public static UUID servico(JdbcTemplate jdbc, String nome, int duracaoMin, long precoCentavos, boolean ativo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO servico (id, nome, duracao_min, preco_centavos, ativo) VALUES (?, ?, ?, ?, ?)",
                id, nome + " " + id, duracaoMin, precoCentavos, ativo);
        return id;
    }

    public static UUID servico(JdbcTemplate jdbc, int duracaoMin) {
        return servico(jdbc, "Serviço", duracaoMin, 5500, true);
    }

    /** Instante de um horário de Osasco. */
    public static OffsetDateTime em(LocalDate data, int hora, int minuto) {
        return ZonedDateTime.of(data, LocalTime.of(hora, minuto), RelogioFixoDeTeste.FUSO).toOffsetDateTime();
    }

    /** Reserva de 30 min. {@code expiraEm} só vale para AGUARDANDO_PAGAMENTO; os status pagos levam o aceite. */
    public static void reserva(JdbcTemplate jdbc, UUID barbeiro, UUID servico, OffsetDateTime inicio, String status,
            Instant expiraEm) {
        boolean aguardando = "AGUARDANDO_PAGAMENTO".equals(status);
        boolean pago = "CONFIRMADO".equals(status) || "CONCLUIDO".equals(status) || "NAO_COMPARECEU".equals(status);
        OffsetDateTime fim = inicio.plusMinutes(30);
        jdbc.update("""
                INSERT INTO reserva (barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos,
                                     expira_em, aceite_politica_versao, aceite_politica_em)
                VALUES (?, ?, 'GUEST', ?, ?, ?, ?, 5500, ?, ?, ?)
                """, barbeiro, servico, inicio, fim, fim, status,
                aguardando ? OffsetDateTime.ofInstant(expiraEm, ZoneOffset.UTC) : null,
                pago ? 1 : null, pago ? inicio.minusDays(1) : null);
    }

    public static void folga(JdbcTemplate jdbc, UUID barbeiro, OffsetDateTime inicio, OffsetDateTime fim) {
        jdbc.update("INSERT INTO folga (barbeiro_id, inicio, fim, motivo) VALUES (?, ?, ?, 'teste')", barbeiro, inicio, fim);
    }
}
