package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.em;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.eventosDePrazo;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.expiraEm;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogar;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.status;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.as;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpo;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.reservar;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.TOKEN_DO_WEBHOOK;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarPixComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDoWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.enviarWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.idDoProvedor;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaCobranca;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste.GatewayFalso;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;

/**
 * A4, A14, A15 e A16 da Fatia 14A: a prorrogação contra ela mesma, contra o webhook do pagamento e contra o job de
 * expiração. Cenário: reserva criada às 10:00:00 de ter 01/12/2026, prazo 3 minutos. Quem decide a corrida é o
 * {@code SELECT ... FOR UPDATE} da linha da reserva (E4), e o banco desempata o resto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, GatewayFalsoDeTeste.class})
class ProrrogacaoConcorrenciaIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelogioAjustavel relogio;

    @Autowired
    GatewayFalso gateway;

    @Autowired
    ProrrogarPagamento prorrogar;

    @Autowired
    ExpiracaoJob job;

    @Autowired
    PlatformTransactionManager gerenciador;

    private UUID barbeiro;
    private UUID servico;

    @BeforeEach
    void cenario() {
        gateway.reiniciar();
        relogio.definir(em(10, 0, 0));
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, 30);
    }

    @AfterEach
    void devolverRelogio() {
        relogio.reiniciar();
    }

    private static int[] soltarJuntas(List<java.util.concurrent.Callable<Integer>> tarefas) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(tarefas.size())) {
            for (var tarefa : tarefas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            int[] respostas = new int[tarefas.size()];
            for (int i = 0; i < respostas.length; i++) {
                respostas[i] = futuros.get(i).get(30, TimeUnit.SECONDS);
            }
            return respostas;
        }
    }

    @Test
    @DisplayName("A4: duas chamadas ao mesmo tempo às 10:02:30 dão uma 201 e uma 200; expiraEm 10:04:00 e um só PrazoDaReservaProrrogado no outbox")
    void duasAoMesmoTempo() throws Exception {
        String acesso = Reservas.clienteLogado(mvc);
        String id = JsonPath.read(reservar(mvc, acesso, corpo(servico, barbeiro, as(15, 0)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        relogio.definir(em(10, 2, 30));
        int[] codigos = soltarJuntas(List.of(
                () -> prorrogar(mvc, acesso, id).andReturn().getResponse().getStatus(),
                () -> prorrogar(mvc, acesso, id).andReturn().getResponse().getStatus()));

        assertThat(codigos).containsExactlyInAnyOrder(200, 201);
        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 4, 0));
        assertThat(eventosDePrazo(jdbc, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("A14: reserva prorrogada (prazo 10:04:00) e o webhook do Pix às 10:03:30: reserva CONFIRMADO, cobrança PAGA e um só ReservaConfirmada")
    void pagamentoDentroDoPrazoNovo() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(15, 0));
        cobrarPixComoVisitante(mvc, jdbc, reserva);
        String idNoGateway = idDoProvedor(jdbc, reserva.id());
        relogio.definir(em(10, 2, 30));
        prorrogarComoVisitante(mvc, reserva.id(), reserva.token()).andExpect(status().isCreated());
        assertThat(expiraEm(jdbc, reserva.id())).isEqualTo(em(10, 4, 0));

        relogio.definir(em(10, 3, 30));
        gateway.marcarPago(idNoGateway);
        enviarWebhook(mvc, TOKEN_DO_WEBHOOK, corpoDoWebhook("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idNoGateway))
                .andExpect(status().isOk());

        assertThat(status(jdbc, reserva.id())).isEqualTo("CONFIRMADO");
        assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PAGA");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE event_type LIKE '%ReservaConfirmada' AND serialized_event LIKE ?
                """, Long.class, "%" + reserva.id() + "%")).isEqualTo(1);
    }

    @Test
    @DisplayName("A15: prorrogação e webhook ao mesmo tempo (10:02:30), em várias rodadas: o estado final é sempre CONFIRMADO e a prorrogação devolve 201 (veio antes) ou 422 TRANSICAO_INVALIDA (veio depois)")
    void prorrogacaoContraWebhook() throws Exception {
        for (int rodada = 0; rodada < 4; rodada++) {
            relogio.definir(em(10, 0, 0));
            UUID outroBarbeiro = DadosDeTeste.barbeiro(jdbc, true);
            ReservaGuest reserva = reservarComoVisitante(mvc, outroBarbeiro, servico, as(15, 0));
            cobrarPixComoVisitante(mvc, jdbc, reserva);
            String idNoGateway = idDoProvedor(jdbc, reserva.id());
            gateway.marcarPago(idNoGateway);
            relogio.definir(em(10, 2, 30));

            int[] codigos = soltarJuntas(List.of(
                    () -> prorrogarComoVisitante(mvc, reserva.id(), reserva.token()).andReturn().getResponse().getStatus(),
                    () -> enviarWebhook(mvc, TOKEN_DO_WEBHOOK,
                            corpoDoWebhook("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idNoGateway))
                            .andReturn().getResponse().getStatus()));

            assertThat(codigos[1]).as("o webhook sempre responde 200").isEqualTo(200);
            assertThat(codigos[0]).isIn(201, 422);
            assertThat(status(jdbc, reserva.id())).as("nunca AGUARDANDO_PAGAMENTO depois de pago").isEqualTo("CONFIRMADO");
            assertThat(statusDaCobranca(jdbc, reserva.id())).isEqualTo("PAGA");
            if (codigos[0] == 422) {
                prorrogarComoVisitante(mvc, reserva.id(), reserva.token()).andExpect(status().isUnprocessableContent())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                .jsonPath("$.code").value("TRANSICAO_INVALIDA"));
            }
        }
    }

    @Test
    @DisplayName("A16: a prorrogação segura o lock da linha às 10:02:59 e o relógio chega a 10:03:00: o job pula a linha (SKIP LOCKED), depois do commit a reserva segue AGUARDANDO_PAGAMENTO até 10:04:00 e só então o job a expira")
    void jobPulaALinhaTravada() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(15, 0));
        TransactionTemplate fora = new TransactionTemplate(gerenciador);

        relogio.definir(em(10, 2, 59));
        try (ExecutorService outroFio = Executors.newSingleThreadExecutor()) {
            fora.executeWithoutResult(transacao -> {
                // Dentro da transação aberta pelo teste a prorrogação entra nela (propagação REQUIRED): o lock fica preso até o commit.
                prorrogar.doVisitante(UUID.fromString(reserva.id()), reserva.token());
                relogio.definir(em(10, 3, 0));
                try {
                    outroFio.submit(job::executar).get(10, TimeUnit.SECONDS);
                } catch (Exception falha) {
                    throw new IllegalStateException("o job não podia esperar a linha travada", falha);
                }
                assertThat(status(jdbc, reserva.id())).as("o job pulou a linha travada").isEqualTo("AGUARDANDO_PAGAMENTO");
                assertThat(expiraEm(jdbc, reserva.id())).as("o commit ainda não aconteceu").isEqualTo(em(10, 3, 0));
            });
        }

        assertThat(expiraEm(jdbc, reserva.id())).isEqualTo(em(10, 4, 0));
        job.executar();
        assertThat(status(jdbc, reserva.id())).as("10:03:00 com prazo até 10:04:00").isEqualTo("AGUARDANDO_PAGAMENTO");
        relogio.definir(em(10, 4, 0));
        job.executar();
        assertThat(status(jdbc, reserva.id())).isEqualTo("EXPIRADO");
    }
}
