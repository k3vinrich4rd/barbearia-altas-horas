package io.github.k3vinrich4rd.altashoras;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Cria contas e sessões fictícias pela própria API. Cada chamada usa um IP diferente, para o limite de
 * tentativas de um teste não atrapalhar o outro. */
public final class ContaDeTeste {

    public static final String SENHA = "Senha-de-teste-123";

    public record Conta(String id, String email, String senha) {
    }

    public record Sessao(String acesso, String refresh, String corpo) {
    }

    private ContaDeTeste() {
    }

    public static String ipNovo() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(1, 250) + "." + r.nextInt(1, 250) + "." + r.nextInt(1, 250);
    }

    public static RequestPostProcessor ip(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    public static String emailNovo() {
        return "cliente." + UUID.randomUUID().toString().substring(0, 12) + "@exemplo.test";
    }

    public static String corpoDeCadastro(String email, String senha) {
        return """
                { "nome": "Cliente Teste", "email": "%s", "whatsapp": "(11) 90000-0001", "senha": "%s", "cep": "01001-000" }
                """.formatted(email, senha);
    }

    public static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder pedido, String corpo) {
        return pedido.contentType(MediaType.APPLICATION_JSON).content(corpo).with(ip(ipNovo()));
    }

    public static Conta cadastrar(MockMvc mvc) throws Exception {
        String email = emailNovo();
        String corpo = mvc.perform(json(post("/auth/cadastro"), corpoDeCadastro(email, SENHA)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Conta(JsonPath.read(corpo, "$.id"), email, SENHA);
    }

    public static String corpoDeLogin(String email, String senha) {
        return """
                { "email": "%s", "senha": "%s" }
                """.formatted(email, senha);
    }

    public static Sessao entrar(MockMvc mvc, String email, String senha) throws Exception {
        String corpo = mvc.perform(json(post("/auth/login"), corpoDeLogin(email, senha)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Sessao(JsonPath.read(corpo, "$.accessToken"), JsonPath.read(corpo, "$.refreshToken"), corpo);
    }

    public static String corpoDeRefresh(String refresh) {
        return """
                { "refreshToken": "%s" }
                """.formatted(refresh);
    }
}
