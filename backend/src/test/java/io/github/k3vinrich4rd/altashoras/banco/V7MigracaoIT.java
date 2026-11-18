package io.github.k3vinrich4rd.altashoras.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/**
 * A1 a A3 da Fatia 6D: o que a V7 deixa no banco. A configuração volta ao valor da V1 (3 min) no fim de cada teste que a
 * muda, porque os testes dividem o mesmo PostgreSQL.
 */
@SpringBootTest
@Import(PostgresTestcontainers.class)
class V7MigracaoIT {

    private static final OffsetDateTime INICIO = OffsetDateTime.of(2026, 12, 1, 13, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    JdbcTemplate jdbc;

    private int prazoNoBanco() {
        return jdbc.queryForObject("SELECT expiracao_pagamento_min FROM configuracao_agenda WHERE id = 1", Integer.class);
    }

    @Test
    @DisplayName("A1: a V7 foi aplicada, a configuração inicial tem prazo de pagamento de 3 minutos e o CHECK aceita de 3 a 60 (2 e 61 são recusados)")
    void prazoDeTresMinutos() {
        assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '7'", Boolean.class))
                .isTrue();
        assertThat(prazoNoBanco()).isEqualTo(3);
        try {
            jdbc.update("UPDATE configuracao_agenda SET expiracao_pagamento_min = 60 WHERE id = 1");
            assertThat(prazoNoBanco()).isEqualTo(60);
            assertThatThrownBy(() -> jdbc.update("UPDATE configuracao_agenda SET expiracao_pagamento_min = 2 WHERE id = 1"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("configuracao_agenda_valores_ck");
            assertThatThrownBy(() -> jdbc.update("UPDATE configuracao_agenda SET expiracao_pagamento_min = 61 WHERE id = 1"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("configuracao_agenda_valores_ck");
        } finally {
            jdbc.update("UPDATE configuracao_agenda SET expiracao_pagamento_min = 3 WHERE id = 1");
        }
    }

    @Test
    @DisplayName("A2: barbeiro.whatsapp é opcional e só aceita de 10 a 13 dígitos (letras, máscara e 9 dígitos são recusados pelo banco; 14 dígitos nem cabem na coluna)")
    void whatsappDoBarbeiro() {
        UUID sem = ReservaSql.novoBarbeiro(jdbc);
        assertThat(jdbc.queryForObject("SELECT whatsapp FROM barbeiro WHERE id = ?", String.class, sem)).isNull();

        jdbc.update("UPDATE barbeiro SET whatsapp = '11912345678' WHERE id = ?", sem);
        jdbc.update("UPDATE barbeiro SET whatsapp = '5511912345678' WHERE id = ?", sem);
        for (String invalido : new String[] {"123456789", "11-91234-5678", "abc1234567"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE barbeiro SET whatsapp = ? WHERE id = ?", invalido, sem))
                    .as(invalido).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("barbeiro_whatsapp_ck");
        }
    }

    private static final String INSERIR_MANUAL = """
            INSERT INTO reserva (barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos,
                                 expira_em, aceite_politica_versao, aceite_politica_em, nome_cliente, observacao)
            VALUES (?, ?, 'MANUAL', ?, ?, ?, ?, 5500, ?, ?, ?, ?, ?)
            """;

    private void inserirManual(UUID barbeiro, UUID servico, OffsetDateTime inicio, String status, Object expiraEm,
            Object aceite, String nome) {
        jdbc.update(INSERIR_MANUAL, barbeiro, servico, inicio, inicio.plusMinutes(30), inicio.plusMinutes(30), status,
                expiraEm, aceite == null ? null : 1, aceite, nome, null);
    }

    @Test
    @DisplayName("A3: o banco aceita a reserva MANUAL CONFIRMADA sem cliente, sem cobrança e sem aceite, e recusa MANUAL aguardando pagamento, com aceite ou com cliente, e nome_cliente fora da origem MANUAL")
    void reservaManualNoBanco() {
        UUID barbeiro = ReservaSql.novoBarbeiro(jdbc);
        UUID servico = ReservaSql.novoServico(jdbc);

        inserirManual(barbeiro, servico, INICIO, "CONFIRMADO", null, null, "Fulano de Tal");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE barbeiro_id = ? AND origem = 'MANUAL'",
                Long.class, barbeiro)).isEqualTo(1);

        assertThatThrownBy(() -> inserirManual(barbeiro, servico, INICIO.plusHours(2), "AGUARDANDO_PAGAMENTO",
                INICIO.minusHours(1), null, null)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("reserva_manual_ck");
        assertThatThrownBy(() -> inserirManual(barbeiro, servico, INICIO.plusHours(3), "CONFIRMADO", null,
                INICIO.minusDays(1), null)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("reserva_manual_ck");

        UUID cliente = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id, nome, email, whatsapp) VALUES (?, 'Cliente Teste', ?, '11900000009')",
                cliente, "cliente." + cliente + "@exemplo.test");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO reserva (barbeiro_id, servico_id, cliente_id, origem, inicio, fim, fim_ocupacao, status,
                                     preco_centavos)
                VALUES (?, ?, ?, 'MANUAL', ?, ?, ?, 'CONFIRMADO', 5500)
                """, barbeiro, servico, cliente, INICIO.plusHours(4), INICIO.plusHours(4).plusMinutes(30),
                INICIO.plusHours(4).plusMinutes(30))).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("reserva_origem_ck");

        Object[] guest = ReservaSql.parametros(barbeiro, servico, INICIO.plusHours(5), "CONFIRMADO");
        jdbc.update(ReservaSql.INSERIR, guest);
        assertThatThrownBy(() -> jdbc.update("UPDATE reserva SET nome_cliente = 'Fulano' WHERE id = ?", guest[0]))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("reserva_contato_manual_ck");
    }
}
