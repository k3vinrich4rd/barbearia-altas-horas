package io.github.k3vinrich4rd.altashoras.cep;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

/**
 * O ViaCEP e a BrasilAPI de mentira (E9, A22): um MockRestServiceServer ligado ao construtor de RestClient que os
 * clientes dos provedores recebem. Nenhuma chamada sai para a internet; chamada sem expectativa falha na hora, e
 * {@link #verificar()} falha se uma chamada esperada não aconteceu. Os CEPs são fictícios do roteiro da spec.
 */
public class ProvedoresFalsos {

    public static final String CEP_SE = "01001000";
    public static final String CEP_INEXISTENTE = "99999999";

    public static final String VIACEP_SE = """
            {"cep":"01001-000","logradouro":"Praça da Sé","complemento":"lado ímpar","unidade":"","bairro":"Sé",
             "localidade":"São Paulo","uf":"SP","estado":"São Paulo","regiao":"Sudeste","ibge":"3550308",
             "gia":"1004","ddd":"11","siafi":"7107"}
            """;
    public static final String BRASILAPI_SE = """
            {"cep":"01001000","state":"SP","city":"São Paulo","neighborhood":"Sé","street":"Praça da Sé",
             "service":"viacep"}
            """;
    public static final String VIACEP_ERRO = "{\"erro\": true}";

    private final RestClient.Builder construtor = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(construtor).ignoreExpectOrder(true).build();

    public RestClient.Builder construtor() {
        return construtor;
    }

    /** Espera uma chamada ao ViaCEP para o CEP e responde com o criador dado. */
    public void viaCep(String cep, ResponseCreator resposta) {
        servidor.expect(ExpectedCount.once(), requestTo("http://localhost:1/ws/" + cep + "/json/"))
                .andExpect(method(HttpMethod.GET)).andRespond(resposta);
    }

    /** Espera uma chamada à BrasilAPI para o CEP e responde com o criador dado. */
    public void brasilApi(String cep, ResponseCreator resposta) {
        servidor.expect(ExpectedCount.once(), requestTo("http://localhost:1/api/cep/v1/" + cep))
                .andExpect(method(HttpMethod.GET)).andRespond(resposta);
    }

    /** O conjunto "os dois fora": ViaCEP em timeout e BrasilAPI com 503. */
    public void osDoisFora(String cep) {
        viaCep(cep, timeout());
        brasilApi(cep, erro503());
    }

    public static ResponseCreator json(String corpo) {
        return withSuccess(corpo, MediaType.APPLICATION_JSON);
    }

    public static ResponseCreator erro503() {
        return withServerError();
    }

    public static ResponseCreator naoEncontrado() {
        return withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"nada\"}");
    }

    public static ResponseCreator naoEJson() {
        return withSuccess("<html><body>manutencao</body></html>", MediaType.TEXT_HTML);
    }

    /** O MockRestServiceServer não espera de verdade; o falso lança o mesmo erro que o timeout do JDK lançaria. */
    public static ResponseCreator timeout() {
        return requisicao -> {
            throw new SocketTimeoutException("Read timed out");
        };
    }

    public static ResponseCreator quedaDeRede() {
        return requisicao -> {
            throw new IOException("Connection refused");
        };
    }

    /** ViaCEP para outro CEP, no formato real da resposta. */
    public static String viaCepDe(String cep, String bairro) {
        return "{\"cep\":\"" + cep.substring(0, 5) + "-" + cep.substring(5) + "\",\"logradouro\":\"Rua Fictícia\","
                + "\"bairro\":\"" + bairro + "\",\"localidade\":\"São Paulo\",\"uf\":\"SP\"}";
    }

    public void verificar() {
        servidor.verify();
    }

    /** Confere o que foi esperado até aqui e abre espaço para novas expectativas (o mock não deixa adicionar depois de chamadas). */
    public void recomecar() {
        servidor.verify();
        servidor.reset();
    }

    public void reiniciar() {
        servidor.reset();
    }
}
