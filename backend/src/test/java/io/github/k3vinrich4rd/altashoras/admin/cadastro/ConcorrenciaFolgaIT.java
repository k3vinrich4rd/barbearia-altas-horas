package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.CadastrosBase;

/**
 * A62 (Fatia 5) e A21 (Fatia 15): a corrida entre criar ou editar uma folga (ou trocar a jornada) e reservar o mesmo horário. Nada disso tem constraint no
 * banco como a reserva; o que impede os dois de passarem é a trava da linha do barbeiro (FOR UPDATE no painel, FOR SHARE
 * na reserva) e a releitura de tudo depois dela (E21, D80).
 */
class ConcorrenciaFolgaIT extends CadastrosBase {

    private static final int REPETICOES = 50;

    private String code(MockHttpServletResponse resposta) throws Exception {
        return JsonPath.read(resposta.getContentAsString(), "$.code");
    }

    @AfterEach
    void removerAtrasos() {
        jdbc.execute("DROP FUNCTION IF EXISTS atraso_do_teste() CASCADE");
    }

    /**
     * Sem isto a janela da corrida mede milissegundos e o teste passaria mesmo sem a trava. Um {@code pg_sleep} antes de
     * gravar empurra os dois lados para dentro da janela: quem chega enquanto o outro ainda grava só não passa se a
     * trava estiver de pé. Tudo é removido no {@link #removerAtrasos()}.
     */
    private void atrasarInserts(String tabela, double segundos) {
        jdbc.execute("DROP TRIGGER IF EXISTS atraso_" + tabela + " ON " + tabela);
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION atraso_do_teste() RETURNS trigger AS $$
                BEGIN PERFORM pg_sleep(TG_ARGV[0]::float8); RETURN NEW; END $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER atraso_" + tabela + " BEFORE INSERT ON " + tabela
                + " FOR EACH ROW EXECUTE FUNCTION atraso_do_teste('" + segundos + "')");
    }

    @Test
    @DisplayName("A62: folga que cobre 15:00 e reserva às 15:00 ao mesmo tempo (50 vezes): ou folga 201 e reserva 422 FORA_DO_EXPEDIENTE, ou reserva 201 e folga 422 FOLGA_COM_RESERVAS; nunca os dois 201, nunca 500, e cada lado vence ao menos uma vez")
    void folgaContraReserva() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        Conta admin = admin();
        Conta cliente = conta("CLIENTE");
        String rotaDaFolga = "/admin/barbeiros/" + barbeiro + "/folgas";
        String corpoDaFolga = """
                { "inicio": "%s", "fim": "%s", "motivo": "Teste" }
                """.formatted(as(14, 30), as(15, 30));

        int folgaVenceu = 0;
        int reservaVenceu = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            atrasarInserts("folga", 0.04);
            atrasarInserts("reserva", 0.04);
            for (int i = 0; i < REPETICOES; i++) {
                // Nas rodadas ímpares a folga larga 60 ms depois: assim cada lado chega primeiro de vez em quando.
                long atrasoDaFolga = i % 2 == 0 ? 0 : 60;
                CountDownLatch largada = new CountDownLatch(1);
                Future<MockHttpServletResponse> folga = pool.submit(() -> {
                    largada.await();
                    Thread.sleep(atrasoDaFolga);
                    return postJson(rotaDaFolga, admin, corpoDaFolga).andReturn().getResponse();
                });
                Future<MockHttpServletResponse> reserva = pool.submit(() -> {
                    largada.await();
                    return reservar(cliente, servico, barbeiro, as(15, 0)).andReturn().getResponse();
                });
                largada.countDown();
                MockHttpServletResponse daFolga = folga.get();
                MockHttpServletResponse daReserva = reserva.get();

                if (daFolga.getStatus() == 201) {
                    assertThat(daReserva.getStatus()).as("repetição %d: folga criada, reserva tem de recusar", i).isEqualTo(422);
                    assertThat(code(daReserva)).isEqualTo("FORA_DO_EXPEDIENTE");
                    folgaVenceu++;
                } else {
                    assertThat(daFolga.getStatus()).as("repetição %d: folga recusada", i).isEqualTo(422);
                    assertThat(code(daFolga)).isEqualTo("FOLGA_COM_RESERVAS");
                    assertThat(daReserva.getStatus()).as("repetição %d: reserva criada", i).isEqualTo(201);
                    reservaVenceu++;
                }
                // Nunca existem folga e reserva ativa no mesmo horário.
                long folgas = jdbc.queryForObject("SELECT count(*) FROM folga WHERE barbeiro_id = ?", Long.class, barbeiro);
                long reservas = jdbc.queryForObject("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", Long.class, barbeiro);
                assertThat(folgas + reservas).as("repetição %d: um dos dois, nunca os dois", i).isEqualTo(1);

                jdbc.update("DELETE FROM reserva WHERE barbeiro_id = ?", barbeiro);
                jdbc.update("DELETE FROM folga WHERE barbeiro_id = ?", barbeiro);
            }
        }
        assertThat(folgaVenceu + reservaVenceu).isEqualTo(REPETICOES);
        assertThat(folgaVenceu).as("a folga venceu ao menos uma vez").isPositive();
        assertThat(reservaVenceu).as("a reserva venceu ao menos uma vez").isPositive();
    }

    @Test
    @DisplayName("A62: jornada trocada que tira a tarde, enquanto uma reserva das 15:00 espera a trava, recusa a reserva com 422 FORA_DO_EXPEDIENTE (a reserva relê a jornada depois da trava)")
    void jornadaContraReserva() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        Conta admin = admin();
        Conta cliente = conta("CLIENTE");
        atrasarInserts("jornada", 0.6);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            // A troca segura a trava do barbeiro por ~0,6 s (o insert da jornada demora); a reserva chega no meio.
            Future<MockHttpServletResponse> troca = pool.submit(() -> put("/admin/barbeiros/" + barbeiro + "/jornada", admin,
                    "[{\"diaSemana\": 2, \"horaInicio\": \"07:00\", \"horaFim\": \"12:00\"}]").andReturn().getResponse());
            Thread.sleep(200);
            MockHttpServletResponse reserva = reservar(cliente, servico, barbeiro, as(15, 0)).andReturn().getResponse();

            assertThat(troca.get().getStatus()).isEqualTo(200);
            assertThat(reserva.getStatus()).isEqualTo(422);
            assertThat(code(reserva)).isEqualTo("FORA_DO_EXPEDIENTE");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", Long.class, barbeiro)).isZero();
    }

    /** O PUT da Fatia 15 grava com UPDATE: o atraso vai no BEFORE UPDATE da folga. */
    private void atrasarUpdates(String tabela, double segundos) {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION atraso_do_teste() RETURNS trigger AS $$
                BEGIN PERFORM pg_sleep(TG_ARGV[0]::float8); RETURN NEW; END $$ LANGUAGE plpgsql
                """);
        jdbc.execute("DROP TRIGGER IF EXISTS atraso_upd_" + tabela + " ON " + tabela);
        jdbc.execute("CREATE TRIGGER atraso_upd_" + tabela + " BEFORE UPDATE ON " + tabela
                + " FOR EACH ROW EXECUTE FUNCTION atraso_do_teste('" + segundos + "')");
    }

    @Test
    @DisplayName("A21: um PUT que passa a cobrir 15:00 e uma reserva às 15:00 ao mesmo tempo (50 vezes): ou PUT 200 e reserva 422 FORA_DO_EXPEDIENTE, ou reserva 201 e PUT 422 FOLGA_COM_RESERVAS; nunca as duas gravadas, nunca 500, e cada lado vence ao menos uma vez")
    void edicaoDaFolgaContraReserva() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        Conta admin = admin();
        Conta cliente = conta("CLIENTE");
        String rotaDaFolga = "/admin/barbeiros/" + barbeiro + "/folgas/";
        String corpoDoPut = """
                { "inicio": "%s", "fim": "%s", "tipo": "FOLGA" }
                """.formatted(as(14, 0), as(15, 30));

        int putVenceu = 0;
        int reservaVenceu = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            atrasarUpdates("folga", 0.04);
            atrasarInserts("reserva", 0.04);
            for (int i = 0; i < REPETICOES; i++) {
                // A folga começa só das 14:00 às 14:30: as 15:00 estão livres até o PUT.
                UUID folgaId = UUID.randomUUID();
                jdbc.update("INSERT INTO folga (id, barbeiro_id, inicio, fim) VALUES (?, ?, ?, ?)", folgaId, barbeiro,
                        as(14, 0), as(14, 30));
                long atrasoDoPut = i % 2 == 0 ? 0 : 60;
                CountDownLatch largada = new CountDownLatch(1);
                Future<MockHttpServletResponse> edicao = pool.submit(() -> {
                    largada.await();
                    Thread.sleep(atrasoDoPut);
                    return put(rotaDaFolga + folgaId, admin, corpoDoPut).andReturn().getResponse();
                });
                Future<MockHttpServletResponse> reserva = pool.submit(() -> {
                    largada.await();
                    return reservar(cliente, servico, barbeiro, as(15, 0)).andReturn().getResponse();
                });
                largada.countDown();
                MockHttpServletResponse doPut = edicao.get();
                MockHttpServletResponse daReserva = reserva.get();

                if (doPut.getStatus() == 200) {
                    assertThat(daReserva.getStatus()).as("repetição %d: folga aumentada, reserva tem de recusar", i).isEqualTo(422);
                    assertThat(code(daReserva)).isEqualTo("FORA_DO_EXPEDIENTE");
                    putVenceu++;
                } else {
                    assertThat(doPut.getStatus()).as("repetição %d: PUT recusado", i).isEqualTo(422);
                    assertThat(code(doPut)).isEqualTo("FOLGA_COM_RESERVAS");
                    assertThat(daReserva.getStatus()).as("repetição %d: reserva criada", i).isEqualTo(201);
                    reservaVenceu++;
                }
                long cobre = jdbc.queryForObject("SELECT count(*) FROM folga WHERE id = ? AND fim > ?", Long.class, folgaId,
                        as(15, 0));
                long reservas = jdbc.queryForObject("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", Long.class, barbeiro);
                assertThat(cobre + reservas).as("repetição %d: um dos dois, nunca os dois", i).isEqualTo(1);

                jdbc.update("DELETE FROM reserva WHERE barbeiro_id = ?", barbeiro);
                jdbc.update("DELETE FROM folga WHERE barbeiro_id = ?", barbeiro);
            }
        }
        assertThat(putVenceu + reservaVenceu).isEqualTo(REPETICOES);
        assertThat(putVenceu).as("o PUT venceu ao menos uma vez").isPositive();
        assertThat(reservaVenceu).as("a reserva venceu ao menos uma vez").isPositive();
    }
}
