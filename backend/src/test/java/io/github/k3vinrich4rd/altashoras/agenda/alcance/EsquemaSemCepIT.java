package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/** A2 da Fatia 11A: o esquema do banco não guarda o CEP de ninguém, em nenhuma tabela. */
@SpringBootTest
@Import(PostgresTestcontainers.class)
class EsquemaSemCepIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("A2: o esquema do banco não tem nenhuma coluna \"cep\" (consulta ao information_schema), e a região guarda só bairro, município, UF e o código IBGE")
    void nenhumaColunaDeCep() {
        List<String> colunas = jdbc.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND lower(column_name) LIKE '%cep%'
                """, String.class);
        assertThat(colunas).isEmpty();

        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'reserva' AND column_name LIKE 'regiao_%'
                """, String.class)).containsExactlyInAnyOrder("regiao_bairro", "regiao_cidade", "regiao_uf",
                "regiao_municipio_ibge", "regiao_consentimento_versao");
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'usuario' AND column_name IN ('bairro', 'cidade', 'uf', 'regiao_municipio_ibge')
                """, String.class)).hasSize(4);
    }
}
