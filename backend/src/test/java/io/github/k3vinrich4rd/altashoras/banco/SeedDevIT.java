package io.github.k3vinrich4rd.altashoras.banco;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/** A9: o perfil dev sobe com o barbeiro, o catálogo e a jornada reais (D36, D37). */
@SpringBootTest
@ActiveProfiles("dev")
@Import(PostgresTestcontainers.class)
class SeedDevIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("A9: 1 barbeiro, Carlos Eduardo Cândido")
    void barbeiro() {
        assertThat(jdbc.queryForList("SELECT nome_exibicao FROM barbeiro", String.class))
                .containsExactly("Carlos Eduardo Cândido");
    }

    @Test
    @DisplayName("A9: 5 serviços com os preços reais em centavos; o de visagismo dura 60 min (2 slots)")
    void catalogo() {
        List<Map<String, Object>> servicos = jdbc.queryForList(
                "SELECT nome, duracao_min, preco_centavos FROM servico ORDER BY preco_centavos");
        assertThat(servicos).extracting(s -> s.get("preco_centavos"))
                .containsExactly(2000L, 3000L, 4500L, 5500L, 6000L);
        assertThat(jdbc.queryForObject("SELECT duracao_min FROM servico WHERE preco_centavos = 6000", Integer.class))
                .isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM servico WHERE duracao_min = 30", Integer.class))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("A9: terça a sábado, 07h-12h e 13h-20h (almoço livre); domingo e segunda fechados")
    void jornadaComAlmoco() {
        List<String> jornada = jdbc.queryForList("""
                SELECT dia_semana || ' ' || to_char(hora_inicio, 'HH24:MI') || '-' || to_char(hora_fim, 'HH24:MI')
                FROM jornada ORDER BY dia_semana, hora_inicio
                """, String.class);
        assertThat(jornada).hasSize(10)
                .startsWith("2 07:00-12:00", "2 13:00-20:00")
                .endsWith("6 07:00-12:00", "6 13:00-20:00")
                .noneMatch(j -> j.startsWith("1 ") || j.startsWith("7 "));
    }
}
