package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.em;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.eventosDePrazo;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.expiraEm;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogacoes;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogadaEm;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogar;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogarSemToken;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.status;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.versao;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.as;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpo;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpoGuest;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.reservar;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.reservarComoVisitante;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste;

/**
 * A1 a A3, A5, A9 a A11 e A17 da Fatia 14A ("Acrescentar 1 minuto", atualizados para a regra da Fatia 15: 120 s por
 * pedido, até 10, ADR 0015) e A34 a A39 da Fatia 15 ("mais tempo para pagar"). Reserva de cliente criada às 10:00:00 de ter
 * 01/12/2026 (horário 15:00), prazo de 3 minutos (expiraEm 10:03:00). Os tokens saem do EmissorDeToken com o relógio já em
 * 10:00:00 (o JWT usa o mesmo Clock), então valem até 10:15.
 */
@Import(GatewayFalsoDeTeste.class)
class ProrrogacaoDoPagamentoIT extends PainelBase {

    private UUID barbeiro;
    private UUID servico;
    private Conta cliente;

    @BeforeEach
    void cenario() {
        relogio.definir(em(10, 0, 0));
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, 30);
        cliente = conta("CLIENTE");
    }

    /** Reserva do cliente do teste, num horário só dela, criada no instante atual do relógio. */
    private String reservaDoCliente(int hora) throws Exception {
        String resposta = reservar(mvc, cliente.token(), corpo(servico, barbeiro, as(hora, 0)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.id");
    }

    private void inserirReservaManual(UUID id) {
        jdbc.update("""
                INSERT INTO reserva (id, barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos)
                VALUES (?, ?, ?, 'MANUAL', ?, ?, ?, 'CONFIRMADO', 5500)
                """, id, barbeiro, servico, as(18, 0), as(18, 30), as(18, 30));
    }

    @Test
    @DisplayName("A1: com o contador em 0:30 (10:02:30) acrescentar dá 201, expiraEm 10:05:00 (120 s desde a Fatia 15, ADR 0015), prorrogacao.usada = true, prorrogada_em = 10:02:30 e version + 1")
    void acrescentaNoUltimoMinuto() throws Exception {
        String id = reservaDoCliente(15);
        long antes = versao(jdbc, id);
        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 3, 0));

        relogio.definir(em(10, 2, 30));
        prorrogar(mvc, cliente.token(), id).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.expiraEm").value("2026-12-01T10:05:00-03:00"))
                .andExpect(jsonPath("$.prorrogacao.usada").value(true))
                .andExpect(jsonPath("$.prorrogacao.segundos").value(120))
                .andExpect(jsonPath("$.prorrogacao.abreEm").value("2026-12-01T10:04:00-03:00"))
                .andExpect(jsonPath("$.prorrogacao.fechaEm").value("2026-12-01T10:05:00-03:00"));

        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 5, 0));
        assertThat(prorrogadaEm(jdbc, id)).isEqualTo(em(10, 2, 30));
        assertThat(versao(jdbc, id)).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("A2: às 10:01:59 (o último minuto começa às 10:02:00) acrescentar dá 422 PRORROGACAO_FORA_DA_JANELA e nada muda")
    void antesDoUltimoMinuto() throws Exception {
        String id = reservaDoCliente(15);
        long antes = versao(jdbc, id);

        relogio.definir(em(10, 1, 59));
        prorrogar(mvc, cliente.token(), id).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRORROGACAO_FORA_DA_JANELA"))
                .andExpect(jsonPath("$.title").value("Fora da janela de prorrogação"));

        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 3, 0));
        assertThat(prorrogadaEm(jdbc, id)).isNull();
        assertThat(versao(jdbc, id)).isEqualTo(antes);

        relogio.definir(em(10, 2, 0));
        prorrogar(mvc, cliente.token(), id).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A3: acrescentar de novo às 10:02:40 com a mesma X-Idempotency-Key (Fatia 15, E21) dá 200 com o mesmo corpo, expiraEm continua 10:05:00 (não 10:07:00) e nenhum evento novo")
    void repeticaoNaoSomaDeNovo() throws Exception {
        String id = reservaDoCliente(15);
        String chave = UUID.randomUUID().toString();
        relogio.definir(em(10, 2, 30));
        String primeiro = prorrogar(mvc, cliente.token(), id, chave).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long versao = versao(jdbc, id);
        assertThat(eventosDePrazo(jdbc, id)).isEqualTo(1);

        relogio.definir(em(10, 2, 40));
        String segundo = prorrogar(mvc, cliente.token(), id, chave).andExpect(status().isOk())
                .andExpect(jsonPath("$.expiraEm").value("2026-12-01T10:05:00-03:00"))
                .andReturn().getResponse().getContentAsString();

        assertThat(segundo).isEqualTo(primeiro);
        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 5, 0));
        assertThat(versao(jdbc, id)).isEqualTo(versao);
        assertThat(eventosDePrazo(jdbc, id)).as("nenhum evento novo").isEqualTo(1);
    }

    @Test
    @DisplayName("A5: às 10:03:00 (o job não passou) e com a reserva já EXPIRADO às 10:03:30 acrescentar dá 422 RESERVA_EXPIRADA nos dois, sem carência nem reabertura")
    void depoisDoFimNaoHaNada() throws Exception {
        String aguardando = reservaDoCliente(15);
        String expirada = reservaDoCliente(16);
        jdbc.update("UPDATE reserva SET status = 'EXPIRADO' WHERE id = ?::uuid", expirada);

        relogio.definir(em(10, 3, 0));
        prorrogar(mvc, cliente.token(), aguardando).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RESERVA_EXPIRADA"));
        relogio.definir(em(10, 3, 30));
        prorrogar(mvc, cliente.token(), expirada).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RESERVA_EXPIRADA"));

        assertThat(status(jdbc, aguardando)).isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(status(jdbc, expirada)).isEqualTo("EXPIRADO");
        assertThat(expiraEm(jdbc, aguardando)).isEqualTo(em(10, 3, 0));
        assertThat(prorrogadaEm(jdbc, aguardando)).isNull();
        assertThat(prorrogadaEm(jdbc, expirada)).isNull();
    }

    @Test
    @DisplayName("A9: a reserva já prorrogada (prazo 10:05:00) que expirou, às 10:05:20, dá 422 RESERVA_EXPIRADA: depois do fim não há outro pedido")
    void naoHaSegundaVez() throws Exception {
        String id = reservaDoCliente(15);
        relogio.definir(em(10, 2, 30));
        prorrogar(mvc, cliente.token(), id).andExpect(status().isCreated());
        long versao = versao(jdbc, id);

        relogio.definir(em(10, 5, 20));
        prorrogar(mvc, cliente.token(), id).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RESERVA_EXPIRADA"));

        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 5, 0));
        assertThat(versao(jdbc, id)).isEqualTo(versao);
        assertThat(eventosDePrazo(jdbc, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("A10: reserva CONFIRMADO, CANCELADO ou CONCLUIDO dá 422 TRANSICAO_INVALIDA nas três e nada muda")
    void soAguardandoPagamentoGanhaTempo() throws Exception {
        String confirmada = reservaDoCliente(15);
        String cancelada = reservaDoCliente(16);
        String concluida = reservaDoCliente(17);
        jdbc.update("""
                UPDATE reserva SET status = 'CONFIRMADO', expira_em = NULL, aceite_politica_versao = 1,
                                   aceite_politica_em = now() WHERE id = ?::uuid
                """, confirmada);
        jdbc.update("UPDATE reserva SET status = 'CANCELADO', expira_em = NULL WHERE id = ?::uuid", cancelada);
        jdbc.update("""
                UPDATE reserva SET status = 'CONCLUIDO', expira_em = NULL, aceite_politica_versao = 1,
                                   aceite_politica_em = now() WHERE id = ?::uuid
                """, concluida);

        relogio.definir(em(10, 2, 30));
        for (String id : new String[] {confirmada, cancelada, concluida}) {
            long versao = versao(jdbc, id);
            prorrogar(mvc, cliente.token(), id).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("TRANSICAO_INVALIDA"));
            assertThat(prorrogadaEm(jdbc, id)).isNull();
            assertThat(versao(jdbc, id)).isEqualTo(versao);
        }
    }

    @Test
    @DisplayName("A11: reserva de outro cliente, id inexistente, reserva manual e visitante com token errado dão 404; sem token na rota do cliente dá 401")
    void quemNaoEDonoNaoVe() throws Exception {
        String minha = reservaDoCliente(15);
        Conta outro = conta("CLIENTE");
        UUID manual = UUID.randomUUID();
        inserirReservaManual(manual);
        String doVisitante = JsonPath.read(reservarComoVisitante(mvc, ContaDeTeste.ipNovo(),
                corpoGuest(servico, barbeiro, as(16, 0))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        relogio.definir(em(10, 2, 30));
        prorrogar(mvc, outro.token(), minha).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        prorrogar(mvc, cliente.token(), UUID.randomUUID().toString()).andExpect(status().isNotFound());
        prorrogar(mvc, cliente.token(), manual.toString()).andExpect(status().isNotFound());
        prorrogarComoVisitante(mvc, manual.toString(), "g1_qualquer").andExpect(status().isNotFound());
        prorrogarComoVisitante(mvc, doVisitante, "g1_token-errado").andExpect(status().isNotFound());
        prorrogarComoVisitante(mvc, doVisitante, null).andExpect(status().isNotFound());
        prorrogarSemToken(mvc, minha).andExpect(status().isUnauthorized());

        assertThat(prorrogadaEm(jdbc, minha)).isNull();
        assertThat(prorrogadaEm(jdbc, doVisitante)).isNull();
        // O corpo é o mesmo do "não existe": a resposta não confirma que a reserva alheia existe (R23).
        String alheia = prorrogar(mvc, outro.token(), minha).andReturn().getResponse().getContentAsString();
        String inexistente = prorrogar(mvc, outro.token(), UUID.randomUUID().toString()).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<String>read(alheia, "$.code")).isEqualTo(JsonPath.<String>read(inexistente, "$.code"));
        assertThat(JsonPath.<String>read(alheia, "$.detail")).isEqualTo(JsonPath.<String>read(inexistente, "$.detail"));
    }

    @Test
    @DisplayName("A17: GET do agendamento às 10:01:00 traz prorrogacao {usada false, segundos 120 (Fatia 15), abreEm 10:02:00, fechaEm 10:03:00}; reserva EXPIRADO e reserva manual (no painel) trazem null")
    void prorrogacaoNaLeitura() throws Exception {
        String id = reservaDoCliente(15);
        String expirada = reservaDoCliente(16);
        jdbc.update("UPDATE reserva SET status = 'EXPIRADO' WHERE id = ?::uuid", expirada);
        UUID manual = UUID.randomUUID();
        inserirReservaManual(manual);

        relogio.definir(em(10, 1, 0));
        mvc.perform(MockMvcRequestBuilders.get("/agendamentos/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prorrogacao.usada").value(false))
                .andExpect(jsonPath("$.prorrogacao.segundos").value(120))
                .andExpect(jsonPath("$.prorrogacao.abreEm").value("2026-12-01T10:02:00-03:00"))
                .andExpect(jsonPath("$.prorrogacao.fechaEm").value("2026-12-01T10:03:00-03:00"));
        mvc.perform(MockMvcRequestBuilders.get("/agendamentos/" + expirada).header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRADO"))
                .andExpect(jsonPath("$.prorrogacao").value((Object) null));

        Conta admin = comTokenNovo(conta("ADMIN", "BARBEIRO"), "ADMIN", "BARBEIRO");
        String agenda = mvc.perform(MockMvcRequestBuilders.get("/admin/agenda?data=2026-12-01").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + admin.token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(agenda, "$[?(@.id == '" + manual + "')].origem")).containsExactly("MANUAL");
        assertThat(JsonPath.<List<Object>>read(agenda, "$[?(@.id == '" + manual + "')].prorrogacao"))
                .containsExactly((Object) null);
        assertThat(JsonPath.<List<Boolean>>read(agenda, "$[?(@.id == '" + id + "')].prorrogacao.usada"))
                .containsExactly(false);
    }

    @Test
    @DisplayName("Contrato: a prorrogação não tem corpo; qualquer campo dá 400 CAMPO_DESCONHECIDO e nada muda")
    void corpoComCampoEhRecusado() throws Exception {
        String id = reservaDoCliente(15);
        relogio.definir(em(10, 2, 30));
        mvc.perform(MockMvcRequestBuilders.post("/agendamentos/" + id + "/prorrogacao")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{ \"segundos\": 600 }"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));
        assertThat(prorrogadaEm(jdbc, id)).isNull();
        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 3, 0));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Fatia 15 (ADR 0015, E20 a E22): 120 s por pedido, até 10 pedidos, X-Idempotency-Key opcional do último pedido.
    // ---------------------------------------------------------------------------------------------------------------

    /** Coloca a reserva com {@code usadas} pedidos já feitos e o prazo em {@code expira} (o último minuto que o teste quer). */
    private void comPedidosUsados(String id, int usadas, java.time.Instant expira) {
        jdbc.update("""
                UPDATE reserva SET prorrogacoes = ?, prorrogada_em = ?, expira_em = ? WHERE id = ?::uuid
                """, usadas, java.sql.Timestamp.from(expira.minusSeconds(150)), java.sql.Timestamp.from(expira), id);
    }

    private String expiraEmDaCobranca(String reservaId) {
        return String.valueOf(jdbc.queryForObject("SELECT expira_em FROM cobranca WHERE reserva_id = ?::uuid",
                java.time.OffsetDateTime.class, reservaId).toInstant());
    }

    @Test
    @DisplayName("A34: reserva de cliente criada às 10:00:00 (expiraEm 10:03:00) e usadas 0, POST /prorrogacao às 10:02:30 dá 201, expiraEm 10:05:00 (+120 s), prorrogacao {usadas 1, maximo 10, restantes 9, segundos 120, usada true}, evento PrazoDaReservaProrrogado e cobranca.expira_em 10:05:00")
    void pedidoSoma120Segundos() throws Exception {
        String id = reservaDoCliente(15);
        PagamentosDeTeste.cobrarComoCliente(mvc, cliente.token(), PagamentosDeTeste.chaveNova(),
                PagamentosDeTeste.corpoDaCobranca(id, "PIX", PagamentosDeTeste.CPF, PagamentosDeTeste.versaoVigente(jdbc)))
                .andExpect(status().isCreated());

        relogio.definir(em(10, 2, 30));
        prorrogar(mvc, cliente.token(), id, UUID.randomUUID().toString()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiraEm").value("2026-12-01T10:05:00-03:00"))
                .andExpect(jsonPath("$.prorrogacao.usadas").value(1))
                .andExpect(jsonPath("$.prorrogacao.maximo").value(10))
                .andExpect(jsonPath("$.prorrogacao.restantes").value(9))
                .andExpect(jsonPath("$.prorrogacao.segundos").value(120))
                .andExpect(jsonPath("$.prorrogacao.usada").value(true));

        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 5, 0));
        assertThat(prorrogacoes(jdbc, id)).isEqualTo(1);
        assertThat(eventosDePrazo(jdbc, id)).isEqualTo(1);
        PagamentosDeTeste.esperar(() -> em(10, 5, 0).toString().equals(expiraEmDaCobranca(id)));
    }

    @Test
    @DisplayName("A35: depois do A34, o mesmo pedido às 10:02:31 com a mesma X-Idempotency-Key dá 200 com o mesmo corpo; expiraEm continua 10:05:00 e usadas continua 1")
    void mesmaChaveEhRepeticao() throws Exception {
        String id = reservaDoCliente(15);
        String chave = UUID.randomUUID().toString();
        relogio.definir(em(10, 2, 30));
        String primeiro = prorrogar(mvc, cliente.token(), id, chave).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        relogio.definir(em(10, 2, 31));
        String segundo = prorrogar(mvc, cliente.token(), id, chave).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(segundo).isEqualTo(primeiro);
        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 5, 0));
        assertThat(prorrogacoes(jdbc, id)).isEqualTo(1);
        assertThat(eventosDePrazo(jdbc, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("A36: depois do A34, um pedido sem chave ou com chave nova às 10:02:31 dá 422 PRORROGACAO_FORA_DA_JANELA (o prazo tem mais de 60 s) e nada muda")
    void semChaveOuChaveNovaForaDaJanela() throws Exception {
        String id = reservaDoCliente(15);
        relogio.definir(em(10, 2, 30));
        prorrogar(mvc, cliente.token(), id, UUID.randomUUID().toString()).andExpect(status().isCreated());
        long versao = versao(jdbc, id);

        relogio.definir(em(10, 2, 31));
        prorrogar(mvc, cliente.token(), id).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRORROGACAO_FORA_DA_JANELA"));
        prorrogar(mvc, cliente.token(), id, UUID.randomUUID().toString()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRORROGACAO_FORA_DA_JANELA"));

        assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 5, 0));
        assertThat(prorrogacoes(jdbc, id)).isEqualTo(1);
        assertThat(versao(jdbc, id)).isEqualTo(versao);
    }

    @Test
    @DisplayName("A37: com usadas 9 e dentro do último minuto, o 10º pedido dá 201 com usadas 10 e restantes 0; o objeto prorrogacao continua presente")
    void decimoPedido() throws Exception {
        String id = reservaDoCliente(15);
        comPedidosUsados(id, 9, em(10, 21, 0));

        relogio.definir(em(10, 20, 30));
        String acesso = comTokenNovo(cliente, "CLIENTE").token();
        prorrogar(mvc, acesso, id).andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiraEm").value("2026-12-01T10:23:00-03:00"))
                .andExpect(jsonPath("$.prorrogacao.usadas").value(10))
                .andExpect(jsonPath("$.prorrogacao.restantes").value(0))
                .andExpect(jsonPath("$.prorrogacao.maximo").value(10));
        assertThat(prorrogacoes(jdbc, id)).isEqualTo(10);

        relogio.definir(em(10, 22, 10));
        mvc.perform(MockMvcRequestBuilders.get("/agendamentos/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + acesso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prorrogacao.restantes").value(0))
                .andExpect(jsonPath("$.prorrogacao.abreEm").value("2026-12-01T10:22:00-03:00"));
    }

    @Test
    @DisplayName("A38: com usadas 10 e dentro do último minuto, o 11º pedido dá 422 PRORROGACAO_ESGOTADA para o cliente e para o visitante; expiraEm e usadas não mudam")
    void decimoPrimeiroPedido() throws Exception {
        String doCliente = reservaDoCliente(15);
        String criada = reservarComoVisitante(mvc, ContaDeTeste.ipNovo(), corpoGuest(servico, barbeiro, as(16, 0)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String doVisitante = JsonPath.read(criada, "$.id");
        String token = JsonPath.read(criada, "$.tokenGuest");
        comPedidosUsados(doCliente, 10, em(10, 23, 0));
        comPedidosUsados(doVisitante, 10, em(10, 23, 0));

        relogio.definir(em(10, 22, 30));
        prorrogar(mvc, comTokenNovo(cliente, "CLIENTE").token(), doCliente).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRORROGACAO_ESGOTADA"))
                .andExpect(jsonPath("$.title").value("Prorrogações esgotadas"));
        prorrogarComoVisitante(mvc, doVisitante, token).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRORROGACAO_ESGOTADA"));

        for (String id : new String[] {doCliente, doVisitante}) {
            assertThat(expiraEm(jdbc, id)).isEqualTo(em(10, 23, 0));
            assertThat(prorrogacoes(jdbc, id)).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("A39: com usadas 10 e o prazo já vencido, um pedido dá 422 RESERVA_EXPIRADA (vem antes do PRORROGACAO_ESGOTADA)")
    void expiradaVemAntesDeEsgotada() throws Exception {
        String id = reservaDoCliente(15);
        comPedidosUsados(id, 10, em(10, 23, 0));

        relogio.definir(em(10, 23, 0));
        prorrogar(mvc, comTokenNovo(cliente, "CLIENTE").token(), id).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RESERVA_EXPIRADA"));
        assertThat(prorrogacoes(jdbc, id)).isEqualTo(10);
    }

    @Test
    @DisplayName("Contrato: X-Idempotency-Key fora do formato UUID dá 400 VALIDACAO_INVALIDA e nada muda")
    void chaveForaDoFormato() throws Exception {
        String id = reservaDoCliente(15);
        relogio.definir(em(10, 2, 30));
        prorrogar(mvc, cliente.token(), id, "nao-e-uuid").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("X-Idempotency-Key"));
        assertThat(prorrogacoes(jdbc, id)).isZero();
    }
}
