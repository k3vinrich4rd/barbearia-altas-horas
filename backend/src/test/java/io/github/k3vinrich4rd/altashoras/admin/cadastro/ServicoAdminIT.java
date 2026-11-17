package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.CadastrosBase;

/** A41 a A47: serviços do painel (criar, alterar, inativar) sem mexer no que já foi vendido. */
class ServicoAdminIT extends CadastrosBase {

    private static final String NOVO = """
            { "nome": "%s", "duracaoMin": %d, "precoCentavos": %d }
            """;

    private static String corpoDeAlteracao(String nome, int duracaoMin, long precoCentavos, boolean ativo, long version) {
        return """
                { "nome": "%s", "duracaoMin": %d, "precoCentavos": %d, "ativo": %s, "version": %d }
                """.formatted(nome, duracaoMin, precoCentavos, ativo, version);
    }

    private long totalDeServicos() {
        return jdbc.queryForObject("SELECT count(*) FROM servico", Long.class);
    }

    @Test
    @DisplayName("A41: GET /admin/servicos lista os 6 (5 ativos e 1 inativo, com ativo e version) e o GET /servicos público só os 5 ativos")
    void listaComInativos() throws Exception {
        for (int i = 0; i < 5; i++) {
            DadosDeTeste.servico(jdbc, "Serviço ativo", 30, 4500 + i, true);
        }
        DadosDeTeste.servico(jdbc, "Serviço antigo", 30, 4500, false);
        Conta admin = admin();

        String corpo = get("/admin/servicos", admin).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].precoFormatado").isNotEmpty())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
        List<Boolean> ativos = JsonPath.read(corpo, "$[*].ativo");
        List<Integer> versoes = JsonPath.read(corpo, "$[*].version");
        assertThat(ativos).hasSize(6).containsOnlyOnce(false);
        assertThat(versoes).hasSize(6).containsOnly(0);
        mvc.perform(MockMvcRequestBuilders.get("/servicos")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].ativo").doesNotExist());
    }

    @Test
    @DisplayName("A42: POST /admin/servicos cria (201, Location, ativo, version 0, precoFormatado) e o serviço aparece em GET /servicos")
    void cria() throws Exception {
        String corpo = postJson("/admin/servicos", admin(), NOVO.formatted("Hidratação", 30, 3500))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/admin/servicos/")))
                .andExpect(jsonPath("$.ativo").value(true)).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.precoFormatado").value("R$ 35,00"))
                .andExpect(jsonPath("$.duracaoMin").value(30)).andExpect(jsonPath("$.precoCentavos").value(3500))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(corpo, "$.id");

        mvc.perform(MockMvcRequestBuilders.get("/servicos")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + id + "')].nome")
                .value("Hidratação"));
        assertThat(jdbc.queryForObject("SELECT atualizado_em FROM servico WHERE id = ?::uuid", OffsetDateTime.class, id)
                .toInstant()).isEqualTo(io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.AGORA);
    }

    @Test
    @DisplayName("A43: nome repetido (sem diferença de maiúsculas) é 409 NOME_JA_EXISTE; dois POST simultâneos com o mesmo nome dão um 201 e um 409, nunca 500")
    void nomeRepetido() throws Exception {
        servicoComNome("Corte Clássico/Social", 30, 4500, true);
        Conta admin = admin();

        postJson("/admin/servicos", admin, NOVO.formatted("corte clássico/social", 30, 4500))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOME_JA_EXISTE"));
        assertThat(totalDeServicos()).isEqualTo(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int rodada = 0; rodada < 5; rodada++) {
                String nome = "Barba Premium " + rodada;
                CountDownLatch largada = new CountDownLatch(1);
                List<Future<MockHttpServletResponse>> respostas = List.of(
                        pool.submit(() -> {
                            largada.await();
                            return postJson("/admin/servicos", admin, NOVO.formatted(nome, 30, 4000)).andReturn().getResponse();
                        }),
                        pool.submit(() -> {
                            largada.await();
                            return postJson("/admin/servicos", admin, NOVO.formatted(nome.toUpperCase(), 30, 4000))
                                    .andReturn().getResponse();
                        }));
                largada.countDown();
                MockHttpServletResponse a = respostas.get(0).get();
                MockHttpServletResponse b = respostas.get(1).get();

                assertThat(List.of(a.getStatus(), b.getStatus())).containsExactlyInAnyOrder(201, 409);
                MockHttpServletResponse perdedora = a.getStatus() == 409 ? a : b;
                assertThat((String) JsonPath.read(perdedora.getContentAsString(), "$.code")).isEqualTo("NOME_JA_EXISTE");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM servico WHERE lower(nome) = lower(?)", Long.class, nome))
                        .isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("A44: nome de 1 letra, duração 4 ou 481, preço 0 e campo extra (ativo, id) são 400 com o campo apontado, e nada é gravado")
    void corposInvalidos() throws Exception {
        Conta admin = admin();
        postJson("/admin/servicos", admin, NOVO.formatted("A", 30, 3500)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA")).andExpect(jsonPath("$.errors[0].field").value("nome"));
        postJson("/admin/servicos", admin, NOVO.formatted("Corte", 4, 3500)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("duracaoMin"));
        postJson("/admin/servicos", admin, NOVO.formatted("Corte", 481, 3500)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("duracaoMin"));
        postJson("/admin/servicos", admin, NOVO.formatted("Corte", 30, 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("precoCentavos"));
        postJson("/admin/servicos", admin, """
                { "nome": "Corte", "duracaoMin": 30, "precoCentavos": 3500, "ativo": false }
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));
        postJson("/admin/servicos", admin, """
                { "id": "%s", "nome": "Corte", "duracaoMin": 30, "precoCentavos": 3500 }
                """.formatted(UUID.randomUUID())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));

        assertThat(totalDeServicos()).isZero();
    }

    @Test
    @DisplayName("A45: PUT com a version certa dá 200 e version 1; repetir a version 0 dá 409; renomear para o nome de outro dá 409 NOME_JA_EXISTE; dois PUT simultâneos dão um 200 e um 409")
    void alteracao() throws Exception {
        UUID servico = servicoComNome("Corte Simples", 30, 4500, true);
        servicoComNome("Barba Completa", 30, 3000, true);
        Conta admin = admin();
        String rota = "/admin/servicos/" + servico;

        put(rota, admin, corpoDeAlteracao("Corte Simples", 30, 5000, true, 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.precoCentavos").value(5000))
                .andExpect(jsonPath("$.precoFormatado").value("R$ 50,00"));
        put(rota, admin, corpoDeAlteracao("Corte Simples", 30, 5500, true, 0)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLITO_DE_VERSAO"));
        assertThat(jdbc.queryForObject("SELECT preco_centavos FROM servico WHERE id = ?", Long.class, servico)).isEqualTo(5000);
        put(rota, admin, corpoDeAlteracao("barba completa", 30, 5000, true, 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOME_JA_EXISTE"));
        assertThat(jdbc.queryForObject("SELECT nome FROM servico WHERE id = ?", String.class, servico)).isEqualTo("Corte Simples");

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int rodada = 0; rodada < 5; rodada++) {
                long versao = jdbc.queryForObject("SELECT version FROM servico WHERE id = ?", Long.class, servico);
                long preco = 6000 + rodada;
                CountDownLatch largada = new CountDownLatch(1);
                List<Future<MockHttpServletResponse>> respostas = List.of(
                        pool.submit(() -> {
                            largada.await();
                            return put(rota, admin, corpoDeAlteracao("Corte Simples", 30, preco, true, versao))
                                    .andReturn().getResponse();
                        }),
                        pool.submit(() -> {
                            largada.await();
                            return put(rota, admin, corpoDeAlteracao("Corte Simples", 45, preco + 100, true, versao))
                                    .andReturn().getResponse();
                        }));
                largada.countDown();
                List<Integer> status = List.of(respostas.get(0).get().getStatus(), respostas.get(1).get().getStatus());

                assertThat(status).containsExactlyInAnyOrder(200, 409);
                MockHttpServletResponse perdedora = respostas.get(0).get().getStatus() == 409 ? respostas.get(0).get()
                        : respostas.get(1).get();
                assertThat((String) JsonPath.read(perdedora.getContentAsString(), "$.code")).isEqualTo("CONFLITO_DE_VERSAO");
                assertThat(jdbc.queryForObject("SELECT version FROM servico WHERE id = ?", Long.class, servico))
                        .isEqualTo(versao + 1);
            }
        }
    }

    @Test
    @DisplayName("A46: desativar o serviço o tira de GET /servicos e POST /agendamentos dá 422 SERVICO_INATIVO, mas a reserva futura continua CONFIRMADO e na agenda")
    void inativarNaoCancela() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID reserva = reserva(barbeiro, servico, as(10, 0), "CONFIRMADO", null, cliente("Maria Souza Teste", "11988880001"),
                null);
        Conta admin = admin();
        String nome = jdbc.queryForObject("SELECT nome FROM servico WHERE id = ?", String.class, servico);

        put("/admin/servicos/" + servico, admin, corpoDeAlteracao(nome, 30, 4500, false, 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mvc.perform(MockMvcRequestBuilders.get("/servicos")).andExpect(jsonPath("$[?(@.id == '" + servico + "')]").isEmpty());
        reservar(conta("CLIENTE"), servico, barbeiro, as(11, 0)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SERVICO_INATIVO"));
        assertThat(statusDaReserva(reserva)).isEqualTo("CONFIRMADO");
        get("/admin/agenda?data=2026-12-01", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + reserva + "')].status").value("CONFIRMADO"));
    }

    @Test
    @DisplayName("A47: mudar o preço e a duração não reescreve a reserva nem a cobrança antigas (4500 e o mesmo fim); a reserva nova sai com o preço e a duração novos")
    void mudancaNaoReescreveOPassado() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID antiga = reserva(barbeiro, servico, as(9, 0), "CONFIRMADO", null, cliente("Maria Souza Teste", "11988880001"), null);
        cobranca(antiga, "PAGA", as(8, 0));
        OffsetDateTime fimAntes = jdbc.queryForObject("SELECT fim FROM reserva WHERE id = ?", OffsetDateTime.class, antiga);
        String nome = jdbc.queryForObject("SELECT nome FROM servico WHERE id = ?", String.class, servico);

        put("/admin/servicos/" + servico, admin(), corpoDeAlteracao(nome, 45, 5000, true, 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.precoCentavos").value(5000)).andExpect(jsonPath("$.duracaoMin").value(45));

        assertThat(jdbc.queryForObject("SELECT preco_centavos FROM reserva WHERE id = ?", Long.class, antiga)).isEqualTo(4500);
        assertThat(jdbc.queryForObject("SELECT valor_centavos FROM cobranca WHERE reserva_id = ?", Long.class, antiga))
                .isEqualTo(4500);
        assertThat(jdbc.queryForObject("SELECT fim FROM reserva WHERE id = ?", OffsetDateTime.class, antiga).toInstant())
                .isEqualTo(fimAntes.toInstant());

        String corpo = reservar(conta("CLIENTE"), servico, barbeiro, as(11, 0)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID nova = UUID.fromString(JsonPath.read(corpo, "$.id"));
        assertThat(jdbc.queryForObject("SELECT preco_centavos FROM reserva WHERE id = ?", Long.class, nova)).isEqualTo(5000);
        assertThat(jdbc.queryForObject("SELECT fim FROM reserva WHERE id = ?", OffsetDateTime.class, nova).toInstant())
                .isEqualTo(as(11, 45).toInstant());
    }
}
