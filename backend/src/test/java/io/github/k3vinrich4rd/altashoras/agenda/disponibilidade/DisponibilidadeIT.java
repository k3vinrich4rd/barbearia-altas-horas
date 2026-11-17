package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/**
 * A rota GET /disponibilidade de ponta a ponta, no PostgreSQL real. "Agora" é terça, 17/11/2026, 08h00
 * (RelogioFixoDeTeste); a terça 01/12/2026 é o dia de teste. Cada teste cria o próprio barbeiro e serviços.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class DisponibilidadeIT {

    private static final LocalDate TERCA = LocalDate.of(2026, 12, 1);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory emf;

    private UUID barbeiro;
    private UUID servico30;
    private UUID servico60;

    @BeforeEach
    void dados() {
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico30 = DadosDeTeste.servico(jdbc, 30);
        servico60 = DadosDeTeste.servico(jdbc, 60);
    }

    private ResultActions consultar(Object servicoId, Object data, UUID barbeiroId) throws Exception {
        var pedido = get("/disponibilidade");
        if (servicoId != null) {
            pedido.param("servicoId", servicoId.toString());
        }
        if (data != null) {
            pedido.param("data", data.toString());
        }
        if (barbeiroId != null) {
            pedido.param("barbeiroId", barbeiroId.toString());
        }
        return mvc.perform(pedido);
    }

    /** Horários do barbeiro do teste, só a hora ("10:00"), para a asserção ficar legível. */
    private List<String> horas(UUID servicoId, LocalDate data) throws Exception {
        String corpo = consultar(servicoId, data, barbeiro).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> horarios = JsonPath.read(corpo, "$.barbeiros[0].horarios");
        return horarios.stream().map(h -> h.substring(11, 16)).toList();
    }

    private void erro(ResultActions resposta, int statusEsperado, String codigo) throws Exception {
        resposta.andExpect(status().is(statusEsperado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(codigo))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // ---- a grade ----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("A6: terça livre, serviço de 30 min: 24 horários, sem 12:00 e 12:30")
    void tercaLivre30() throws Exception {
        assertThat(horas(servico30, TERCA)).hasSize(24).startsWith("07:00").endsWith("19:30")
                .doesNotContain("12:00", "12:30");
    }

    @Test
    @DisplayName("A7: serviço de 60 min: 22 horários, sem 11:30 e sem 19:30")
    void tercaLivre60() throws Exception {
        assertThat(horas(servico60, TERCA)).hasSize(22).doesNotContain("11:30", "19:30").endsWith("19:00");
    }

    @Test
    @DisplayName("A8: segunda e domingo respondem 200 com horarios vazio")
    void diasFechados() throws Exception {
        assertThat(horas(servico30, LocalDate.of(2026, 11, 30))).isEmpty();
        assertThat(horas(servico30, LocalDate.of(2026, 12, 6))).isEmpty();
    }

    // ---- o que ocupa ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("A12: reserva CONFIRMADO 10:00-10:30 tira só o 10:00 do serviço de 30 min")
    void confirmadoOcupa() throws Exception {
        DadosDeTeste.reserva(jdbc, barbeiro, servico30, DadosDeTeste.em(TERCA, 10, 0), "CONFIRMADO", null);
        assertThat(horas(servico30, TERCA)).hasSize(23).contains("09:30", "10:30").doesNotContain("10:00");
    }

    @Test
    @DisplayName("A13: a mesma reserva tira 09:30 e 10:00 do serviço de 60 min")
    void confirmadoOcupaServicoLongo() throws Exception {
        DadosDeTeste.reserva(jdbc, barbeiro, servico30, DadosDeTeste.em(TERCA, 10, 0), "CONFIRMADO", null);
        assertThat(horas(servico60, TERCA)).contains("09:00", "10:30").doesNotContain("09:30", "10:00");
    }

    @Test
    @DisplayName("A14: AGUARDANDO_PAGAMENTO ainda no prazo ocupa o horário")
    void pendenteNoPrazoOcupa() throws Exception {
        DadosDeTeste.reserva(jdbc, barbeiro, servico30, DadosDeTeste.em(TERCA, 10, 0), "AGUARDANDO_PAGAMENTO",
                RelogioFixoDeTeste.AGORA.plus(Duration.ofMinutes(10)));
        assertThat(horas(servico30, TERCA)).doesNotContain("10:00");
    }

    @Test
    @DisplayName("A15: AGUARDANDO_PAGAMENTO vencida não ocupa, mesmo sem o job de expiração ter rodado (R15)")
    void pendenteVencidaNaoOcupa() throws Exception {
        DadosDeTeste.reserva(jdbc, barbeiro, servico30, DadosDeTeste.em(TERCA, 10, 0), "AGUARDANDO_PAGAMENTO",
                RelogioFixoDeTeste.AGORA.minus(Duration.ofMinutes(1)));
        assertThat(horas(servico30, TERCA)).hasSize(24).contains("10:00");
    }

    @Test
    @DisplayName("A16: reservas EXPIRADO e CANCELADO não ocupam")
    void statusFinaisNaoOcupam() throws Exception {
        DadosDeTeste.reserva(jdbc, barbeiro, servico30, DadosDeTeste.em(TERCA, 10, 0), "EXPIRADO", null);
        DadosDeTeste.reserva(jdbc, barbeiro, servico30, DadosDeTeste.em(TERCA, 10, 0), "CANCELADO", null);
        assertThat(horas(servico30, TERCA)).hasSize(24).contains("10:00");
    }

    @Test
    @DisplayName("A17: folga 14:00-16:00 tira 14:00 a 15:30 (30 min) e também 13:30 (60 min)")
    void folgaParcial() throws Exception {
        DadosDeTeste.folga(jdbc, barbeiro, DadosDeTeste.em(TERCA, 14, 0), DadosDeTeste.em(TERCA, 16, 0));
        assertThat(horas(servico30, TERCA)).hasSize(20).contains("13:30", "16:00").doesNotContain("14:00", "15:30");
        assertThat(horas(servico60, TERCA)).contains("13:00", "16:00").doesNotContain("13:30", "15:00");
    }

    @Test
    @DisplayName("A19: reserva de outro barbeiro não ocupa o horário deste")
    void outroBarbeiroNaoOcupa() throws Exception {
        UUID outro = DadosDeTeste.barbeiro(jdbc, true);
        DadosDeTeste.reserva(jdbc, outro, servico30, DadosDeTeste.em(TERCA, 10, 0), "CONFIRMADO", null);
        assertThat(horas(servico30, TERCA)).hasSize(24).contains("10:00");
    }

    // ---- entrada e saída --------------------------------------------------------------------------------------------

    @Test
    @DisplayName("A20: hoje + 30 dias (17/12) ainda está dentro do horizonte")
    void limiteDoHorizonte() throws Exception {
        assertThat(horas(servico30, LocalDate.of(2026, 12, 17))).hasSize(24);
    }

    @Test
    @DisplayName("A21: hoje + 31 dias e ontem dão 422 FORA_DO_HORIZONTE")
    void foraDoHorizonte() throws Exception {
        erro(consultar(servico30, LocalDate.of(2026, 12, 18), barbeiro), 422, "FORA_DO_HORIZONTE");
        erro(consultar(servico30, LocalDate.of(2026, 11, 16), barbeiro), 422, "FORA_DO_HORIZONTE");
    }

    @Test
    @DisplayName("A22: serviço que não existe dá 404 RECURSO_NAO_ENCONTRADO")
    void servicoInexistente() throws Exception {
        erro(consultar(UUID.randomUUID(), TERCA, barbeiro), 404, "RECURSO_NAO_ENCONTRADO");
    }

    @Test
    @DisplayName("A23: serviço inativo dá 422 SERVICO_INATIVO")
    void servicoInativo() throws Exception {
        UUID inativo = DadosDeTeste.servico(jdbc, "Inativo", 30, 5500, false);
        erro(consultar(inativo, TERCA, barbeiro), 422, "SERVICO_INATIVO");
    }

    @Test
    @DisplayName("A24: barbeiro inexistente ou inativo dá 404 RECURSO_NAO_ENCONTRADO")
    void barbeiroInexistenteOuInativo() throws Exception {
        erro(consultar(servico30, TERCA, UUID.randomUUID()), 404, "RECURSO_NAO_ENCONTRADO");
        erro(consultar(servico30, TERCA, DadosDeTeste.barbeiro(jdbc, false)), 404, "RECURSO_NAO_ENCONTRADO");
    }

    @Test
    @DisplayName("A25: data ausente, data malformada e servicoId malformado dão 400 VALIDACAO_INVALIDA com o campo em errors")
    void parametrosInvalidos() throws Exception {
        erro(consultar(servico30, null, null), 400, "VALIDACAO_INVALIDA");
        consultar(servico30, null, null).andExpect(jsonPath("$.errors[0].field").value("data"));
        consultar(servico30, "amanha", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors[0].field").value("data"));
        consultar("abc", TERCA, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("servicoId"));
        consultar(null, TERCA, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("servicoId"));
    }

    @Test
    @DisplayName("A26: sem barbeiroId vêm todos os barbeiros ativos, cada um com os próprios horários")
    void todosOsBarbeiros() throws Exception {
        UUID outro = DadosDeTeste.barbeiro(jdbc, true);
        UUID inativo = DadosDeTeste.barbeiro(jdbc, false);
        DadosDeTeste.reserva(jdbc, outro, servico30, DadosDeTeste.em(TERCA, 10, 0), "CONFIRMADO", null);

        String corpo = consultar(servico30, TERCA, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(corpo, "$.barbeiros[*].barbeiroId");
        List<List<String>> meus = JsonPath.read(corpo, "$.barbeiros[?(@.barbeiroId=='" + barbeiro + "')].horarios");
        List<List<String>> doOutro = JsonPath.read(corpo, "$.barbeiros[?(@.barbeiroId=='" + outro + "')].horarios");

        assertThat(ids).contains(barbeiro.toString(), outro.toString()).doesNotContain(inativo.toString());
        assertThat(meus.getFirst()).hasSize(24);
        assertThat(doOutro.getFirst()).hasSize(23);
    }

    @Test
    @DisplayName("A27: resposta com data, fuso, servicoId, duracaoMin, horários crescentes no formato do contrato e no-store")
    void formatoDaResposta() throws Exception {
        String corpo = consultar(servico60, TERCA, barbeiro).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data").value("2026-12-01"))
                .andExpect(jsonPath("$.fuso").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.servicoId").value(servico60.toString()))
                .andExpect(jsonPath("$.duracaoMin").value(60))
                .andExpect(jsonPath("$.barbeiros[0].barbeiroId").value(barbeiro.toString()))
                .andExpect(jsonPath("$.barbeiros[0].nomeExibicao").value("Barbeiro " + barbeiro))
                .andExpect(jsonPath("$.barbeiros[0].horarios[0]").value("2026-12-01T07:00:00-03:00"))
                .andReturn().getResponse().getContentAsString();
        List<String> horarios = JsonPath.read(corpo, "$.barbeiros[0].horarios");
        assertThat(horarios).isSorted().allMatch(h -> h.matches("2026-12-01T\\d{2}:\\d{2}:00-03:00"));
    }

    @Test
    @DisplayName("A29: o número de consultas SQL não cresce com o número de barbeiros (sem N+1)")
    void semNMaisUm() throws Exception {
        Statistics estatisticas = emf.unwrap(SessionFactory.class).getStatistics();
        estatisticas.setStatisticsEnabled(true);

        long antes = estatisticas.getPrepareStatementCount();
        consultar(servico30, TERCA, null).andExpect(status().isOk());
        long comOsAtuais = estatisticas.getPrepareStatementCount() - antes;

        for (int i = 0; i < 5; i++) {
            DadosDeTeste.barbeiro(jdbc, true);
        }
        antes = estatisticas.getPrepareStatementCount();
        consultar(servico30, TERCA, null).andExpect(status().isOk());
        long comMaisCinco = estatisticas.getPrepareStatementCount() - antes;

        assertThat(comMaisCinco).isEqualTo(comOsAtuais).isLessThanOrEqualTo(4);
    }
}
