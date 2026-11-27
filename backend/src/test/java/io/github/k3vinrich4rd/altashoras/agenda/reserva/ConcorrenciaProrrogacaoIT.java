package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.em;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.eventosDePrazo;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.expiraEm;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogacoes;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogar;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.as;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpo;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.reservar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;

/**
 * A40 da Fatia 15 (E21, E22): dois pedidos de mais tempo sem chave ao mesmo tempo, no último minuto. A trava da linha
 * ({@code FOR UPDATE}) faz o segundo decidir depois do commit do primeiro: o prazo já tem mais de 60 s, então ele cai fora
 * da janela. Nunca somam duas vezes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class ConcorrenciaProrrogacaoIT {

    private static final int RODADAS = 50;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelogioAjustavel relogio;

    @AfterEach
    void devolverRelogio() {
        relogio.reiniciar();
    }

    private static List<MockHttpServletResponse> soltarJuntas(List<Callable<MockHttpServletResponse>> tarefas)
            throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futuros = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(tarefas.size())) {
            for (var tarefa : tarefas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            List<MockHttpServletResponse> respostas = new ArrayList<>();
            for (Future<MockHttpServletResponse> futuro : futuros) {
                respostas.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return respostas;
        }
    }

    @Test
    @DisplayName("A40: dois pedidos sem chave ao mesmo tempo no último minuto, em 50 repetições: em toda repetição um 201 e um 422 PRORROGACAO_FORA_DA_JANELA; usadas sobe 1 e expiraEm ganha 120 s uma vez só")
    void doisPedidosSemChave() throws Exception {
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        for (int rodada = 0; rodada < RODADAS; rodada++) {
            relogio.definir(em(10, 0, 0));
            UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
            String acesso = Reservas.clienteLogado(mvc);
            String id = JsonPath.read(reservar(mvc, acesso, corpo(servico, barbeiro, as(15, 0)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

            relogio.definir(em(10, 2, 30));
            List<MockHttpServletResponse> respostas = soltarJuntas(List.of(
                    () -> prorrogar(mvc, acesso, id).andReturn().getResponse(),
                    () -> prorrogar(mvc, acesso, id).andReturn().getResponse()));

            List<Integer> codigos = respostas.stream().map(MockHttpServletResponse::getStatus).toList();
            assertThat(codigos).as("rodada %d", rodada).containsExactlyInAnyOrder(201, 422);
            MockHttpServletResponse recusada = respostas.stream().filter(r -> r.getStatus() == 422).findFirst().orElseThrow();
            assertThat(JsonPath.<String>read(recusada.getContentAsString(), "$.code"))
                    .as("rodada %d", rodada).isEqualTo("PRORROGACAO_FORA_DA_JANELA");
            assertThat(prorrogacoes(jdbc, id)).as("rodada %d", rodada).isEqualTo(1);
            assertThat(expiraEm(jdbc, id)).as("rodada %d", rodada).isEqualTo(em(10, 5, 0));
            assertThat(eventosDePrazo(jdbc, id)).as("rodada %d", rodada).isEqualTo(1);
        }
    }
}
