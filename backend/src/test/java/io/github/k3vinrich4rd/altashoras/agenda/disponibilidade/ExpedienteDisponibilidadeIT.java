package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.cadastro.FeriasBase;

/**
 * A26 a A33: o expediente do dia por barbeiro em GET /disponibilidade (1.12.0, E16 a E19). O público vê só "aberto",
 * o motivo genérico e os horários de início, fim e almoço; nunca o tipo nem o motivo da folga.
 */
class ExpedienteDisponibilidadeIT extends FeriasBase {

    private ResultActions disponibilidade(LocalDate dia, UUID barbeiro) throws Exception {
        var pedido = MockMvcRequestBuilders.get("/disponibilidade").param("servicoId", servico.toString())
                .param("data", dia.toString());
        if (barbeiro != null) {
            pedido.param("barbeiroId", barbeiro.toString());
        }
        return mvc.perform(pedido).andExpect(status().isOk());
    }

    private static void fechado(ResultActions resposta, String prefixo, String motivo) throws Exception {
        resposta.andExpect(jsonPath(prefixo + ".horarios", empty()))
                .andExpect(jsonPath(prefixo + ".expediente.aberto").value(false))
                .andExpect(jsonPath(prefixo + ".expediente.motivoFechado").value(motivo))
                .andExpect(jsonPath(prefixo + ".expediente.inicio").value(nullValue()))
                .andExpect(jsonPath(prefixo + ".expediente.fim").value(nullValue()))
                .andExpect(jsonPath(prefixo + ".expediente.almoco").value(nullValue()));
    }

    private static void aberto(ResultActions resposta, String dia) throws Exception {
        resposta.andExpect(jsonPath("$.barbeiros[0].expediente.aberto").value(true))
                .andExpect(jsonPath("$.barbeiros[0].expediente.motivoFechado").value(nullValue()))
                .andExpect(jsonPath("$.barbeiros[0].expediente.inicio").value(dia + "T07:00:00-03:00"))
                .andExpect(jsonPath("$.barbeiros[0].expediente.fim").value(dia + "T20:00:00-03:00"))
                .andExpect(jsonPath("$.barbeiros[0].expediente.almoco.inicio").value(dia + "T12:00:00-03:00"))
                .andExpect(jsonPath("$.barbeiros[0].expediente.almoco.fim").value(dia + "T13:00:00-03:00"));
    }

    @Test
    @DisplayName("A26: jornada de terça a sábado: domingo 06/12 e segunda 07/12 dão 200, horarios [] e expediente fechado FORA_DO_EXPEDIENTE com inicio, fim e almoco null")
    void foraDoExpediente() throws Exception {
        fechado(disponibilidade(LocalDate.of(2026, 12, 6), eduardo), "$.barbeiros[0]", "FORA_DO_EXPEDIENTE");
        fechado(disponibilidade(LocalDate.of(2026, 12, 7), eduardo), "$.barbeiros[0]", "FORA_DO_EXPEDIENTE");
    }

    @Test
    @DisplayName("A27: terça 08/12 sem folga: expediente aberto, motivoFechado null, das 07:00 às 20:00 e almoço das 12:00 às 13:00, tudo com -03:00")
    void diaNormal() throws Exception {
        aberto(disponibilidade(LocalDate.of(2026, 12, 8), eduardo), "2026-12-08");
    }

    @Test
    @DisplayName("A28: jornada de terça com um intervalo só (07:00–20:00): almoco null, inicio 07:00 e fim 20:00")
    void umIntervaloSo() throws Exception {
        jdbc.update("DELETE FROM jornada WHERE barbeiro_id = ? AND dia_semana = 2", eduardo);
        jdbc.update("INSERT INTO jornada (barbeiro_id, dia_semana, hora_inicio, hora_fim) VALUES (?, 2, '07:00', '20:00')",
                eduardo);

        disponibilidade(LocalDate.of(2026, 12, 8), eduardo)
                .andExpect(jsonPath("$.barbeiros[0].expediente.aberto").value(true))
                .andExpect(jsonPath("$.barbeiros[0].expediente.inicio").value("2026-12-08T07:00:00-03:00"))
                .andExpect(jsonPath("$.barbeiros[0].expediente.fim").value("2026-12-08T20:00:00-03:00"))
                .andExpect(jsonPath("$.barbeiros[0].expediente.almoco").value(nullValue()));
    }

