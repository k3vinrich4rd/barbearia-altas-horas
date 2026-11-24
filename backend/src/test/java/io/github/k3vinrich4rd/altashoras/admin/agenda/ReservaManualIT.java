package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste;

/**
 * A13 a A24 da Fatia 6D: a reserva manual (POST /admin/agendamentos), o horário combinado por WhatsApp que a barbearia
 * trava no painel. Roda na terça 01/12/2026 às 09:30 com a massa do painel (barbeiro A com a conta contaA, barbeiro B,
 * admin, serviço de 30 min e R$ 45,00). Nomes fictícios.
 */
class ReservaManualIT extends PainelBase {

    private static final String ROTA = "/admin/agendamentos";

    private String corpo(UUID servico, UUID barbeiro, OffsetDateTime inicio, String nome, String observacao) {
        StringBuilder b = new StringBuilder("{ \"servicoId\": \"" + servico + "\", \"barbeiroId\": \"" + barbeiro
                + "\", \"inicio\": \"" + inicio + "\"");
        if (nome != null) {
            b.append(", \"nomeCliente\": \"").append(nome).append('"');
        }
        if (observacao != null) {
            b.append(", \"observacao\": \"").append(observacao).append('"');
        }
        return b.append(" }").toString();
    }

    private ResultActions criar(Conta quem, String corpo) throws Exception {
        var pedido = MockMvcRequestBuilders.post(ROTA).contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (quem != null) {
            pedido = pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + quem.token());
        }
        return mvc.perform(pedido);
    }

    private long contarReservas() {
        return jdbc.queryForObject("SELECT count(*) FROM reserva", Long.class);
    }

    @Test
    @DisplayName("A13: o BARBEIRO trava as 15:00 para si: 201 CONFIRMADO, origem MANUAL, sem prazo e sem cobrança; no banco sem cliente, sem contato de visitante e sem aceite, com a auditoria sem nome")
    void barbeiroTravaParaSi() throws Exception {
        Massa m = massa();

        String resposta = criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(15, 0), "Fulano de Tal", "veio pelo Insta"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.origem").value("MANUAL"))
                .andExpect(jsonPath("$.expiraEm").doesNotExist())
                .andExpect(jsonPath("$.cobrancaStatus").doesNotExist())
                .andExpect(jsonPath("$.inicio").value("2026-12-01T15:00:00-03:00"))
                .andExpect(jsonPath("$.fim").value("2026-12-01T15:30:00-03:00"))
                .andExpect(jsonPath("$.precoCentavos").value(4500))
                .andExpect(jsonPath("$.barbeiro.id").value(m.barbeiroA().toString()))
                .andExpect(jsonPath("$.cliente.nome").value("Fulano de Tal"))
                .andExpect(jsonPath("$.cliente.whatsapp").doesNotExist())
                .andExpect(jsonPath("$.observacao").value("veio pelo Insta"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(JsonPath.read(resposta, "$.id"));
        var linha = jdbc.queryForMap("""
                SELECT origem, status, cliente_id, expira_em, aceite_politica_em, criado_por, nome_cliente
                FROM reserva WHERE id = ?""", id);
        assertThat(linha).containsEntry("origem", "MANUAL").containsEntry("status", "CONFIRMADO")
                .containsEntry("criado_por", m.contaA().id()).containsEntry("nome_cliente", "Fulano de Tal");
        assertThat(linha.get("cliente_id")).isNull();
        assertThat(linha.get("expira_em")).isNull();
        assertThat(linha.get("aceite_politica_em")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cobranca WHERE reserva_id = ?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva_guest WHERE reserva_id = ?", Long.class, id)).isZero();
        assertThat(jdbc.queryForList("SELECT detalhe::text FROM auditoria_admin WHERE acao = 'AGENDAMENTO_MANUAL_CRIADO' AND recurso_id = ?",
                String.class, id)).singleElement().asString().doesNotContain("Fulano").doesNotContain("Insta");
    }

    @Test
    @DisplayName("A14: o ADMIN trava um horário para o barbeiro B (qualquer barbeiro) e a auditoria registra o admin como autor")
    void adminTravaParaQualquerBarbeiro() throws Exception {
        Massa m = massa();

        criar(m.admin(), corpo(m.servico(), m.barbeiroB(), as(16, 0), null, null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.barbeiro.id").value(m.barbeiroB().toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));

        assertThat(jdbc.queryForObject("SELECT ator_id FROM auditoria_admin WHERE acao = 'AGENDAMENTO_MANUAL_CRIADO'",
                UUID.class)).isEqualTo(m.admin().id());
    }

    @Test
    @DisplayName("A15: o BARBEIRO travando para outro barbeiro, a conta BARBEIRO sem vínculo e o ADMIN com barbeiro inexistente recebem 404, sem gravar reserva nem auditoria")
    void escopoDoBarbeiro() throws Exception {
        Massa m = massa();
        Conta semVinculo = conta("BARBEIRO");
        long reservas = contarReservas();
        long auditoria = linhasDeAuditoria();

        criar(m.contaA(), corpo(m.servico(), m.barbeiroB(), as(16, 0), null, null)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        criar(semVinculo, corpo(m.servico(), m.barbeiroA(), as(16, 0), null, null)).andExpect(status().isNotFound());
        criar(m.admin(), corpo(m.servico(), UUID.randomUUID(), as(16, 0), null, null)).andExpect(status().isNotFound());

        assertThat(contarReservas()).isEqualTo(reservas);
        assertThat(linhasDeAuditoria()).isEqualTo(auditoria);
    }

    @Test
    @DisplayName("A16: horário de reserva CONFIRMADO ou de pendente ainda válida dá 409 HORARIO_INDISPONIVEL sem gravar nada; o de pendente já vencida está livre e dá 201")
    void horarioOcupado() throws Exception {
        irPara(TERCA, 8, 0); // antes da massa: as 09:00 da R1 ainda não passaram e os tokens nascem neste instante
        Massa m = massa(); // R1 09:00 CONFIRMADO, R2 10:00 aguardando até 09:40, R3 11:00 EXPIRADO
        UUID vencida = reserva(m.barbeiroA(), m.servico(), as(13, 0), "AGUARDANDO_PAGAMENTO",
                relogio.instant().minusSeconds(60), null, "Visitante Vencido");
        long reservas = contarReservas();
        long auditoria = linhasDeAuditoria();

        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(9, 0), null, null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORARIO_INDISPONIVEL"));
        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(10, 0), null, null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORARIO_INDISPONIVEL"));
        assertThat(contarReservas()).isEqualTo(reservas);
        assertThat(linhasDeAuditoria()).isEqualTo(auditoria);

        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(13, 0), null, null)).andExpect(status().isCreated());
        assertThat(statusDaReserva(vencida)).isEqualTo("EXPIRADO");
    }

    @Test
    @DisplayName("A17: almoço (12:00), antes da jornada (06:30), fora da granularidade (09:45), segunda-feira, folga, serviço inativo, horário que já passou e data além de 30 dias dão 422 com o código certo")
    void respeitaADisponibilidade() throws Exception {
        Massa m = massa();
        UUID inativo = DadosDeTeste.servico(jdbc, "Serviço parado", 30, 4500, false);
        DadosDeTeste.folga(jdbc, m.barbeiroA(), as(17, 0), as(18, 0));
        OffsetDateTime segunda = DadosDeTeste.em(TERCA.plusDays(6), 10, 0);
        OffsetDateTime longe = DadosDeTeste.em(TERCA.plusDays(40), 10, 0);

        for (OffsetDateTime inicio : List.of(as(12, 0), DadosDeTeste.em(TERCA.plusDays(1), 6, 30), as(9, 45), segunda, as(17, 0))) {
            criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), inicio, null, null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("FORA_DO_EXPEDIENTE"));
        }
        criar(m.contaA(), corpo(inativo, m.barbeiroA(), as(15, 0), null, null)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SERVICO_INATIVO"));
        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(8, 0), null, null)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ANTECEDENCIA_INSUFICIENTE"));
        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), longe, null, null)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FORA_DO_HORIZONTE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE origem = 'MANUAL'", Long.class)).isZero();
    }

    @Test
    @DisplayName("A17b: 18:18 (dentro da jornada, mas fora da grade de 30 min) dá 422 FORA_DO_EXPEDIENTE dizendo a grade e a jornada do dia; 18:30 dá 201")
    void horarioForaDaGradeExplicaOMotivo() throws Exception {
        Massa m = massa();

        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(18, 18), null, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FORA_DO_EXPEDIENTE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("de 30 em 30 minutos")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("07:00 às 12:00 e 13:00 às 20:00")));
        // almoço: a mensagem também diz a jornada, sem falar de grade
        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(12, 0), null, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("07:00 às 12:00 e 13:00 às 20:00")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE origem = 'MANUAL'", Long.class)).isZero();

        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(18, 30), null, null)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A18: a antecedência mínima de 2 h não vale para a reserva manual (10:30 com o relógio às 09:30 dá 201), mas vale para o cliente (a mesma hora dá 422 ANTECEDENCIA_INSUFICIENTE)")
    void semAntecedenciaMinima() throws Exception {
        Massa m = massa();
        Conta cliente = conta("CLIENTE");

        mvc.perform(MockMvcRequestBuilders.post("/agendamentos").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                        .content(corpo(m.servico(), m.barbeiroA(), as(10, 30), null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ANTECEDENCIA_INSUFICIENTE"));
        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(10, 30), null, null)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A19: a reserva manual aparece na agenda do dia (origem MANUAL, nome, observação, sem WhatsApp; sem nome vira Sem nome) e o horário some da disponibilidade pública")
    void apareceNaAgendaEOcupa() throws Exception {
        Massa m = massa();
        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(15, 0), "Fulano de Tal", "combinado no zap")).andExpect(status().isCreated());
        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(15, 30), null, null)).andExpect(status().isCreated());

        get("/admin/agenda?data=2026-12-01", m.contaA()).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.inicio == '2026-12-01T15:00:00-03:00')].origem").value("MANUAL"))
                .andExpect(jsonPath("$[?(@.inicio == '2026-12-01T15:00:00-03:00')].cliente.nome").value("Fulano de Tal"))
                .andExpect(jsonPath("$[?(@.inicio == '2026-12-01T15:00:00-03:00')].observacao").value("combinado no zap"))
                .andExpect(jsonPath("$[?(@.inicio == '2026-12-01T15:30:00-03:00')].cliente.nome").value("Sem nome"));

        String horarios = mvc.perform(MockMvcRequestBuilders.get("/disponibilidade")
                        .param("servicoId", m.servico().toString()).param("data", "2026-12-01")
                        .param("barbeiroId", m.barbeiroA().toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(horarios).doesNotContain("2026-12-01T15:00:00-03:00").doesNotContain("2026-12-01T15:30:00-03:00")
                .contains("2026-12-01T14:30:00-03:00").contains("2026-12-01T16:00:00-03:00");
    }

    @Test
    @DisplayName("A20: quatro pedidos ao mesmo tempo para o mesmo horário: um dá 201 e os outros 409 HORARIO_INDISPONIVEL; só uma reserva é gravada")
    void concorrencia() throws Exception {
        Massa m = massa();
        String pedido = corpo(m.servico(), m.barbeiroA(), as(16, 0), "Fulano", null);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            resultados.add(pool.submit(() -> {
                largada.await();
                return criar(m.contaA(), pedido).andReturn().getResponse().getStatus();
            }));
        }
        largada.countDown();
        List<Integer> codigos = new ArrayList<>();
        for (Future<Integer> f : resultados) {
            codigos.add(f.get());
        }
        pool.shutdown();

        assertThat(codigos).containsOnlyOnce(201).filteredOn(c -> c != 201).hasSize(3).containsOnly(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE origem = 'MANUAL'", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("A21: a reserva manual é fechada como as demais: o BARBEIRO conclui ou marca falta a partir do início e nenhuma cobrança nem pendência de pagamento aparece")
    void fechamento() throws Exception {
        Massa m = massa();
        UUID concluida = UUID.fromString(JsonPath.read(criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(15, 0), "Fulano", null))
                .andReturn().getResponse().getContentAsString(), "$.id"));
        UUID faltou = UUID.fromString(JsonPath.read(criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(15, 30), null, null))
                .andReturn().getResponse().getContentAsString(), "$.id"));

        patchStatus(concluida, m.contaA(), corpoDeStatus("CONCLUIDO", 0)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSICAO_INVALIDA")); // ainda não começou
        irPara(TERCA, 15, 30);
        Conta contaA = comTokenNovo(m.contaA(), "BARBEIRO");
        Conta admin = comTokenNovo(m.admin(), "ADMIN", "BARBEIRO");
        patchStatus(concluida, contaA, corpoDeStatus("CONCLUIDO", 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO")).andExpect(jsonPath("$.cobrancaStatus").doesNotExist());
        patchStatus(faltou, contaA, corpoDeStatus("NAO_COMPARECEU", 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAO_COMPARECEU"));

        assertThat(statusDaReserva(concluida)).isEqualTo("CONCLUIDO");
        assertThat(jdbc.queryForObject("SELECT finalizado_por FROM reserva WHERE id = ?", UUID.class, faltou))
                .isEqualTo(m.contaA().id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cobranca WHERE reserva_id IN (?, ?)", Long.class, concluida, faltou))
                .isZero();
        get("/admin/pagamentos", admin).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("A22: o ADMIN cancela a reserva manual com motivo (200 CANCELADO) sem criar cobrança nem devolução e o horário volta; o BARBEIRO recebe 403 e sem motivo é 422 MOTIVO_OBRIGATORIO")
    void cancelamento() throws Exception {
        Massa m = massa();
        UUID id = UUID.fromString(JsonPath.read(criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(15, 0), "Fulano", null))
                .andReturn().getResponse().getContentAsString(), "$.id"));

        patchStatus(id, m.contaA(), corpoDeCancelamento("desistiu", 0)).andExpect(status().isForbidden());
        patchStatus(id, m.admin(), corpoDeStatus("CANCELADO", 0)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MOTIVO_OBRIGATORIO"));
        patchStatus(id, m.admin(), corpoDeCancelamento("desistiu", 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO")).andExpect(jsonPath("$.cobrancaStatus").doesNotExist());
        esperarOutboxVazio();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cobranca WHERE reserva_id = ?", Long.class, id)).isZero();
        get("/admin/pagamentos", m.admin()).andExpect(jsonPath("$.total").value(0));
        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(15, 0), null, null)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A23: corpo inválido é 400 (nome com 81 e observação com 201 caracteres, campos fora do contrato como whatsapp, clienteId, status e precoCentavos, servicoId faltando); sem token é 401, CLIENTE é 403; nome em branco vira nulo")
    void validacaoEAcesso() throws Exception {
        Massa m = massa();
        long reservas = contarReservas();

        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(15, 0), "n".repeat(81), null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nomeCliente"));
        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(15, 0), null, "o".repeat(201))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("observacao"));
        for (String extra : new String[] {"\"whatsapp\": \"11912345678\"", "\"clienteId\": \"" + UUID.randomUUID() + "\"",
                "\"status\": \"CONFIRMADO\"", "\"precoCentavos\": 1"}) {
            criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(15, 0), null, null).replace(" }", ", " + extra + " }"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));
        }
        criar(m.contaA(), "{ \"barbeiroId\": \"" + m.barbeiroA() + "\", \"inicio\": \"" + as(15, 0) + "\" }")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("servicoId"));
        criar(null, corpo(m.servico(), m.barbeiroA(), as(15, 0), null, null)).andExpect(status().isUnauthorized());
        criar(conta("CLIENTE"), corpo(m.servico(), m.barbeiroA(), as(15, 0), null, null)).andExpect(status().isForbidden());
        criar(conta(true, "ADMIN"), corpo(m.servico(), m.barbeiroA(), as(15, 0), null, null)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TROCA_DE_SENHA_OBRIGATORIA"));
        assertThat(contarReservas()).isEqualTo(reservas);

        criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(15, 0), "   ", "  ")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.cliente.nome").value("Sem nome")).andExpect(jsonPath("$.observacao").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT nome_cliente FROM reserva WHERE origem = 'MANUAL'", String.class)).isNull();
    }

    @Test
    @DisplayName("A24: o cliente logado não vê nem paga uma reserva manual (404, R23) e ela não entra em Meus agendamentos")
    void clienteNaoEnxerga() throws Exception {
        Massa m = massa();
        UUID id = UUID.fromString(JsonPath.read(criar(m.contaA(), corpo(m.servico(), m.barbeiroA(), as(15, 0), "Fulano", null))
                .andReturn().getResponse().getContentAsString(), "$.id"));
        Conta cliente = conta("CLIENTE");

        get("/agendamentos/" + id, cliente).andExpect(status().isNotFound());
        get("/me/agendamentos", cliente).andExpect(status().isOk()).andExpect(jsonPath("$.itens.length()").value(0));
        PagamentosDeTeste.cobrarComoCliente(mvc, cliente.token(), PagamentosDeTeste.chaveNova(),
                PagamentosDeTeste.corpoDaCobranca(id.toString(), "PIX", PagamentosDeTeste.CPF, 1))
                .andExpect(status().isNotFound());
        mvc.perform(MockMvcRequestBuilders.get("/agendamentos/guest/" + id).header("X-Guest-Token", "x".repeat(43)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cobranca WHERE reserva_id = ?", Long.class, id)).isZero();
    }
}
