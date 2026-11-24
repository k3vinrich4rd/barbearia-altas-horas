package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A44 e A45 da Fatia 14C: a grade do dia do painel ({@code GET /admin/agenda/dia}). */
class GradeDoDiaIT extends AgendaNovaBase {

    @Test
    @DisplayName("A44: ter 01/12 do Eduardo: granularidade 30; 26 horários de 07:00 a 19:30; 09:00, 09:30, 10:00, 10:30, 15:00 e 15:30 OCUPADO com o agendamentoId; 12:00 e 12:30 ALMOCO; os outros 18 LIVRE (expirada e cancelada não ocupam); agendamentos com as 3 que ocupam")
    void gradeDeTerca() throws Exception {
        MassaDaAgenda m = massaDaAgenda();
        String rota = "/admin/agenda/dia?data=2026-12-01&barbeiroId=" + m.barbeiroEduardo();
        get(rota, m.eduardo()).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        String corpo = corpo(rota, m.eduardo());

        assertThat(JsonPath.<Integer>read(corpo, "$.granularidadeMin")).isEqualTo(30);
        assertThat(JsonPath.<String>read(corpo, "$.data")).isEqualTo("2026-12-01");
        assertThat(JsonPath.<List<String>>read(corpo, "$.colunas[*].barbeiroId"))
                .containsExactly(m.barbeiroEduardo().toString());
        List<String> horarios = JsonPath.read(corpo, "$.colunas[0].horarios[*].inicio");
        assertThat(horarios).hasSize(26).startsWith("07:00", "07:30").endsWith("19:00", "19:30");
        assertThat(inicios(corpo, m.barbeiroEduardo(), "OCUPADO"))
                .containsExactly("09:00", "09:30", "10:00", "10:30", "15:00", "15:30");
        assertThat(inicios(corpo, m.barbeiroEduardo(), "ALMOCO")).containsExactly("12:00", "12:30");
        assertThat(inicios(corpo, m.barbeiroEduardo(), "LIVRE")).hasSize(18).contains("16:00", "17:00");
        assertThat(JsonPath.<List<String>>read(corpo, "$.colunas[0].horarios[?(@.inicio == '09:30')].agendamentoId"))
                .containsExactly(m.r0900().toString());
        assertThat(JsonPath.<List<String>>read(corpo, "$.colunas[0].horarios[?(@.inicio == '15:00')].agendamentoId"))
                .containsExactly(m.r1500().toString());
        assertThat(JsonPath.<List<Object>>read(corpo, "$.colunas[0].horarios[?(@.situacao == 'LIVRE')].agendamentoId"))
                .allMatch(id -> id == null);
        assertThat(JsonPath.<List<String>>read(corpo, "$.colunas[0].agendamentos[*].id"))
                .containsExactly(m.r0900().toString(), m.r1000().toString(), m.r1500().toString());
    }

    @Test
    @DisplayName("A45: qua 02/12 do Eduardo: 14:00, 14:30, 15:00 e 15:30 são FOLGA; dom 06/12: horarios vazio")
    void folgaEDomingo() throws Exception {
        MassaDaAgenda m = massaDaAgenda();
        String quarta = corpo("/admin/agenda/dia?data=2026-12-02&barbeiroId=" + m.barbeiroEduardo(), m.eduardo());
        assertThat(inicios(quarta, m.barbeiroEduardo(), "FOLGA")).containsExactly("14:00", "14:30", "15:00", "15:30");
        assertThat(situacoes(quarta, m.barbeiroEduardo())).hasSize(26);

        String domingo = corpo("/admin/agenda/dia?data=2026-12-06&barbeiroId=" + m.barbeiroEduardo(), m.eduardo());
        assertThat(situacoes(domingo, m.barbeiroEduardo())).isEmpty();
        assertThat(JsonPath.<List<String>>read(domingo, "$.colunas[*].barbeiroId"))
                .containsExactly(m.barbeiroEduardo().toString());
    }
}
