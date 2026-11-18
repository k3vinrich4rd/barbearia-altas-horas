package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.CadastrosBase;

/** A64 a A68: as regras da agenda mudam sem deploy, valem na próxima requisição e não reescrevem o que já foi feito. */
class ConfiguracaoAdminIT extends CadastrosBase {

    private static final String ROTA = "/admin/configuracao";

    private UUID barbeiro;
    private UUID servico;
    private Conta admin;

    @BeforeEach
    void dados() {
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        admin = admin();
    }

    private static String corpo(int antecedencia, int intervalo, int granularidade, int horizonte, int expiracao,
            long version) {
        return """
                { "antecedenciaMinimaMin": %d, "intervaloEntreAtendimentosMin": %d, "granularidadeMin": %d,
                  "horizonteDias": %d, "expiracaoPagamentoMin": %d, "version": %d }
                """.formatted(antecedencia, intervalo, granularidade, horizonte, expiracao, version);
    }

    private void aindaComOsValoresDaV1() {
        assertThat(jdbc.queryForMap("""
                SELECT antecedencia_minima_min a, intervalo_entre_atendimentos_min i, granularidade_min g,
                       horizonte_dias h, expiracao_pagamento_min e, version v
                FROM configuracao_agenda
                """)).containsEntry("a", 120).containsEntry("i", 0).containsEntry("g", 30).containsEntry("h", 30)
                .containsEntry("e", 3).containsEntry("v", 0L);
    }

    @Test
    @DisplayName("A64: GET /admin/configuracao devolve a configuração inicial: antecedência 120, intervalo 0, granularidade 30, horizonte 30, expiração 3, fuso America/Sao_Paulo e version 0")
    void leituraInicial() throws Exception {
        get(ROTA, admin).andExpect(status().isOk()).andExpect(jsonPath("$.antecedenciaMinimaMin").value(120))
                .andExpect(jsonPath("$.intervaloEntreAtendimentosMin").value(0))
                .andExpect(jsonPath("$.granularidadeMin").value(30)).andExpect(jsonPath("$.horizonteDias").value(30))
                .andExpect(jsonPath("$.expiracaoPagamentoMin").value(3))
                .andExpect(jsonPath("$.fuso").value("America/Sao_Paulo")).andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("A65: reservar 90 min à frente é 422 ANTECEDENCIA_INSUFICIENTE; depois do PUT com antecedência 60 (version 1) a mesma reserva é 201, sem reiniciar a API")
    void antecedenciaMudaNaHora() throws Exception {
        Conta cliente = conta("CLIENTE");
        OffsetDateTime meiaHoraDepoisDeUmaEMeia = DadosDeTeste.em(LocalDate.of(2026, 11, 17), 9, 30);

        reservar(cliente, servico, barbeiro, meiaHoraDepoisDeUmaEMeia).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ANTECEDENCIA_INSUFICIENTE"));

        put(ROTA, admin, corpo(60, 0, 30, 30, 3, 0)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.antecedenciaMinimaMin").value(60)).andExpect(jsonPath("$.fuso").value("America/Sao_Paulo"));

        reservar(cliente, servico, barbeiro, meiaHoraDepoisDeUmaEMeia).andExpect(status().isCreated());
        get(ROTA, admin).andExpect(jsonPath("$.antecedenciaMinimaMin").value(60)).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("A66: com expiracaoPagamentoMin 30 a reserva nova expira em agora + 30 min, e a feita antes da mudança mantém o expira_em antigo")
    void expiracaoNaoReescreveOPassado() throws Exception {
        Conta cliente = conta("CLIENTE");
        UUID antes = UUID.fromString(JsonPath.read(reservar(cliente, servico, barbeiro, as(11, 0))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));

        put(ROTA, admin, corpo(120, 0, 30, 30, 30, 0)).andExpect(status().isOk());
        UUID depois = UUID.fromString(JsonPath.read(reservar(cliente, servico, barbeiro, as(10, 0))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));

        assertThat(jdbc.queryForObject("SELECT expira_em FROM reserva WHERE id = ?", OffsetDateTime.class, antes)
                .toInstant()).isEqualTo(RelogioFixoDeTeste.AGORA.plusSeconds(3 * 60));
        assertThat(jdbc.queryForObject("SELECT expira_em FROM reserva WHERE id = ?", OffsetDateTime.class, depois)
                .toInstant()).isEqualTo(RelogioFixoDeTeste.AGORA.plusSeconds(30 * 60));
    }

    @Test
    @DisplayName("A67: antecedência -1, intervalo 121, granularidade 25, horizonte 0 e 366, expiração 2 e 61 são 400 VALIDACAO_INVALIDA no campo certo; fuso no corpo é 400 CAMPO_DESCONHECIDO; nada é gravado")
    void valoresInvalidos() throws Exception {
        String[][] casos = {
                {corpo(-1, 0, 30, 30, 3, 0), "antecedenciaMinimaMin"},
                {corpo(120, 121, 30, 30, 3, 0), "intervaloEntreAtendimentosMin"},
                {corpo(120, 0, 25, 30, 3, 0), "granularidadeMin"},
                {corpo(120, 0, 30, 0, 3, 0), "horizonteDias"},
                {corpo(120, 0, 30, 366, 3, 0), "horizonteDias"},
                {corpo(120, 0, 30, 30, 2, 0), "expiracaoPagamentoMin"},
                {corpo(120, 0, 30, 30, 61, 0), "expiracaoPagamentoMin"}};
        for (String[] caso : casos) {
            put(ROTA, admin, caso[0]).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                    .andExpect(jsonPath("$.errors[0].field").value(caso[1]));
        }
        put(ROTA, admin, """
                { "fuso": "UTC", "antecedenciaMinimaMin": 120, "intervaloEntreAtendimentosMin": 0, "granularidadeMin": 30,
                  "horizonteDias": 30, "expiracaoPagamentoMin": 3, "version": 0 }
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));

        aindaComOsValoresDaV1();
    }

    @Test
    @DisplayName("A68: PUT com version 0 quando a configuração já está na version 1 é 409 CONFLITO_DE_VERSAO e a configuração fica intacta")
    void versaoVelha() throws Exception {
        put(ROTA, admin, corpo(60, 0, 30, 30, 3, 0)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));

        put(ROTA, admin, corpo(240, 10, 15, 60, 20, 0)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLITO_DE_VERSAO"));

        assertThat(jdbc.queryForMap("""
                SELECT antecedencia_minima_min a, intervalo_entre_atendimentos_min i, granularidade_min g,
                       horizonte_dias h, expiracao_pagamento_min e, version v
                FROM configuracao_agenda
                """)).containsEntry("a", 60).containsEntry("i", 0).containsEntry("g", 30).containsEntry("h", 30)
                .containsEntry("e", 3).containsEntry("v", 1L);
    }
}
