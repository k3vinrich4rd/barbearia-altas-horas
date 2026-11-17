package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.as;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class ReservaConcorrenteIT {

    private static final int CLIENTES = 10;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("A9: 10 clientes no mesmo horário ao mesmo tempo → exatamente um 201, os outros 409, nunca 500")
    void disputa() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        List<String> acessos = new ArrayList<>();
        for (int i = 0; i < CLIENTES; i++) {
            acessos.add(Reservas.clienteLogado(mvc));
        }

        CountDownLatch largada = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> respostas = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(CLIENTES)) {
            for (String acesso : acessos) {
                respostas.add(pool.submit(() -> {
                    largada.await();
                    return Reservas.reservar(mvc, acesso, corpo(servico, barbeiro, as(10, 0))).andReturn().getResponse();
                }));
            }
            largada.countDown();

            int criadas = 0;
            for (Future<MockHttpServletResponse> futura : respostas) {
                MockHttpServletResponse resposta = futura.get();
                if (resposta.getStatus() == 201) {
                    criadas++;
                } else {
                    assertThat(resposta.getStatus()).isEqualTo(409);
                    String code = JsonPath.read(resposta.getContentAsString(), "$.code");
                    assertThat(code).isIn("HORARIO_INDISPONIVEL", "CONFLITO_CONCORRENTE");
                }
            }
            assertThat(criadas).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", Long.class, barbeiro))
                .isEqualTo(1L);
    }
}
