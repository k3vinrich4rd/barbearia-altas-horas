package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.ContadorDeSql;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/** A1 a A7: a agenda do dia e da semana no painel, com o escopo do barbeiro e sem N+1. */
class AgendaAdminIT extends PainelBase {

    private static final String DIA = "/admin/agenda?data=2026-12-01";

    private List<String> ids(String corpo) {
        return JsonPath.read(corpo, "$[*].id");
    }

    @Test
    @DisplayName("A1: o barbeiro A vê R1, R2 e R5 em ordem (R3 expirada e R4 vencida ficam de fora) com o contato e sem e-mail, token ou CPF")
    void agendaDoBarbeiro() throws Exception {
        Massa m = massa();

        String corpo = get(DIA, m.contaA()).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].status").value("CONFIRMADO"))
                .andExpect(jsonPath("$[0].inicio").value("2026-12-01T09:00:00-03:00"))
                .andExpect(jsonPath("$[0].servico.nome").value(startsWith("Corte Clássico")))
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[0].cliente.nome").value("Maria Souza Teste"))
                .andExpect(jsonPath("$[0].cliente.whatsapp").value("11988880001"))
                .andExpect(jsonPath("$[0].cobrancaStatus").value("PAGA"))
                .andExpect(jsonPath("$[1].status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$[1].cliente.nome").value("Visitante Dois"))
                .andExpect(jsonPath("$[1].cobrancaStatus").value((Object) null))
                .andExpect(jsonPath("$[2].status").value("CANCELADO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(corpo)).containsExactly(m.r1().toString(), m.r2().toString(), m.r5().toString());
        assertThat(corpo).doesNotContain("@").doesNotContainIgnoringCase("token").doesNotContainIgnoringCase("cpf")
                .doesNotContainIgnoringCase("email");
    }

    @Test
    @DisplayName("A2: o ADMIN vê as 4 reservas dos dois barbeiros em ordem; com barbeiroId do B, só a R6")
    void agendaDoAdmin() throws Exception {
        Massa m = massa();

        String todas = get(DIA, m.admin()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(todas)).containsExactly(m.r1().toString(), m.r6().toString(), m.r2().toString(), m.r5().toString());

        String doB = get(DIA + "&barbeiroId=" + m.barbeiroB(), m.admin()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(doB)).containsExactly(m.r6().toString());
    }

    @Test
    @DisplayName("A3: a visão SEMANA de uma quinta vai de segunda a domingo: 5 itens, sem o domingo anterior nem a segunda seguinte")
    void semana() throws Exception {
        Massa m = massa();
        UUID domingoAnterior = reserva(m.barbeiroA(), m.servico(), DadosDeTeste.em(LocalDate.of(2026, 11, 29), 23, 30),
                "CONCLUIDO", null, m.clienteDaR1(), null);
        UUID segunda = reserva(m.barbeiroA(), m.servico(), DadosDeTeste.em(LocalDate.of(2026, 11, 30), 0, 0),
                "CONCLUIDO", null, m.clienteDaR1(), null);
        UUID domingo = reserva(m.barbeiroA(), m.servico(), DadosDeTeste.em(LocalDate.of(2026, 12, 6), 23, 30),
                "CONFIRMADO", null, m.clienteDaR1(), null);
        UUID segundaSeguinte = reserva(m.barbeiroA(), m.servico(), DadosDeTeste.em(LocalDate.of(2026, 12, 7), 0, 0),
                "CONFIRMADO", null, m.clienteDaR1(), null);

        String corpo = get("/admin/agenda?data=2026-12-03&visao=SEMANA", m.contaA()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(corpo)).containsExactly(segunda.toString(), m.r1().toString(), m.r2().toString(),
                m.r5().toString(), domingo.toString())
                .doesNotContain(domingoAnterior.toString(), segundaSeguinte.toString());
    }

    @Test
    @DisplayName("A4: o dia é o da barbearia, não o do UTC: 23:00 de 01/12 (02:00 UTC de 02/12) fica em 01/12 e 00:00 de 02/12 fica em 02/12")
    void diaNoFusoDaBarbearia() throws Exception {
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        Conta barbeiro = conta("BARBEIRO");
        UUID id = barbeiroDaConta(barbeiro.id());
        UUID cliente = cliente("Maria Souza Teste", "11988880001");
        UUID noite = reserva(id, servico, as(23, 0), "CONFIRMADO", null, cliente, null);
        UUID meiaNoite = reserva(id, servico, DadosDeTeste.em(LocalDate.of(2026, 12, 2), 0, 0), "CONFIRMADO", null,
                cliente, null);

        assertThat(ids(get("/admin/agenda?data=2026-12-01", barbeiro).andReturn().getResponse().getContentAsString()))
                .containsExactly(noite.toString());
        assertThat(ids(get("/admin/agenda?data=2026-12-02", barbeiro).andReturn().getResponse().getContentAsString()))
                .containsExactly(meiaNoite.toString());
    }

    @Test
    @DisplayName("A5: o número de consultas SQL da agenda é o mesmo com 2 e com 40 reservas (sem N+1)")
    void semNMaisUm() throws Exception {
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        Conta barbeiro = conta("BARBEIRO");
        UUID id = barbeiroDaConta(barbeiro.id());
        UUID cliente = cliente("Maria Souza Teste", "11988880001");
        for (int i = 0; i < 2; i++) {
            cobranca(reserva(id, servico, as(7 + i, 0), "CONCLUIDO", null, cliente, null), "PAGA", as(6, 0));
        }
        get(DIA, barbeiro).andExpect(status().isOk());

        long antes = ContadorDeSql.total();
        get(DIA, barbeiro).andExpect(jsonPath("$.length()").value(2));
        long comDuas = ContadorDeSql.total() - antes;

        for (int i = 0; i < 38; i++) {
            // CONCLUIDO não entra na constraint de sobreposição: o teste só precisa de volume, não de agenda realista.
            UUID reserva = reserva(id, servico, as(0, 0).plusMinutes(30L * i), "CONCLUIDO", null,
                    i % 2 == 0 ? cliente : null, "Visitante " + i);
            cobranca(reserva, "PAGA", as(6, 0));
        }
        antes = ContadorDeSql.total();
        get(DIA, barbeiro).andExpect(jsonPath("$.length()").value(40));
        long comQuarenta = ContadorDeSql.total() - antes;

        assertThat(comQuarenta).isEqualTo(comDuas).isBetween(3L, 5L);
    }

    @Test
    @DisplayName("A6: o BARBEIRO não vê o barbeiro alheio nem sem vínculo (404); o ADMIN com barbeiro inexistente também recebe 404")
    void escopoDoBarbeiro() throws Exception {
        Massa m = massa();

        get(DIA + "&barbeiroId=" + m.barbeiroB(), m.contaA()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        get(DIA, conta("BARBEIRO")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        get(DIA + "&barbeiroId=" + UUID.randomUUID(), m.admin()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));

        String propria = get(DIA, m.contaA()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(propria)).containsExactly(m.r1().toString(), m.r2().toString(), m.r5().toString());
        String comOProprioId = get(DIA + "&barbeiroId=" + m.barbeiroA(), m.contaA()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(comOProprioId)).hasSize(3);
    }

    @Test
    @DisplayName("A7: sem data, data fora do formato ISO ou visão desconhecida: 400 VALIDACAO_INVALIDA")
    void pedidoInvalido() throws Exception {
        Conta barbeiro = massa().contaA();

        for (String rota : List.of("/admin/agenda", "/admin/agenda?data=31-12-2026",
                "/admin/agenda?data=2026-12-01&visao=MES")) {
            get(rota, barbeiro).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        }
    }
}
