package io.github.k3vinrich4rd.altashoras.conta.convite;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.SENHA;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.emailNovo;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.json;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/**
 * Começo comum dos testes do convite do barbeiro (Fatia 10B). Aproveita o contexto e a massa do {@link PainelBase}: terça
 * 01/12/2026, 09:30, tabelas de reserva e auditoria vazias. Cada teste cria o próprio barbeiro sem conta (com a jornada
 * real) e o dono é uma conta de verdade com ADMIN e BARBEIRO. O código do convite só existe no corpo da resposta do dono e
 * nas variáveis do teste. Contas, e-mails e IPs são fictícios ({@code @exemplo.test}, 10.x.x.x).
 */
public abstract class ConviteBase extends PainelBase {

    @AfterEach
    protected void limparConvites() {
        jdbc.update("DELETE FROM convite_conta");
    }

    protected UUID barbeiroSemConta() {
        return DadosDeTeste.barbeiro(jdbc, true);
    }

    protected Conta dono() {
        return conta("ADMIN", "BARBEIRO");
    }

    protected ResultActions emitir(Conta quem, UUID barbeiro) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/admin/barbeiros/" + barbeiro + "/convite")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + quem.token()));
    }

    protected ResultActions revogar(Conta quem, UUID barbeiro) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.delete("/admin/barbeiros/" + barbeiro + "/convite")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + quem.token()));
    }

    /** Gera o convite e devolve só o código (o corpo certo do 201 já é conferido). */
    protected String gerarCodigo(Conta dono, UUID barbeiro) throws Exception {
        String corpo = emitir(dono, barbeiro).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.codigo");
    }

    protected static String corpoDeAceite(String codigo, String email, String senha) {
        return senha == null
                ? "{ \"codigo\": \"%s\", \"email\": \"%s\" }".formatted(codigo, email)
                : "{ \"codigo\": \"%s\", \"email\": \"%s\", \"senha\": \"%s\" }".formatted(codigo, email, senha);
    }

    /** O aceite, de um IP novo (o limite por IP de um teste não atrapalha o outro) e sem login. */
    protected ResultActions aceitar(String codigo, String email, String senha) throws Exception {
        return mvc.perform(json(MockMvcRequestBuilders.post("/auth/convite"), corpoDeAceite(codigo, email, senha)));
    }

    /** O aceite de quem está logado numa conta (o token vai no cabeçalho, como o BFF faz). */
    protected ResultActions aceitarLogado(String token, String codigo, String email, String senha) throws Exception {
        MockHttpServletRequestBuilder pedido = json(MockMvcRequestBuilders.post("/auth/convite"),
                corpoDeAceite(codigo, email, senha)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mvc.perform(pedido);
    }

    protected String emailDoBarbeiro() {
        return "barbeiro." + UUID.randomUUID().toString().substring(0, 12) + "@exemplo.test";
    }

    /** Cria o convite, aceita com e-mail e senha novos e devolve o e-mail (a conta nasce ativa e ligada ao barbeiro). */
    protected String convidarEAceitar(Conta dono, UUID barbeiro) throws Exception {
        String email = emailDoBarbeiro();
        aceitar(gerarCodigo(dono, barbeiro), email, SENHA).andExpect(status().isNoContent());
        return email;
    }

    protected String tokenDe(String email) throws Exception {
        return ContaDeTeste.entrar(mvc, email, SENHA).acesso();
    }

    protected UUID usuarioDoBarbeiro(UUID barbeiro) {
        return jdbc.queryForObject("SELECT usuario_id FROM barbeiro WHERE id = ?", UUID.class, barbeiro);
    }

    /** Desliga o barbeiro pelo PUT do painel (E10): lê a versão e o nome do banco, como a tela faria. */
    protected ResultActions desligar(Conta dono, UUID barbeiro) throws Exception {
        return alterarAtivo(dono, barbeiro, false);
    }

    protected ResultActions reativar(Conta dono, UUID barbeiro) throws Exception {
        return alterarAtivo(dono, barbeiro, true);
    }

    private ResultActions alterarAtivo(Conta dono, UUID barbeiro, boolean ativo) throws Exception {
        var linha = jdbc.queryForMap("SELECT nome_exibicao, version, usuario_id FROM barbeiro WHERE id = ?", barbeiro);
        String usuario = linha.get("usuario_id") == null ? "" : ", \"usuarioId\": \"" + linha.get("usuario_id") + "\"";
        String corpo = "{ \"nomeExibicao\": \"%s\", \"ativo\": %s, \"version\": %s%s }".formatted(linha.get("nome_exibicao"),
                ativo, linha.get("version"), usuario);
        return mvc.perform(MockMvcRequestBuilders.put("/admin/barbeiros/" + barbeiro)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dono.token()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    /** Um cliente cadastrado pela API, sem CEP (nenhum teste daqui fala com provedor de endereço). */
    protected ContaDeTeste.Conta novoCliente() throws Exception {
        String email = emailNovo();
        String corpo = mvc.perform(json(MockMvcRequestBuilders.post("/auth/cadastro"), """
                { "nome": "Cliente Teste", "email": "%s", "whatsapp": "(11) 90000-0001", "senha": "%s" }
                """.formatted(email, SENHA))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new ContaDeTeste.Conta(JsonPath.read(corpo, "$.id"), email, SENHA);
    }

    protected String clienteCadastrado() throws Exception {
        return novoCliente().email();
    }

    protected static String novoEmailDeCliente() {
        return emailNovo();
    }
}
