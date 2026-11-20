package io.github.k3vinrich4rd.altashoras.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
 * A1 a A6 da fatia do catálogo inicial (D152): o que a V8 deixa no banco, visto pelas rotas públicas, e a V8 rodada
 * de novo sem duplicar nem sobrescrever nada. A propriedade "teste.contexto" separa este contexto (e o banco dele) dos
 * outros testes, que apagam o catálogo para montar o próprio cenário. Os testes que mexem no banco rodam numa
 * transação desfeita no fim.
 */
@SpringBootTest(properties = "teste.contexto=v8-catalogo-inicial")
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class V8CatalogoInicialIT {

    private static final String V8 = "db/migration/V8__catalogo_inicial.sql";
    private static final UUID EDUARDO = UUID.fromString("b0000000-0000-4000-8000-000000000001");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Autowired
    Clock clock;

    private void rodarV8DeNovo() {
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new ClassPathResource(V8));
    }

    private int contar(String tabela) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabela, Integer.class);
    }

    @Test
    @DisplayName("A1: depois da V8, GET /servicos devolve os 5 serviços da tabela oficial, todos com 60 min, com nome e valor")
    void servicosPublicos() throws Exception {
        mvc.perform(get("/servicos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.duracaoMin != 60)]").isEmpty())
                .andExpect(jsonPath("$[?(@.nome=='Corte Clássico / Social')].precoCentavos").value(4500))
                .andExpect(jsonPath("$[?(@.nome=='Corte Degradê / Fade / Navalhado')].precoCentavos").value(5500))
                .andExpect(jsonPath("$[?(@.nome=='Corte Moderno / Visagismo (tesoura)')].precoCentavos").value(6000))
                .andExpect(jsonPath("$[?(@.nome=='Barba Completa / Desenhada')].precoCentavos").value(3000))
                .andExpect(jsonPath("$[?(@.nome=='Acabamento (pezinho) / Sobrancelha')].precoCentavos").value(2000))
                .andExpect(jsonPath("$[?(@.nome=='Corte Degradê / Fade / Navalhado')].precoFormatado").value("R$ 55,00"));
    }

    @Test
    @DisplayName("A2: depois da V8, GET /barbeiros devolve só o Eduardo Candido, com o WhatsApp comercial público (só dígitos)")
    void barbeiroPublico() throws Exception {
        mvc.perform(get("/barbeiros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(EDUARDO.toString()))
                .andExpect(jsonPath("$[0].nomeExibicao").value("Eduardo Candido"))
                .andExpect(jsonPath("$[0].whatsapp").value("11996941689"));
    }

    @Test
    @DisplayName("A3: a jornada padrão (terça a sábado, 7h às 20h, almoço das 12h às 13h) dá 22 horários numa terça livre e nenhum na segunda")
    void jornadaPadrao() throws Exception {
        LocalDate terca = LocalDate.now(clock).with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        mvc.perform(get("/disponibilidade")
                        .param("servicoId", "6f1c2a8e-0000-4000-8000-000000000002")
                        .param("data", terca.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barbeiros[0].nomeExibicao").value("Eduardo Candido"))
                .andExpect(jsonPath("$.barbeiros[0].horarios.length()").value(22));
        mvc.perform(get("/disponibilidade")
                        .param("servicoId", "6f1c2a8e-0000-4000-8000-000000000002")
                        .param("data", terca.plusDays(6).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barbeiros[*].horarios[*]").isEmpty());
        assertThat(jdbc.queryForList("SELECT DISTINCT dia_semana FROM jornada WHERE barbeiro_id = ? ORDER BY 1",
                Integer.class, EDUARDO)).containsExactly(2, 3, 4, 5, 6);
    }

    @Test
    @Transactional
    @DisplayName("A4: a V8 rodada mais duas vezes não duplica serviço, barbeiro nem jornada")
    void idempotente() {
        rodarV8DeNovo();
        rodarV8DeNovo();
        assertThat(contar("servico")).isEqualTo(5);
        assertThat(contar("barbeiro")).isEqualTo(1);
        assertThat(contar("jornada")).isEqualTo(10);
    }

    @Test
    @Transactional
    @DisplayName("A5: rodada de novo, a V8 não sobrescreve o que o admin mudou (preço, nome, jornada) nem recria o que ele desativou")
    void naoSobrescreveOPainel() {
        jdbc.update("UPDATE servico SET preco_centavos = 5000 WHERE nome = 'Corte Clássico / Social'");
        jdbc.update("UPDATE servico SET ativo = false WHERE nome = 'Barba Completa / Desenhada'");
        jdbc.update("UPDATE barbeiro SET nome_exibicao = 'Eduardo' WHERE id = ?", EDUARDO);
        jdbc.update("DELETE FROM jornada WHERE barbeiro_id = ? AND dia_semana = 6", EDUARDO);

        rodarV8DeNovo();

        assertThat(jdbc.queryForObject("SELECT preco_centavos FROM servico WHERE nome = 'Corte Clássico / Social'",
                Long.class)).isEqualTo(5000L);
        assertThat(jdbc.queryForObject("SELECT ativo FROM servico WHERE nome = 'Barba Completa / Desenhada'",
                Boolean.class)).isFalse();
        assertThat(jdbc.queryForList("SELECT nome_exibicao FROM barbeiro", String.class)).containsExactly("Eduardo");
        assertThat(contar("servico")).isEqualTo(5);
        assertThat(contar("jornada")).isEqualTo(8);
    }

    @Test
    @Transactional
    @DisplayName("A6: se o admin já cadastrou o mesmo serviço (outra caixa) ou o mesmo barbeiro com outro id, a V8 não cria cópia")
    void naoDuplicaCadastroDoPainel() {
        jdbc.update("DELETE FROM jornada");
        jdbc.update("DELETE FROM barbeiro");
        jdbc.update("DELETE FROM servico");
        jdbc.update("INSERT INTO servico (id, nome, duracao_min, preco_centavos) VALUES (?, 'CORTE CLÁSSICO / SOCIAL', 60, 4500)",
                UUID.randomUUID());
        UUID outro = UUID.randomUUID();
        jdbc.update("INSERT INTO barbeiro (id, nome_exibicao) VALUES (?, 'EDUARDO CANDIDO')", outro);

        rodarV8DeNovo();

        assertThat(contar("servico")).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM servico WHERE lower(nome) = lower('Corte Clássico / Social')",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id FROM barbeiro", UUID.class)).containsExactly(outro);
        assertThat(contar("jornada")).isZero();
    }

    @Test
    @Transactional
    @DisplayName("A7: num banco que já tinha o primeiro admin, a V8 liga o barbeiro criado a essa conta (a mesma regra do evento da subida)")
    void ligaAoAdminQueJaExistia() {
        jdbc.update("DELETE FROM jornada");
        jdbc.update("DELETE FROM barbeiro");
        UUID admin = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id, nome, email) VALUES (?, 'Administrador', 'admin-v8@exemplo.test')", admin);
        jdbc.update("INSERT INTO usuario_papel (usuario_id, papel) VALUES (?, 'ADMIN'), (?, 'BARBEIRO')", admin, admin);

        rodarV8DeNovo();

        assertThat(jdbc.queryForObject("SELECT usuario_id FROM barbeiro WHERE id = ?", UUID.class, EDUARDO))
                .isEqualTo(admin);
        assertThat(contar("jornada")).isEqualTo(10);
    }
}