    @Test
    @DisplayName("A29: nas férias do A1 (21/12 a 03/01), 22/12 dá horarios [] e expediente FECHADO com tudo null; nem FERIAS nem o motivo aparecem na resposta")
    void ferias() throws Exception {
        criarFolga(corpo(FERIAS_INICIO, FERIAS_FIM, "FERIAS", "viagem de família", null)).andExpect(status().isCreated());

        ResultActions resposta = disponibilidade(LocalDate.of(2026, 12, 22), eduardo);
        fechado(resposta, "$.barbeiros[0]", "FECHADO");
        String corpo = resposta.andReturn().getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("FERIAS").doesNotContain("FOLGA").doesNotContain("viagem").doesNotContain("tipo")
                .doesNotContain("motivo\"");
    }

    @Test
    @DisplayName("A30: uma FOLGA de 05/12 00:00 a 06/12 00:00 (o sábado inteiro) fecha o dia igual às férias: aberto false, FECHADO")
    void folgaDoDiaInteiro() throws Exception {
        criarFolga(corpo(dez(5, 0, 0), dez(6, 0, 0), "FOLGA", null, null)).andExpect(status().isCreated());

        ResultActions resposta = disponibilidade(LocalDate.of(2026, 12, 5), eduardo);
        fechado(resposta, "$.barbeiros[0]", "FECHADO");
        assertThat(resposta.andReturn().getResponse().getContentAsString()).doesNotContain("FOLGA\"");
    }

    @Test
    @DisplayName("A31: folga só de 05/12 14:00 a 16:00: o dia continua aberto com início, fim e almoço normais; só somem os inícios que invadiriam 14:00–16:00")
    void folgaParcial() throws Exception {
        criarFolga(corpo(dez(5, 14, 0), dez(5, 16, 0), "FOLGA", "compromisso", null)).andExpect(status().isCreated());

        aberto(disponibilidade(LocalDate.of(2026, 12, 5), eduardo), "2026-12-05");
        assertThat(horasDe(LocalDate.of(2026, 12, 5)))
                .contains("13:30", "16:00").doesNotContain("14:00", "14:30", "15:00", "15:30").hasSize(20);
    }

    @Test
    @DisplayName("A32: uma FOLGA e umas FERIAS cobrindo juntas o mesmo sábado: o público vê FECHADO; o painel (ADMIN) lista as duas com tipo e motivo")
    void folgaEFeriasNoMesmoDia() throws Exception {
        criarFolga(corpo(dez(12, 0, 0), dez(12, 14, 0), "FOLGA", "médico", null)).andExpect(status().isCreated());
        criarFolga(corpo(dez(12, 12, 0), dez(13, 0, 0), "FERIAS", "descanso", null)).andExpect(status().isCreated());

        fechado(disponibilidade(LocalDate.of(2026, 12, 12), eduardo), "$.barbeiros[0]", "FECHADO");
        get(rotaDasFolgas(eduardo), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tipo", contains("FOLGA", "FERIAS")))
                .andExpect(jsonPath("$[*].motivo", contains("médico", "descanso")));
    }

    @Test
    @DisplayName("A33: dois barbeiros e só o Eduardo de férias em 22/12: sem barbeiroId, o Eduardo vem fechado (FECHADO) e o outro aberto com os horários dele")
    void doisBarbeiros() throws Exception {
        UUID outro = DadosDeTeste.barbeiro(jdbc, true);
        criarFolga(corpo(FERIAS_INICIO, FERIAS_FIM, "FERIAS", null, null)).andExpect(status().isCreated());

        ResultActions resposta = disponibilidade(LocalDate.of(2026, 12, 22), null);
        String doEduardo = "$.barbeiros[?(@.barbeiroId == '" + eduardo + "')]";
        String doOutro = "$.barbeiros[?(@.barbeiroId == '" + outro + "')]";
        resposta.andExpect(jsonPath(doEduardo + ".expediente.aberto", contains(false)))
                .andExpect(jsonPath(doEduardo + ".expediente.motivoFechado", contains("FECHADO")))
                .andExpect(jsonPath(doEduardo + ".horarios[*]", empty()))
                .andExpect(jsonPath(doOutro + ".expediente.aberto", contains(true)))
                .andExpect(jsonPath(doOutro + ".expediente.motivoFechado", contains(nullValue())))
                .andExpect(jsonPath(doOutro + ".horarios[*]", not(empty())));
    }
}
