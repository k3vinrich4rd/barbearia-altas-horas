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

/**
 * A57 a A61 (Fatia 5): folgas do painel (criar, listar, remover) e a regra de nunca esconder um horário que já tem
 * cliente. A1, A4, A7 e A9 (Fatia 15, ADR 0015): tipo, resposta FolgaSalva, auditoria e a lista de conflitos no 422.
 */
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

    // ---- Fatia 15 (relógio em ter 01/12/2026 10:00, como a seção 5 da spec) ---------------------------------------

    private void relogioDaFatia15() {
        irPara(TERCA_DE_TESTE, 10, 0);
        admin = admin();
    }

    private static OffsetDateTime dez(int dia, int hora, int minuto) {
        return DadosDeTeste.em(LocalDate.of(2026, 12, dia), hora, minuto);
    }

    private String detalheDaAuditoria(String acao) {
        return jdbc.queryForObject("SELECT detalhe::text FROM auditoria_admin WHERE acao = ? ORDER BY ocorrido_em DESC LIMIT 1",
                String.class, acao);
    }

    private long auditorias(String acao) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE acao = ?", Long.class, acao);
    }

    private String rota() {
        return "/admin/barbeiros/" + barbeiro + "/folgas";
    }

    @Test
    @DisplayName("A1: POST de férias de 21/12 00:00 a 03/01 00:00 com tipo FERIAS e sem reserva no período dá 201 FolgaSalva com tipo FERIAS, reservasMantidas [] e Location; auditoria FOLGA_CRIADA com o tipo")
    void feriasCriadas() throws Exception {
        relogioDaFatia15();
        String corpo = """
                { "inicio": "%s", "fim": "%s", "tipo": "FERIAS", "motivo": "Viagem de fim de ano" }
                """.formatted(dez(21, 0, 0), DadosDeTeste.em(LocalDate.of(2027, 1, 3), 0, 0));
        var resposta = postJson(rota(), admin, corpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("FERIAS"))
                .andExpect(jsonPath("$.inicio").value("2026-12-21T00:00:00-03:00"))
                .andExpect(jsonPath("$.fim").value("2027-01-03T00:00:00-03:00"))
                .andExpect(jsonPath("$.motivo").value("Viagem de fim de ano"))
                .andExpect(jsonPath("$.atualizadaEm").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.reservasMantidas").isEmpty())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        String id = JsonPath.read(resposta.getContentAsString(), "$.id");
        assertThat(resposta.getHeader("Location")).isEqualTo(rota() + "/" + id);
        assertThat(jdbc.queryForObject("SELECT tipo FROM folga WHERE id = ?::uuid", String.class, id)).isEqualTo("FERIAS");
        assertThat(detalheDaAuditoria("FOLGA_CRIADA")).contains("\"tipo\": \"FERIAS\"").contains(barbeiro.toString())
                .doesNotContain("Viagem");
    }

    @Test
    @DisplayName("A4: POST sem o campo tipo (o corpo do front de hoje) dá 201 com tipo FOLGA, e o GET lista a folga com tipo FOLGA")
    void semTipoViraFolga() throws Exception {
        relogioDaFatia15();
        criarFolga(barbeiro, dez(5, 14, 0), dez(5, 16, 0)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("FOLGA")).andExpect(jsonPath("$.reservasMantidas").isEmpty());
        get(rota(), admin).andExpect(status().isOk()).andExpect(jsonPath("$[0].tipo").value("FOLGA"))
                .andExpect(jsonPath("$[0].atualizadaEm").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("A7: DELETE da folga de 05/12 dá 204, os horários das 14:00 às 15:30 voltam à disponibilidade e a auditoria grava FOLGA_REMOVIDA")
    void removerDevolveOsHorarios() throws Exception {
        relogioDaFatia15();
        LocalDate sabado = LocalDate.of(2026, 12, 5);
        String id = idDe(criarFolga(barbeiro, dez(5, 14, 0), dez(5, 16, 0)).andExpect(status().isCreated()));
        assertThat(horas(servico, barbeiro, sabado)).doesNotContain("14:00", "14:30", "15:00", "15:30");

        delete(rota() + "/" + id, admin).andExpect(status().isNoContent());

        assertThat(horas(servico, barbeiro, sabado)).hasSize(24).contains("14:00", "14:30", "15:00", "15:30");
        assertThat(auditorias("FOLGA_REMOVIDA")).isEqualTo(1);
        assertThat(detalheDaAuditoria("FOLGA_REMOVIDA")).contains(barbeiro.toString()).doesNotContain("Consulta");
    }

    @Test
    @DisplayName("A9: com a CONFIRMADO e a AGUARDANDO_PAGAMENTO no prazo do A8, mais uma AGUARDANDO_PAGAMENTO vencida e uma CANCELADO no período, o POST sem manterReservas dá 422 FOLGA_COM_RESERVAS com conflitos[] listando só as 2 ativas; nada gravado e sem auditoria")
    void conflitosNo422() throws Exception {
        relogioDaFatia15();
        UUID cliente = cliente("Cliente Exemplo", "11988880001");
        UUID confirmada = reserva(barbeiro, servico, dez(22, 15, 0), "CONFIRMADO", null, cliente, null);
        UUID pendente = reserva(barbeiro, servico, dez(22, 15, 30), "AGUARDANDO_PAGAMENTO",
                relogio.instant().plusSeconds(120), null, "Visitante Dois");
        reserva(barbeiro, servico, dez(22, 16, 0), "AGUARDANDO_PAGAMENTO", relogio.instant().minusSeconds(1), null,
                "Visitante Vencido");
        reserva(barbeiro, servico, dez(22, 16, 30), "CANCELADO", null, cliente, null);
        String corpo = """
                { "inicio": "%s", "fim": "%s", "tipo": "FERIAS" }
                """.formatted(dez(21, 0, 0), DadosDeTeste.em(LocalDate.of(2027, 1, 3), 0, 0));

        postJson(rota(), admin, corpo).andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("FOLGA_COM_RESERVAS"))
                .andExpect(jsonPath("$.conflitos.length()").value(2))
                .andExpect(jsonPath("$.conflitos[0].reservaId").value(confirmada.toString()))
                .andExpect(jsonPath("$.conflitos[0].inicio").value("2026-12-22T15:00:00-03:00"))
                .andExpect(jsonPath("$.conflitos[0].fim").value("2026-12-22T15:30:00-03:00"))
                .andExpect(jsonPath("$.conflitos[0].servico.id").value(servico.toString()))
                .andExpect(jsonPath("$.conflitos[0].servico.nome").value(org.hamcrest.Matchers.startsWith("Corte Clássico")))
                .andExpect(jsonPath("$.conflitos[0].clienteNome").value("Cliente Exemplo"))
                .andExpect(jsonPath("$.conflitos[0].origem").value("CLIENTE"))
                .andExpect(jsonPath("$.conflitos[0].status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.conflitos[1].reservaId").value(pendente.toString()))
                .andExpect(jsonPath("$.conflitos[1].clienteNome").value("Visitante Dois"))
                .andExpect(jsonPath("$.conflitos[1].origem").value("GUEST"))
                .andExpect(jsonPath("$.conflitos[1].status").value("AGUARDANDO_PAGAMENTO"));
        assertThat(folgasGravadas()).isZero();
        assertThat(auditorias("FOLGA_CRIADA")).isZero();
    }
}
