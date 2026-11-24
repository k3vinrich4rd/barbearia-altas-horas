package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A9 da Fatia 11A: a migração que limpa o dado antigo sem prova de consentimento e tira o CEP do banco (V12, depois da V11 que
 * acrescenta as colunas). Roda num PostgreSQL só dele: sobe o esquema até a V11, planta dados com e sem prova, migra até o fim
 * e confere. Os testes de pessoa e de nível não dependem disto.
 */
class MigracaoRegiaoSemCepIT {

    private static final OffsetDateTime INICIO = OffsetDateTime.of(2026, 11, 10, 13, 0, 0, 0, ZoneOffset.UTC);

    @Test
    @DisplayName("A9: banco na V11 com reservas e contas com região, uns com prova de consentimento e outros sem: a V12 anula a região sem prova, preserva a com prova, remove toda coluna cep, o flyway validate fica limpo e uma segunda execução não muda nada")
    void migracaoLimpaOQueNaoTemProva() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource fonte = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                    postgres.getPassword());
            Flyway ate11 = Flyway.configure().dataSource(fonte).locations("classpath:db/migration").target("11").load();
            ate11.migrate();
            JdbcTemplate jdbc = new JdbcTemplate(fonte);

            UUID barbeiro = UUID.randomUUID();
            UUID servico = UUID.randomUUID();
            jdbc.update("INSERT INTO barbeiro (id, nome_exibicao, ativo) VALUES (?, 'Barbeiro Teste', true)", barbeiro);
            jdbc.update("INSERT INTO servico (id, nome, duracao_min, preco_centavos, ativo) VALUES (?, 'Corte', 30, 5000, true)", servico);

            // Contas: com prova (aceite mais recente), revogada (último registro é não) e sem nenhuma linha de região
            UUID comProva = usuario(jdbc, "com.prova");
            UUID revogada = usuario(jdbc, "revogada");
            UUID semProva = usuario(jdbc, "sem.prova");
            consentimento(jdbc, comProva, true, 1);
            consentimento(jdbc, revogada, true, 1);
            consentimento(jdbc, revogada, false, 2);

            // Reservas: uma com a versão do texto aceito (prova) e outra com região mas sem versão
            UUID reservaComProva = reserva(jdbc, barbeiro, servico, 0, "1.2");
            UUID reservaSemProva = reserva(jdbc, barbeiro, servico, 1, null);
            jdbc.update("UPDATE reserva_guest SET cep = '06010000' WHERE reserva_id IN (?, ?)", reservaComProva, reservaSemProva);

            Flyway tudo = Flyway.configure().dataSource(fonte).locations("classpath:db/migration").target("12").load(); // a V13 (Fatia 13) é de outra fatia
            assertThat(tudo.migrate().migrationsExecuted).isEqualTo(1);

            assertThat(jdbc.queryForList("SELECT 1 FROM usuario WHERE id = ? AND cidade = 'Osasco' AND regiao_municipio_ibge = '3534401'",
                    comProva)).hasSize(1);
            for (UUID semPreservar : new UUID[] {revogada, semProva}) {
                assertThat(jdbc.queryForMap("SELECT bairro, cidade, uf, regiao_municipio_ibge FROM usuario WHERE id = ?",
                        semPreservar).values()).containsOnlyNulls();
            }
            assertThat(jdbc.queryForMap("SELECT regiao_cidade, regiao_municipio_ibge FROM reserva WHERE id = ?", reservaComProva))
                    .containsEntry("regiao_cidade", "Osasco").containsEntry("regiao_municipio_ibge", "3534401");
            assertThat(jdbc.queryForMap("""
                    SELECT regiao_bairro, regiao_cidade, regiao_uf, regiao_municipio_ibge FROM reserva WHERE id = ?""",
                    reservaSemProva).values()).containsOnlyNulls();
            assertThat(jdbc.queryForList("SELECT 1 FROM reserva WHERE id IN (?, ?)", reservaComProva, reservaSemProva)).hasSize(2);

            assertThat(jdbc.queryForList("""
                    SELECT table_name || '.' || column_name FROM information_schema.columns
                    WHERE table_schema = 'public' AND lower(column_name) LIKE '%cep%'""", String.class)).isEmpty();
            tudo.validate();
            assertThat(tudo.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE cidade IS NOT NULL", Integer.class)).isEqualTo(1);
        }
    }

    private static UUID usuario(JdbcTemplate jdbc, String nome) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, nome, email, whatsapp, cep, bairro, cidade, uf, regiao_municipio_ibge)
                VALUES (?, ?, ?, '11900000001', '06010000', 'Centro', 'Osasco', 'SP', '3534401')
                """, id, nome, nome + "@exemplo.test");
        return id;
    }

    private static void consentimento(JdbcTemplate jdbc, UUID usuario, boolean aceito, int minutos) {
        jdbc.update("""
                INSERT INTO consentimento (usuario_id, finalidade, versao_texto, aceito, registrado_em)
                VALUES (?, 'REGIAO_ALCANCE', '1.2', ?, ?)
                """, usuario, aceito, INICIO.minusDays(2).plusMinutes(minutos));
    }

    private static UUID reserva(JdbcTemplate jdbc, UUID barbeiro, UUID servico, int ordem, String versao) {
        UUID id = UUID.randomUUID();
        OffsetDateTime inicio = INICIO.plusMinutes(30L * ordem);
        jdbc.update("""
                INSERT INTO reserva (id, barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos,
                                     aceite_politica_versao, aceite_politica_em, regiao_bairro, regiao_cidade, regiao_uf,
                                     regiao_municipio_ibge, regiao_consentimento_versao)
                VALUES (?, ?, ?, 'GUEST', ?, ?, ?, 'CONCLUIDO', 5000, 1, ?, 'Centro', 'Osasco', 'SP', '3534401', ?)
                """, id, barbeiro, servico, inicio, inicio.plusMinutes(30), inicio.plusMinutes(30), inicio.minusDays(1), versao);
        jdbc.update("""
                INSERT INTO reserva_guest (reserva_id, nome, email, whatsapp, token_hash)
                VALUES (?, 'Visitante Teste', ?, '11988880002', ?)
                """, id, "v." + id + "@exemplo.test", (id.toString() + id).replace("-", "").substring(0, 64));
        return id;
    }
}
