package io.github.k3vinrich4rd.altashoras.admin;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Base dos testes dos cadastros do painel (Fatia 5B). Usa o mesmo contexto e o mesmo PostgreSQL do painel, mas o
 * relógio é o dos critérios de cadastro (terça 17/11/2026, 08:00) e cada teste começa com as tabelas de serviço,
 * barbeiro, jornada e folga VAZIAS (apagadas aqui) e com a configuração nos valores da V1: o A41 conta serviços e o A64
 * lê a configuração inicial. Os outros testes do painel não dependem de linha pré-existente: cada um cria o que usa
 * (massa, DadosDeTeste). A configuração também volta aos valores da V1 depois de cada teste, para o que um teste mudou
 * não vazar para os seguintes. O restante (reservas, cobranças, auditoria) a {@link PainelBase} já esvazia.
 */
public abstract class CadastrosBase extends PainelBase {

    protected static final LocalDate HOJE = LocalDate.of(2026, 11, 17);

    @BeforeEach
    protected void prepararCadastros() {
        jdbc.update("DELETE FROM folga");
        jdbc.update("DELETE FROM jornada");
        jdbc.update("DELETE FROM barbeiro");
        jdbc.update("DELETE FROM servico");
        restaurarConfiguracao();
        relogio.reiniciar();
    }

    @AfterEach
    protected void devolverAConfiguracao() {
        restaurarConfiguracao();
    }

    /** Os valores da V1 (D36), com o prazo de pagamento da V7 (3 min, D121): outro teste do contexto pode ter mudado a configuração. */
    protected void restaurarConfiguracao() {
        jdbc.update("""
                UPDATE configuracao_agenda
                SET intervalo_entre_atendimentos_min = 0, granularidade_min = 30, expiracao_pagamento_min = 3,
                    atualizado_por = NULL, version = 0
                WHERE id = 1
                """);
    }

    protected Conta admin() {
        return conta("ADMIN", "BARBEIRO");
    }

    // ---- HTTP -------------------------------------------------------------------------------------------------------

    protected ResultActions enviar(HttpMethod metodo, String rota, Conta quem, String corpo) throws Exception {
        MockHttpServletRequestBuilder pedido = MockMvcRequestBuilders.request(metodo, rota)
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (quem != null) {
            pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + quem.token());
        }
        return mvc.perform(pedido);
    }

    protected ResultActions put(String rota, Conta quem, String corpo) throws Exception {
        return enviar(HttpMethod.PUT, rota, quem, corpo);
    }

    protected ResultActions postJson(String rota, Conta quem, String corpo) throws Exception {
        return enviar(HttpMethod.POST, rota, quem, corpo);
    }

    protected ResultActions delete(String rota, Conta quem) throws Exception {
        return enviar(HttpMethod.DELETE, rota, quem, "");
    }

    /** POST /agendamentos como cliente logado (o token vem do próprio emissor, com o relógio do teste). */
    protected ResultActions reservar(Conta cliente, UUID servico, UUID barbeiro, OffsetDateTime inicio) throws Exception {
        return postJson("/agendamentos", cliente, """
                { "servicoId": "%s", "barbeiroId": "%s", "inicio": "%s" }
                """.formatted(servico, barbeiro, inicio));
    }

    /** As horas ("07:00") livres do barbeiro no dia, pela rota pública de disponibilidade. */
    protected List<String> horas(UUID servico, UUID barbeiro, LocalDate dia) throws Exception {
        String corpo = mvc.perform(MockMvcRequestBuilders.get("/disponibilidade").param("servicoId", servico.toString())
                .param("data", dia.toString()).param("barbeiroId", barbeiro.toString()))
                .andReturn().getResponse().getContentAsString();
        List<String> horarios = JsonPath.read(corpo, "$.barbeiros[*].horarios[*]");
        return horarios.stream().map(h -> h.substring(11, 16)).toList();
    }

    protected static String json(String modelo, Object... valores) {
        return modelo.formatted(valores);
    }

    protected static String idDe(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id");
    }

    protected UUID servicoComNome(String nome, int duracaoMin, long precoCentavos, boolean ativo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO servico (id, nome, duracao_min, preco_centavos, ativo) VALUES (?, ?, ?, ?, ?)", id, nome,
                duracaoMin, precoCentavos, ativo);
        return id;
    }
}
