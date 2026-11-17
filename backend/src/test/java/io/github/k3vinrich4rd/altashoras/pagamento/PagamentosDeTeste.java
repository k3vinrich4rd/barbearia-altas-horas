package io.github.k3vinrich4rd.altashoras.pagamento;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/** Atalhos dos testes de pagamento. "Agora" é terça 17/11/2026 08h00; a terça 01/12/2026 é o dia de teste. */
public final class PagamentosDeTeste {

    public static final LocalDate TERCA = LocalDate.of(2026, 12, 1);
    /** CPF válido de teste (gerado só para teste; não é de ninguém). */
    public static final String CPF = "529.982.247-25";
    public static final String CPF_SO_DIGITOS = "52998224725";
    public static final String TOKEN_DO_WEBHOOK = "token-do-webhook-so-de-teste";

    /** Reserva criada pelo visitante: o id e o token que só aparece na criação. */
    public record ReservaGuest(String id, String token) {
    }

    private PagamentosDeTeste() {
    }

    public static OffsetDateTime as(int hora, int minuto) {
        return DadosDeTeste.em(TERCA, hora, minuto);
    }

    public static String corpoDaReservaGuest(UUID servico, UUID barbeiro, OffsetDateTime inicio) {
        return """
                { "servicoId": "%s", "barbeiroId": "%s", "inicio": "%s",
                  "nome": "Visitante Teste", "email": "Visitante@Exemplo.test", "whatsapp": "(11) 90000-0002" }
                """.formatted(servico, barbeiro, inicio);
    }

    public static ReservaGuest reservarComoVisitante(MockMvc mvc, UUID barbeiro, UUID servico, OffsetDateTime inicio)
            throws Exception {
        String corpo = mvc.perform(post("/agendamentos/guest").contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaReservaGuest(servico, barbeiro, inicio)).with(ContaDeTeste.ip(ContaDeTeste.ipNovo())))
                .andReturn().getResponse().getContentAsString();
        return new ReservaGuest(JsonPath.read(corpo, "$.id"), JsonPath.read(corpo, "$.tokenGuest"));
    }

    /** Conta nova e logada; devolve o access token. */
    public static String clienteLogado(MockMvc mvc) throws Exception {
        ContaDeTeste.Conta conta = ContaDeTeste.cadastrar(mvc);
        return ContaDeTeste.entrar(mvc, conta.email(), conta.senha()).acesso();
    }

    public static String reservarComoCliente(MockMvc mvc, String acesso, UUID barbeiro, UUID servico, OffsetDateTime inicio)
            throws Exception {
        String corpo = mvc.perform(post("/agendamentos").header(HttpHeaders.AUTHORIZATION, "Bearer " + acesso)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "servicoId": "%s", "barbeiroId": "%s", "inicio": "%s" }
                                """.formatted(servico, barbeiro, inicio)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.id");
    }

    public static int versaoVigente(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT max(versao) FROM politica_pagamento", Integer.class);
    }

    /** Corpo completo e válido: aceite verdadeiro na versão vigente. */
    public static String corpoDaCobranca(String reservaId, String metodo, String cpf, int versao) {
        return """
                { "reservaId": "%s", "metodo": "%s", "cpf": "%s", "aceitePolitica": true, "versaoPolitica": %d }
                """.formatted(reservaId, metodo, cpf, versao);
    }

    public static ResultActions cobrarComoVisitante(MockMvc mvc, ReservaGuest reserva, String chave, String corpo)
            throws Exception {
        return mvc.perform(post("/pagamentos/cobrancas").contentType(MediaType.APPLICATION_JSON)
                .header("X-Guest-Token", reserva.token()).header("X-Idempotency-Key", chave).content(corpo));
    }

    public static ResultActions cobrarComoCliente(MockMvc mvc, String acesso, String chave, String corpo) throws Exception {
        return mvc.perform(post("/pagamentos/cobrancas").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + acesso).header("X-Idempotency-Key", chave).content(corpo));
    }

    public static String chaveNova() {
        return UUID.randomUUID().toString();
    }

    /** Cria a cobrança Pix do visitante com tudo certo e devolve o corpo da resposta. */
    public static String cobrarPixComoVisitante(MockMvc mvc, JdbcTemplate jdbc, ReservaGuest reserva) throws Exception {
        return cobrarComoVisitante(mvc, reserva, chaveNova(),
                corpoDaCobranca(reserva.id(), "PIX", CPF, versaoVigente(jdbc)))
                .andReturn().getResponse().getContentAsString();
    }

    public static String idDoProvedor(JdbcTemplate jdbc, String reservaId) {
        return jdbc.queryForObject("SELECT id_cobranca_provedor FROM cobranca WHERE reserva_id = ?::uuid", String.class,
                reservaId);
    }

    public static String statusDaCobranca(JdbcTemplate jdbc, String reservaId) {
        return jdbc.queryForObject("SELECT status FROM cobranca WHERE reserva_id = ?::uuid", String.class, reservaId);
    }

    public static String statusDaReserva(JdbcTemplate jdbc, String reservaId) {
        return jdbc.queryForObject("SELECT status FROM reserva WHERE id = ?::uuid", String.class, reservaId);
    }

    public static String corpoDoWebhook(String idEvento, String evento, String idPagamento) {
        return """
                { "id": "%s", "event": "%s", "dateCreated": "2026-11-17 08:00:00",
                  "payment": { "object": "payment", "id": "%s", "status": "RECEIVED", "value": 55.00 } }
                """.formatted(idEvento, evento, idPagamento);
    }

    public static ResultActions enviarWebhook(MockMvc mvc, String token, String corpo) throws Exception {
        var pedido = post("/pagamentos/webhook").contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (token != null) {
            pedido = pedido.header("asaas-access-token", token);
        }
        return mvc.perform(pedido);
    }

    /** Espera curta pelo listener do outbox, que roda depois do commit (@ApplicationModuleListener). */
    public static void esperar(java.util.function.BooleanSupplier condicao) throws InterruptedException {
        for (int i = 0; i < 100 && !condicao.getAsBoolean(); i++) {
            Thread.sleep(100);
        }
        org.assertj.core.api.Assertions.assertThat(condicao.getAsBoolean()).as("condição em até 10 s").isTrue();
    }
}
