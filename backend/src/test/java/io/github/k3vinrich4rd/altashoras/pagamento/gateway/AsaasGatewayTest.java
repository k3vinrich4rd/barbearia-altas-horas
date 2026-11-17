package io.github.k3vinrich4rd.altashoras.pagamento.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import io.github.k3vinrich4rd.altashoras.comum.valor.Cpf;

/** O adaptador real do Asaas, provado sem rede: a requisição tem o formato documentado e a resposta vira o modelo interno. */
class AsaasGatewayTest {

    private static final String BASE = "https://api-sandbox.asaas.test/v3";
    private static final Instant EXPIRA_EM = ZonedDateTime
            .of(2026, 11, 17, 8, 15, 0, 0, ZoneId.of("America/Sao_Paulo")).toInstant();

    private MockRestServiceServer servidor;
    private AsaasGateway gateway;

    @BeforeEach
    void montar() {
        RestClient.Builder construtor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(construtor).build();
        gateway = new AsaasGateway(construtor, BASE, "token-do-asaas-so-de-teste", "America/Sao_Paulo");
    }

    private static NovaCobranca pedido(UUID cobrancaId, MetodoPagamento metodo) {
        return new NovaCobranca(cobrancaId, metodo, 5500, Cpf.de("529.982.247-25"), "Visitante Teste", EXPIRA_EM);
    }

