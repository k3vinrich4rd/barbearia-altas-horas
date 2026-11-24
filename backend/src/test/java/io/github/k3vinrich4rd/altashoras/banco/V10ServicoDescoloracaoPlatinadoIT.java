package io.github.k3vinrich4rd.altashoras.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;

/**
 * A21 da Fatia 12 (site mais fluido): o serviço "Descoloração / Platinado (química)" que a V10 grava, visto pela rota
 * pública, e a V10 rodada de novo sem duplicar nem sobrescrever o que o admin fez. No molde do V8CatalogoInicialIT: a
 * propriedade "teste.contexto" separa este contexto (e o banco dele) dos outros, que apagam o catálogo para montar o
 * próprio cenário, e os testes que mexem no banco rodam numa transação desfeita no fim.
 */
@SpringBootTest(properties = "teste.contexto=v10-servico-descoloracao-platinado")
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class V10ServicoDescoloracaoPlatinadoIT {

    private static final String V10 = "db/migration/V10__servico_descoloracao_platinado.sql";
    private static final String NOME = "Descoloração / Platinado (química)";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    private void rodarV10DeNovo() {
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new ClassPathResource(V10));
    }

    private int contarDoServico() {
        return jdbc.queryForObject("SELECT count(*) FROM servico WHERE lower(nome) = lower(?)", Integer.class, NOME);
    }

    @Test
    @DisplayName("A21: depois da V10, GET /servicos traz a Descoloração / Platinado (química) com 60 min e R$ 80,00 (8000 centavos), ao lado dos 5 da V8")
    void servicoNoCatalogoPublico() throws Exception {
        mvc.perform(get("/servicos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.nome=='" + NOME + "')].duracaoMin").value(60))
                .andExpect(jsonPath("$[?(@.nome=='" + NOME + "')].precoCentavos").value(8000))
                .andExpect(jsonPath("$[?(@.nome=='" + NOME + "')].precoFormatado").value("R$ 80,00"))
                .andExpect(jsonPath("$[?(@.nome=='Corte Clássico / Social')].precoCentavos").value(4500));
    }

    @Test
    @Transactional
    @DisplayName("A21: a V10 rodada mais duas vezes não duplica o serviço")
    void idempotente() {
        rodarV10DeNovo();
        rodarV10DeNovo();
        assertThat(contarDoServico()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM servico", Integer.class)).isEqualTo(6);
    }

    @Test
    @Transactional
    @DisplayName("A21: rodada de novo, a V10 não sobrescreve o preço nem recria o serviço que o admin desativou")
    void naoSobrescreveOPainel() {
        jdbc.update("UPDATE servico SET preco_centavos = 9000, ativo = false WHERE lower(nome) = lower(?)", NOME);

        rodarV10DeNovo();

        assertThat(jdbc.queryForObject("SELECT preco_centavos FROM servico WHERE lower(nome) = lower(?)", Long.class,
                NOME)).isEqualTo(9000L);
        assertThat(jdbc.queryForObject("SELECT ativo FROM servico WHERE lower(nome) = lower(?)", Boolean.class, NOME))
                .isFalse();
        assertThat(contarDoServico()).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("A21: se o admin já criou um serviço de mesmo nome (outra caixa, outro id), a V10 não cria cópia nem altera o dele")
    void naoDuplicaServicoDoPainel() {
        jdbc.update("DELETE FROM servico WHERE lower(nome) = lower(?)", NOME);
        jdbc.update("INSERT INTO servico (id, nome, duracao_min, preco_centavos) VALUES (?, 'DESCOLORAÇÃO / PLATINADO (QUÍMICA)', 90, 12000)",
                UUID.randomUUID());

        rodarV10DeNovo();

        assertThat(contarDoServico()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT preco_centavos FROM servico WHERE lower(nome) = lower(?)", Long.class,
                NOME)).isEqualTo(12000L);
        assertThat(jdbc.queryForObject("SELECT duracao_min FROM servico WHERE lower(nome) = lower(?)", Integer.class,
                NOME)).isEqualTo(90);
    }
}
