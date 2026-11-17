package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.clienteLogado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.AsaasGateway;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.GatewayDePagamento;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.GatewaySimulado;

/** O outro lado do A45: com {@code app.pagamento.modo=asaas} a confirmação simulada não existe (404). */
@SpringBootTest(properties = "app.pagamento.modo=asaas")
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class SimulacaoDesligadaIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext contexto;

    @Test
    @DisplayName("A45: com app.pagamento.modo=asaas a rota de confirmação simulada não existe → 404, e o gateway ativo é o do Asaas")
    void rotaNaoExisteForaDoModoSimulado() throws Exception {
        assertThat(contexto.getBeansOfType(GatewayDePagamento.class).values()).singleElement()
                .isInstanceOf(AsaasGateway.class);
        assertThat(contexto.getBeansOfType(GatewaySimulado.class)).isEmpty();

        String acesso = clienteLogado(mvc);
        mvc.perform(post("/pagamentos/simulacao/" + UUID.randomUUID() + "/confirmar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + acesso))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        mvc.perform(post("/pagamentos/simulacao/" + UUID.randomUUID() + "/confirmar"))
                .andExpect(status().isNotFound());
    }
}
