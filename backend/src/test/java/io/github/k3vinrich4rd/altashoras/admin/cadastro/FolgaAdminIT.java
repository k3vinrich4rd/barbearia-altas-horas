package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.CadastrosBase;

/** A57 a A61: folgas do painel (criar, listar, remover) e a regra de nunca esconder um horário que já tem cliente. */
class FolgaAdminIT extends CadastrosBase {

    private static final LocalDate TERCA_DE_TESTE = LocalDate.of(2026, 12, 1);

    private UUID barbeiro;
    private UUID servico;
    private Conta admin;

    @BeforeEach
    void dados() {
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        admin = admin();
    }

    private static String periodo(OffsetDateTime inicio, OffsetDateTime fim, String motivo) {
        String m = motivo == null ? "" : ", \"motivo\": \"" + motivo + "\"";
        return """
                { "inicio": "%s", "fim": "%s"%s }
                """.formatted(inicio, fim, m);
    }

    private ResultActions criarFolga(UUID doBarbeiro, OffsetDateTime inicio, OffsetDateTime fim) throws Exception {
        return postJson("/admin/barbeiros/" + doBarbeiro + "/folgas", admin, periodo(inicio, fim, "Consulta"));
    }

    private long folgasGravadas() {
        return jdbc.queryForObject("SELECT count(*) FROM folga WHERE barbeiro_id = ?", Long.class, barbeiro);
    }

    @Test
    @DisplayName("A57: folga de 01/12 das 14:00 às 16:00 (201) deixa 20 horários (somem 14:00, 14:30, 15:00 e 15:30; 16:00 continua) e POST /agendamentos às 14:30 é 422 FORA_DO_EXPEDIENTE")
    void folgaEscondeOsHorarios() throws Exception {
        assertThat(horas(servico, barbeiro, TERCA_DE_TESTE)).hasSize(24);

        criarFolga(barbeiro, as(14, 0), as(16, 0)).andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/admin/barbeiros/" + barbeiro + "/folgas/")))
                .andExpect(jsonPath("$.inicio").value("2026-12-01T14:00:00-03:00"))
                .andExpect(jsonPath("$.fim").value("2026-12-01T16:00:00-03:00"))
                .andExpect(jsonPath("$.motivo").value("Consulta"));

        assertThat(horas(servico, barbeiro, TERCA_DE_TESTE)).hasSize(20).contains("13:30", "16:00")
                .doesNotContain("14:00", "14:30", "15:00", "15:30");
        reservar(conta("CLIENTE"), servico, barbeiro, as(14, 30)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FORA_DO_EXPEDIENTE"));
    }

    @Test
    @DisplayName("A58: folga que atravessa uma reserva CONFIRMADO ou AGUARDANDO_PAGAMENTO válida é 422 FOLGA_COM_RESERVAS e nada é gravado; folga que só encosta na reserva das 15:00 vale (201)")
    void naoEscondeHorarioComCliente() throws Exception {
        UUID cliente = cliente("Maria Souza Teste", "11988880001");
        reserva(barbeiro, servico, as(15, 0), "CONFIRMADO", null, cliente, null);
        reserva(barbeiro, servico, as(15, 30), "AGUARDANDO_PAGAMENTO", RelogioFixoDeTeste.AGORA.plusSeconds(600), null,
                "Visitante Dois");

        criarFolga(barbeiro, as(14, 0), as(16, 0)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FOLGA_COM_RESERVAS"));
        assertThat(folgasGravadas()).isZero();
        criarFolga(barbeiro, as(14, 0), as(15, 0)).andExpect(status().isCreated());
        assertThat(folgasGravadas()).isEqualTo(1);
        // Só a reserva pendente válida (15:30 a 16:00) já basta para impedir.
        criarFolga(barbeiro, as(15, 45), as(17, 0)).andExpect(status().isUnprocessableEntity());
        assertThat(folgasGravadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("A59: reservas CANCELADO, EXPIRADO, CONCLUIDO e AGUARDANDO_PAGAMENTO vencida dentro de 14:00 a 16:00 não impedem a folga (201)")
    void soReservaAtivaImpede() throws Exception {
        UUID cliente = cliente("Maria Souza Teste", "11988880001");
        reserva(barbeiro, servico, as(14, 0), "CANCELADO", null, cliente, null);
        reserva(barbeiro, servico, as(14, 30), "EXPIRADO", null, null, "Visitante Dois");
        reserva(barbeiro, servico, as(15, 0), "CONCLUIDO", null, cliente, null);
        reserva(barbeiro, servico, as(15, 30), "AGUARDANDO_PAGAMENTO", RelogioFixoDeTeste.AGORA.minusSeconds(60), null,
                "Visitante Tres");

        criarFolga(barbeiro, as(14, 0), as(16, 0)).andExpect(status().isCreated());
        assertThat(folgasGravadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("A60: folga com fim igual ou antes do início, toda no passado, motivo de 201 caracteres e campo extra são 400")
    void corposInvalidos() throws Exception {
        String rota = "/admin/barbeiros/" + barbeiro + "/folgas";
        postJson(rota, admin, periodo(as(14, 0), as(14, 0), null)).andExpect(status().isBadRequest());
        postJson(rota, admin, periodo(as(16, 0), as(14, 0), null)).andExpect(status().isBadRequest());
        OffsetDateTime passado = DadosDeTeste.em(LocalDate.of(2026, 11, 10), 10, 0);
        postJson(rota, admin, periodo(passado, passado.plusHours(2), null)).andExpect(status().isBadRequest());
        postJson(rota, admin, periodo(as(14, 0), as(16, 0), "x".repeat(201))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("motivo"));
        postJson(rota, admin, """
                { "inicio": "%s", "fim": "%s", "extra": true }
                """.formatted(as(14, 0), as(16, 0))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));
        assertThat(folgasGravadas()).isZero();
    }

    @Test
    @DisplayName("A61: GET lista só as folgas de hoje em diante; DELETE dá 204 e a disponibilidade volta a 24; DELETE repetido e com folgaId de outro barbeiro são 404")
    void listarERemover() throws Exception {
        UUID outro = DadosDeTeste.barbeiro(jdbc, true);
        OffsetDateTime passado = DadosDeTeste.em(LocalDate.of(2026, 11, 10), 10, 0);
        DadosDeTeste.folga(jdbc, barbeiro, passado, passado.plusHours(2));
        String criada = idDe(criarFolga(barbeiro, as(14, 0), as(16, 0)).andExpect(status().isCreated()));
        String daOutro = idDe(criarFolga(outro, as(10, 0), as(11, 0)).andExpect(status().isCreated()));
        String rota = "/admin/barbeiros/" + barbeiro + "/folgas";

        String lista = get(rota, admin).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(lista, "$[*].id");
        assertThat(ids).containsExactly(criada);
        assertThat(horas(servico, barbeiro, TERCA_DE_TESTE)).hasSize(20);

        delete(rota + "/" + daOutro, admin).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM folga WHERE id = ?::uuid", Long.class, daOutro)).isEqualTo(1);
        delete(rota + "/" + criada, admin).andExpect(status().isNoContent());
        assertThat(horas(servico, barbeiro, TERCA_DE_TESTE)).hasSize(24);
        delete(rota + "/" + criada, admin).andExpect(status().isNotFound());
        get("/admin/barbeiros/" + UUID.randomUUID() + "/folgas", admin).andExpect(status().isNotFound());
    }
}