    @Test
    @DisplayName("A32: criar (Pix e cartão) e consultar mandam customer, billingType, value, externalReference e o cabeçalho de autenticação; as respostas viram o modelo interno")
    void formatoDasRequisicoes() {
        UUID pix = UUID.randomUUID();
        servidor.expect(requestTo(BASE + "/customers")).andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "token-do-asaas-so-de-teste"))
                .andExpect(jsonPath("$.name").value("Visitante Teste"))
                .andExpect(jsonPath("$.cpfCnpj").value("52998224725"))
                .andRespond(withSuccess("{\"id\":\"cus_001\",\"object\":\"customer\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/payments")).andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "token-do-asaas-so-de-teste"))
                .andExpect(jsonPath("$.customer").value("cus_001"))
                .andExpect(jsonPath("$.billingType").value("PIX"))
                .andExpect(jsonPath("$.value").value(55.0))
                .andExpect(jsonPath("$.dueDate").value("2026-11-17"))
                .andExpect(jsonPath("$.externalReference").value(pix.toString()))
                .andRespond(withSuccess("""
                        {"id":"pay_001","customer":"cus_001","status":"PENDING","billingType":"PIX","value":55.0,
                         "invoiceUrl":"https://sandbox.asaas.com/i/001","campoNovoQueNaoUsamos":true}
                        """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/payments/pay_001/pixQrCode")).andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", "token-do-asaas-so-de-teste"))
                .andRespond(withSuccess("{\"success\":true,\"encodedImage\":\"QUJD\",\"payload\":\"000201PIX\"}",
                        MediaType.APPLICATION_JSON));

        CobrancaNoGateway criada = gateway.criar(pedido(pix, MetodoPagamento.PIX));

        assertThat(criada).isEqualTo(new CobrancaNoGateway("pay_001", "cus_001", "000201PIX", "QUJD", null));
        servidor.verify();
        servidor.reset();

        // Cartão: o link da fatura vira o checkout e o QR do Pix nem é pedido.
        UUID cartao = UUID.randomUUID();
        servidor.expect(requestTo(BASE + "/customers")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"cus_002\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/payments")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.billingType").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.value").value(55.0))
                .andExpect(jsonPath("$.externalReference").value(cartao.toString()))
                .andRespond(withSuccess("""
                        {"id":"pay_002","customer":"cus_002","status":"PENDING","billingType":"CREDIT_CARD",
                         "invoiceUrl":"https://sandbox.asaas.com/i/002"}
                        """, MediaType.APPLICATION_JSON));

        CobrancaNoGateway comCartao = gateway.criar(pedido(cartao, MetodoPagamento.CARTAO_CREDITO));

        assertThat(comCartao).isEqualTo(new CobrancaNoGateway("pay_002", "cus_002", null, null,
                "https://sandbox.asaas.com/i/002"));
        servidor.verify();

        // Consultar: o status do Asaas vira o nosso.
        assertThat(consultar("RECEIVED")).isEqualTo(StatusNoGateway.PAGA);
        assertThat(consultar("CONFIRMED")).isEqualTo(StatusNoGateway.PAGA);
        assertThat(consultar("RECEIVED_IN_CASH")).isEqualTo(StatusNoGateway.PAGA);
        assertThat(consultar("PENDING")).isEqualTo(StatusNoGateway.PENDENTE);
        assertThat(consultar("OVERDUE")).isEqualTo(StatusNoGateway.PENDENTE);
        assertThat(consultar("REFUNDED")).isEqualTo(StatusNoGateway.CANCELADA);
        servidor.verify();
    }

    private StatusNoGateway consultar(String statusDoAsaas) {
        servidor.reset();
        servidor.expect(requestTo(BASE + "/payments/pay_001")).andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", "token-do-asaas-so-de-teste"))
                .andRespond(withSuccess("{\"id\":\"pay_001\",\"status\":\"" + statusDoAsaas + "\"}",
                        MediaType.APPLICATION_JSON));
        return gateway.consultar("pay_001");
    }

    @Test
    @DisplayName("buscarPorReferencia: GET /payments?externalReference= devolve o primeiro de data[]; lista vazia = nada")
    void buscaPorReferencia() {
        UUID id = UUID.randomUUID();
        servidor.expect(requestTo(BASE + "/payments?externalReference=" + id)).andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", "token-do-asaas-so-de-teste"))
                .andRespond(withSuccess("""
                        {"object":"list","data":[{"id":"pay_009","customer":"cus_009","status":"PENDING","billingType":"PIX"}]}
                        """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/payments/pay_009/pixQrCode"))
                .andRespond(withSuccess("{\"encodedImage\":\"QUJD\",\"payload\":\"000201\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.buscarPorReferencia(id))
                .contains(new CobrancaNoGateway("pay_009", "cus_009", "000201", "QUJD", null));
        servidor.verify();
        servidor.reset();

        servidor.expect(requestTo(BASE + "/payments?externalReference=" + id))
                .andRespond(withSuccess("{\"object\":\"list\",\"data\":[]}", MediaType.APPLICATION_JSON));
        assertThat(gateway.buscarPorReferencia(id)).isEmpty();
        servidor.verify();
    }

    @Test
    @DisplayName("erro 5xx ou falha de rede do Asaas viram GatewayIndisponivelException (503), sem corpo nem dado pessoal")
    void falhaViraIndisponivel() {
        servidor.expect(requestTo(BASE + "/customers")).andRespond(withServerError());
        assertThatThrownBy(() -> gateway.criar(pedido(UUID.randomUUID(), MetodoPagamento.PIX)))
                .isInstanceOf(GatewayIndisponivelException.class)
                .hasMessageNotContaining("52998224725");
        servidor.reset();

        servidor.expect(requestTo(BASE + "/payments/pay_001"))
                .andRespond(request -> {
                    throw new IOException("timeout simulado");
                });
        assertThatThrownBy(() -> gateway.consultar("pay_001")).isInstanceOf(GatewayIndisponivelException.class);
    }

    @Test
    @DisplayName("o toString do pedido não imprime CPF nem nome do pagador")
    void pedidoNaoVazaDadoPessoal() {
        String texto = pedido(UUID.randomUUID(), MetodoPagamento.PIX).toString();
        assertThat(texto).doesNotContain("52998224725").doesNotContain("529.982").doesNotContain("Visitante");
    }
}
