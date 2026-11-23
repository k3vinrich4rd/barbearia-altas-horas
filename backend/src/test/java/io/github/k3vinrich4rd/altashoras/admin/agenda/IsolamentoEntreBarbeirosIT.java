package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/**
 * A2 a A12 da Fatia 10A, mais A39 e A40 (o ADMIN na agenda de outro barbeiro): o isolamento entre dois barbeiros reais
 * (A e B), com reservas de conta, de visitante e manuais. O BARBEIRO só mexe na própria agenda (e o que é de outro é 404);
 * o ADMIN (o dono) vê, cria reserva manual, fecha e cancela na agenda de qualquer barbeiro. Terça 01/12/2026, 09:30.
 */
class IsolamentoEntreBarbeirosIT extends PainelBase {

    private static final String DIA = "/admin/agenda?data=2026-12-01";

    private List<String> ids(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$[*].id");
    }

    private String corpoManual(UUID servico, UUID barbeiro, OffsetDateTime inicio, String nome) {
        return "{ \"servicoId\": \"" + servico + "\", \"barbeiroId\": \"" + barbeiro + "\", \"inicio\": \"" + inicio
                + "\", \"nomeCliente\": \"" + nome + "\" }";
    }

    private ResultActions criarManual(Conta quem, String corpo) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/admin/agendamentos").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + quem.token()).content(corpo));
    }

    private void tudoIgual(UUID reserva, String statusAntes, long versaoAntes) {
        assertThat(statusDaReserva(reserva)).isEqualTo(statusAntes);
        assertThat(versaoDa(reserva)).isEqualTo(versaoAntes);
    }

    @Test
    @DisplayName("A2: o BARBEIRO A pede a agenda com barbeiroId de B e recebe 404; sem filtro, vê só as reservas dele")
    void agendaDeOutroEhNaoEncontrada() throws Exception {
        Massa m = massa();

        get(DIA + "&barbeiroId=" + m.barbeiroB(), m.contaA()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        assertThat(ids(get(DIA, m.contaA()))).containsExactly(m.r1().toString(), m.r2().toString(), m.r5().toString())
                .doesNotContain(m.r6().toString());
        assertThat(ids(get(DIA + "&barbeiroId=" + m.barbeiroA(), m.contaA()))).contains(m.r1().toString());
    }

    @Test
    @DisplayName("A3: o BARBEIRO A tenta concluir, marcar falta e cancelar a reserva de B e recebe 404 nos três, sem mudar a reserva (o teste de dono vem antes do 403 do cancelamento)")
    void fecharReservaDeOutroEhNaoEncontrada() throws Exception {
        Massa m = massa();
        long versao = versaoDa(m.r6());
        long auditoria = linhasDeAuditoria();

        patchStatus(m.r6(), m.contaA(), corpoDeStatus("CONCLUIDO", versao)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        patchStatus(m.r6(), m.contaA(), corpoDeStatus("NAO_COMPARECEU", versao)).andExpect(status().isNotFound());
        patchStatus(m.r6(), m.contaA(), corpoDeCancelamento("Tentativa", versao)).andExpect(status().isNotFound());

        tudoIgual(m.r6(), "CONFIRMADO", versao);
        assertThat(linhasDeAuditoria()).isEqualTo(auditoria);
    }

    @Test
    @DisplayName("A4: o BARBEIRO A faz reserva manual no horário livre de B e recebe 404; nenhuma reserva e nenhuma linha de auditoria")
    void reservaManualNaAgendaDeOutroEhNaoEncontrada() throws Exception {
        Massa m = massa();
        long reservas = jdbc.queryForObject("SELECT count(*) FROM reserva", Long.class);
        long auditoria = linhasDeAuditoria();

        criarManual(m.contaA(), corpoManual(m.servico(), m.barbeiroB(), as(16, 0), "Fulano")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva", Long.class)).isEqualTo(reservas);
        assertThat(linhasDeAuditoria()).isEqualTo(auditoria);
    }

    @Test
    @DisplayName("A5: o BARBEIRO A pede o alcance com barbeiroId de B e recebe os números de A, nunca os de B, sem sinal do filtro ignorado")
    void alcanceDeOutroVemComOsNumerosDoProprio() throws Exception {
        Massa m = massa();
        String comFiltro = get("/admin/alcance?barbeiroId=" + m.barbeiroB(), m.contaA()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String semFiltro = get("/admin/alcance", m.contaA()).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        assertThat(comFiltro).isEqualTo(semFiltro).doesNotContain(m.barbeiroB().toString());
    }

    @Test
    @DisplayName("A6: a jornada vem do token: A recebe a jornada de A e B a de B")
    void minhaJornadaEhDoToken() throws Exception {
        Massa m = massa();

        get("/admin/minha-jornada", m.contaA()).andExpect(status().isOk())
                .andExpect(jsonPath("$.barbeiroId").value(m.barbeiroA().toString()));
        get("/admin/minha-jornada", m.contaB()).andExpect(status().isOk())
                .andExpect(jsonPath("$.barbeiroId").value(m.barbeiroB().toString()));
    }

    @Test
    @DisplayName("A7: o BARBEIRO A chama /admin/barbeiros, serviços, configuração, pagamentos e auditoria e recebe 403 ACESSO_NEGADO em todas, sem dado")
    void rotasSoDoDono() throws Exception {
        Massa m = massa();
        UUID qualquer = UUID.randomUUID();

        for (String rota : List.of("/admin/barbeiros", "/admin/barbeiros/" + m.barbeiroB() + "/folgas", "/admin/servicos",
                "/admin/configuracao", "/admin/pagamentos", "/admin/auditoria")) {
            get(rota, m.contaA()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        }
        for (String rota : List.of("/admin/barbeiros/" + m.barbeiroB() + "/convite",
                "/admin/pagamentos/" + qualquer + "/devolucao")) {
            mvc.perform(MockMvcRequestBuilders.post(rota).header(HttpHeaders.AUTHORIZATION, "Bearer " + m.contaA().token()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        }
        mvc.perform(MockMvcRequestBuilders.delete("/admin/barbeiros/" + m.barbeiroB() + "/convite")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + m.contaA().token())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A8: com o barbeiro de A desligado (conta ainda ativa), as rotas do painel com escopo próprio dão 404")
    void barbeiroDesligadoNaoEnxergaNada() throws Exception {
        Massa m = massa();
        jdbc.update("UPDATE barbeiro SET ativo = false WHERE id = ?", m.barbeiroA());

        get(DIA, m.contaA()).andExpect(status().isNotFound());
        get("/admin/alcance", m.contaA()).andExpect(status().isNotFound());
        get("/admin/minha-jornada", m.contaA()).andExpect(status().isNotFound());
        patchStatus(m.r1(), m.contaA(), corpoDeStatus("CONCLUIDO", versaoDa(m.r1()))).andExpect(status().isNotFound());
        criarManual(m.contaA(), corpoManual(m.servico(), m.barbeiroA(), as(16, 0), "Fulano")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A9: o ADMIN vê A e B sem filtro, só B com ?barbeiroId=B e recebe 404 com um barbeiro inexistente")
    void adminVeTudoOuFiltra() throws Exception {
        Massa m = massa();

        assertThat(ids(get(DIA, m.admin()))).containsExactly(m.r1().toString(), m.r6().toString(), m.r2().toString(),
                m.r5().toString());
        assertThat(ids(get(DIA + "&barbeiroId=" + m.barbeiroB(), m.admin()))).containsExactly(m.r6().toString());
        get(DIA + "&barbeiroId=" + UUID.randomUUID(), m.admin()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A10: reserva de B e id inexistente dão 404 com o mesmo code, title e formato, sem o id no corpo")
    void alheioEInexistenteSaoIguais() throws Exception {
        Massa m = massa();
        UUID inexistente = UUID.randomUUID();

        String alheia = patchStatus(m.r6(), m.contaA(), corpoDeStatus("CONCLUIDO", 0)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String sumida = patchStatus(inexistente, m.contaA(), corpoDeStatus("CONCLUIDO", 0)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        for (String campo : List.of("$.code", "$.title", "$.detail", "$.status", "$.type")) {
            assertThat((Object) JsonPath.read(alheia, campo)).isEqualTo(JsonPath.read(sumida, campo));
        }
        // O "instance" repete a URL que o próprio chamador pediu; fora ele, o corpo não traz id nenhum.
        assertThat(alheia.replace(m.r6().toString(), "ID")).doesNotContain(m.r6().toString());
        assertThat(alheia.replaceAll("\"instance\":\"[^\"]*\"", "")).doesNotContain(m.r6().toString());
        assertThat(sumida.replaceAll("\"instance\":\"[^\"]*\"", "")).doesNotContain(inexistente.toString());
    }

    @Test
    @DisplayName("A11: cada barbeiro vê só os clientes dele; nenhum nome, WhatsApp ou e-mail de cliente de B aparece para A, nem em erro")
    void clientesDeUmNaoVazamParaOutro() throws Exception {
        Massa m = massa();

        String deA = get(DIA, m.contaA()).andReturn().getResponse().getContentAsString();
        String deB = get(DIA, m.contaB()).andReturn().getResponse().getContentAsString();
        String erro = patchStatus(m.r6(), m.contaA(), corpoDeStatus("CONCLUIDO", 0)).andReturn().getResponse()
                .getContentAsString();

        assertThat(deA).contains("Maria Souza Teste").contains("Visitante Dois").doesNotContain("Ana Costa Teste")
                .doesNotContain("11988880006");
        assertThat(deB).contains("Ana Costa Teste").doesNotContain("Maria Souza Teste").doesNotContain("Visitante Dois")
                .doesNotContain("11988880001");
        assertThat(erro).doesNotContain("Ana Costa").doesNotContain("11988880006").doesNotContain("@exemplo.test");
    }

    @Test
    @DisplayName("A12: a reserva manual de A às 15:00 deixa A ocupado e B livre, e não aparece na agenda de B")
    void reservaManualDeUmNaoOcupaOOutro() throws Exception {
        Massa m = massa();
        criarManual(m.contaA(), corpoManual(m.servico(), m.barbeiroA(), as(15, 0), "Fulano de Tal")).andExpect(status().isCreated());

        for (var par : List.of(java.util.Map.entry(m.barbeiroA(), false), java.util.Map.entry(m.barbeiroB(), true))) {
            String horarios = mvc.perform(MockMvcRequestBuilders.get("/disponibilidade")
                            .param("servicoId", m.servico().toString()).param("data", "2026-12-01")
                            .param("barbeiroId", par.getKey().toString()))
                    .andReturn().getResponse().getContentAsString();
            assertThat(horarios.contains("2026-12-01T15:00:00-03:00")).isEqualTo(par.getValue());
        }
        assertThat(get(DIA, m.contaB()).andReturn().getResponse().getContentAsString()).doesNotContain("Fulano de Tal");
    }

    @Test
    @DisplayName("A39: o ADMIN faz reserva manual na agenda de outro barbeiro (B): 201, a reserva é de B, a auditoria registra o admin e ela só aparece na agenda de B")
    void adminFazReservaManualNaAgendaDeOutro() throws Exception {
        Massa m = massa();

        String corpo = criarManual(m.admin(), corpoManual(m.servico(), m.barbeiroB(), as(16, 0), "Fulano de Tal"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.barbeiro.id").value(m.barbeiroB().toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMADO")).andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(JsonPath.read(corpo, "$.id"));
        assertThat(jdbc.queryForObject("SELECT barbeiro_id FROM reserva WHERE id = ?", UUID.class, id)).isEqualTo(m.barbeiroB());
        assertThat(jdbc.queryForObject("SELECT ator_id FROM auditoria_admin WHERE recurso_id = ?", UUID.class, id))
                .isEqualTo(m.admin().id());
        assertThat(ids(get(DIA, m.contaB()))).contains(id.toString());
        assertThat(ids(get(DIA, m.contaA()))).doesNotContain(id.toString());
    }

    @Test
    @DisplayName("A40: o ADMIN conclui, marca falta e cancela reservas de outro barbeiro (B): 200 nos três, com o autor e o motivo gravados e a auditoria registrada")
    void adminFechaECancelaNaAgendaDeOutro() throws Exception {
        Massa m = massa();
        irPara(TERCA, 11, 0);
        Conta admin = comTokenNovo(m.admin(), "ADMIN", "BARBEIRO");
        UUID segunda = reserva(m.barbeiroB(), m.servico(), as(10, 0), "CONFIRMADO", null, cliente("Joao Lima Teste", "11988880007"), null);
        UUID terceira = reserva(m.barbeiroB(), m.servico(), as(10, 30), "CONFIRMADO", null, cliente("Rita Lima Teste", "11988880008"), null);

        patchStatus(m.r6(), admin, corpoDeStatus("CONCLUIDO", versaoDa(m.r6()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));
        patchStatus(segunda, admin, corpoDeStatus("NAO_COMPARECEU", versaoDa(segunda))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAO_COMPARECEU"));
        patchStatus(terceira, admin, corpoDeCancelamento("Barbeiro B doente", versaoDa(terceira))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO")).andExpect(jsonPath("$.motivoCancelamento").value("Barbeiro B doente"));

        assertThat(jdbc.queryForObject("SELECT cancelado_por FROM reserva WHERE id = ?", UUID.class, terceira))
                .isEqualTo(m.admin().id());
        assertThat(jdbc.queryForList("SELECT DISTINCT ator_id FROM auditoria_admin", UUID.class)).containsExactly(m.admin().id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE recurso_id IN (?, ?, ?)", Long.class,
                m.r6(), segunda, terceira)).isEqualTo(3);
    }
}
