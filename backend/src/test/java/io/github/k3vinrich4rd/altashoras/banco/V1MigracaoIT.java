package io.github.k3vinrich4rd.altashoras.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/** A3 e A8: a V1 sobe do zero com a extensão, a constraint e as regras de pagamento 100% (D31 a D35). */
@SpringBootTest
@Import(PostgresTestcontainers.class)
class V1MigracaoIT {

    private static final OffsetDateTime INICIO = OffsetDateTime.of(2026, 12, 1, 13, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("A3: Flyway aplicou a V1 com sucesso")
    void v1Aplicada() {
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("A3: extensão btree_gist, constraint anti-sobreposição e índices existem")
    void estruturaExiste() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT contype FROM pg_constraint WHERE conname = 'reserva_sem_sobreposicao'", String.class))
                .isEqualTo("x");
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'reserva'", String.class);
        assertThat(indices).contains("reserva_barbeiro_inicio_ix", "reserva_expiracao_ix", "reserva_cliente_inicio_ix");
    }

    @Test
    @DisplayName("Configuração inicial = regras reais: slots de 30 min e 3 min para pagar (a V1 nasceu com 15; a V7 trocou para 3, D121; a V14 deixou a antecedência e o horizonte sem uso, D197 e D199)")
    void configuracaoReal() {
        var cfg = jdbc.queryForMap("SELECT * FROM configuracao_agenda WHERE id = 1");
        assertThat(cfg).containsEntry("intervalo_entre_atendimentos_min", 0)
                .containsEntry("granularidade_min", 30)
                .containsEntry("expiracao_pagamento_min", 3)
                .containsEntry("fuso", "America/Sao_Paulo");
        assertThat(jdbc.queryForObject("SELECT texto FROM politica_pagamento WHERE versao = 1", String.class))
                .contains("100%").contains("não há cancelamento, remarcação ou estorno");
    }

    @Test
    @DisplayName("A8: AGUARDANDO_PAGAMENTO sem expira_em é recusada pelo banco")
    void aguardandoSemPrazoRecusada() {
        UUID barbeiro = ReservaSql.novoBarbeiro(jdbc);
        UUID servico = ReservaSql.novoServico(jdbc);
        Object[] p = ReservaSql.parametros(barbeiro, servico, INICIO, "AGUARDANDO_PAGAMENTO");
        p[7] = null;
        assertThatThrownBy(() -> jdbc.update(ReservaSql.INSERIR, p))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("reserva_expiracao_ck");
    }

    @Test
    @DisplayName("CONFIRMADO sem aceite da política é recusada pelo banco (R18, D34)")
    void confirmadoSemAceiteRecusada() {
        UUID barbeiro = ReservaSql.novoBarbeiro(jdbc);
        UUID servico = ReservaSql.novoServico(jdbc);
        Object[] p = ReservaSql.parametros(barbeiro, servico, INICIO, "CONFIRMADO");
        p[8] = null;
        p[9] = null;
        assertThatThrownBy(() -> jdbc.update(ReservaSql.INSERIR, p))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("reserva_aceite_ck");
    }

    @Test
    @DisplayName("Cobrança só aceita PIX ou CARTAO_CREDITO (D31)")
    void metodoDeCobrancaRestrito() {
        UUID barbeiro = ReservaSql.novoBarbeiro(jdbc);
        UUID servico = ReservaSql.novoServico(jdbc);
        Object[] p = ReservaSql.parametros(barbeiro, servico, INICIO, "AGUARDANDO_PAGAMENTO");
        jdbc.update(ReservaSql.INSERIR, p);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO cobranca (reserva_id, provedor, metodo, valor_centavos, status, chave_idempotencia_hash, hash_corpo)
                VALUES (?, 'ASAAS', 'BOLETO', 5500, 'PENDENTE', repeat('a', 64), repeat('b', 64))
                """, p[0]))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("cobranca_metodo_ck");
    }
}
