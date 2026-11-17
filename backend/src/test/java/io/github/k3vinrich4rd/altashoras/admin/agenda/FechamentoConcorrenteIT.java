package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static org.assertj.core.api.Assertions.assertThat;

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

import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/** A15: dois fechamentos ao mesmo tempo com a mesma version (dois cliques, ou o celular e o computador do Eduardo). */
class FechamentoConcorrenteIT extends PainelBase {

    private static final int REPETICOES = 10;

    @Test
    @DisplayName("A15: dois PATCH simultâneos com a mesma version: exatamente um 200 e um 409 CONFLITO_DE_VERSAO (nunca 500), um só status final e uma só auditoria")
    void doisCliques() throws Exception {
        Massa m = massa();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < REPETICOES; i++) {
                UUID reserva = reserva(m.barbeiroA(), m.servico(), as(0, 0).plusMinutes(30L * i), "CONFIRMADO", null,
                        m.clienteDaR1(), null);
                long v = versaoDa(reserva);
                long auditoriaAntes = linhasDeAuditoria();

                CountDownLatch largada = new CountDownLatch(1);
                Future<MockHttpServletResponse> concluir = pool.submit(() -> {
                    largada.await();
                    return patchStatus(reserva, m.contaA(), corpoDeStatus("CONCLUIDO", v)).andReturn().getResponse();
                });
                Future<MockHttpServletResponse> faltar = pool.submit(() -> {
                    largada.await();
                    return patchStatus(reserva, m.contaA(), corpoDeStatus("NAO_COMPARECEU", v)).andReturn().getResponse();
                });
                largada.countDown();

                List<MockHttpServletResponse> respostas = List.of(concluir.get(), faltar.get());
                assertThat(respostas.stream().filter(r -> r.getStatus() == 200)).hasSize(1);
                MockHttpServletResponse perdedora = respostas.stream().filter(r -> r.getStatus() != 200).findFirst().orElseThrow();
                assertThat(perdedora.getStatus()).isEqualTo(409);
                assertThat((String) JsonPath.read(perdedora.getContentAsString(), "$.code")).isEqualTo("CONFLITO_DE_VERSAO");

                assertThat(statusDaReserva(reserva)).isIn("CONCLUIDO", "NAO_COMPARECEU");
                assertThat(versaoDa(reserva)).isEqualTo(v + 1);
                assertThat(linhasDeAuditoria() - auditoriaAntes).isEqualTo(1);
            }
        }
    }
}
