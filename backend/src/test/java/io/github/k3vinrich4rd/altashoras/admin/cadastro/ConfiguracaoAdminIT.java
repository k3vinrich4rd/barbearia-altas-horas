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

    private static String corpo(int intervalo, int granularidade, int expiracao, long version) {
        return """
                { "intervaloEntreAtendimentosMin": %d, "granularidadeMin": %d, "expiracaoPagamentoMin": %d, "version": %d }
                """.formatted(intervalo, granularidade, expiracao, version);
    }

    private void aindaComOsValoresDaV1() {
        assertThat(jdbc.queryForMap("""
                SELECT intervalo_entre_atendimentos_min i, granularidade_min g, expiracao_pagamento_min e, version v
                FROM configuracao_agenda
                """)).containsEntry("i", 0).containsEntry("g", 30).containsEntry("e", 3).containsEntry("v", 0L);
    }

    @Test
    @DisplayName("A64: GET /admin/configuracao devolve a configuração inicial: intervalo 0, granularidade 30, expiração 3, fuso America/Sao_Paulo e version 0 (sem antecedência nem horizonte, D197)")
    void leituraInicial() throws Exception {
        get(ROTA, admin).andExpect(status().isOk()).andExpect(jsonPath("$.intervaloEntreAtendimentosMin").value(0))
                .andExpect(jsonPath("$.granularidadeMin").value(30))
                .andExpect(jsonPath("$.expiracaoPagamentoMin").value(3))
                .andExpect(jsonPath("$.fuso").value("America/Sao_Paulo")).andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("A65: reservar às 10:15 é 422 FORA_DO_EXPEDIENTE (a grade é de 30 em 30); depois do PUT com granularidade 15 (version 1) a mesma reserva é 201, sem reiniciar a API")
    void granularidadeMudaNaHora() throws Exception {
        Conta cliente = conta("CLIENTE");
        OffsetDateTime quinzeDepoisDaDez = DadosDeTeste.em(LocalDate.of(2026, 12, 1), 10, 15);

        reservar(cliente, servico, barbeiro, quinzeDepoisDaDez).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FORA_DO_EXPEDIENTE"));

        put(ROTA, admin, corpo(0, 15, 3, 0)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.granularidadeMin").value(15)).andExpect(jsonPath("$.fuso").value("America/Sao_Paulo"));

        reservar(cliente, servico, barbeiro, quinzeDepoisDaDez).andExpect(status().isCreated());
        get(ROTA, admin).andExpect(jsonPath("$.granularidadeMin").value(15)).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("A66: com expiracaoPagamentoMin 30 a reserva nova expira em agora + 30 min, e a feita antes da mudança mantém o expira_em antigo")
    void expiracaoNaoReescreveOPassado() throws Exception {
        Conta cliente = conta("CLIENTE");
        UUID antes = UUID.fromString(JsonPath.read(reservar(cliente, servico, barbeiro, as(11, 0))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));

        put(ROTA, admin, corpo(0, 30, 30, 0)).andExpect(status().isOk());
        UUID depois = UUID.fromString(JsonPath.read(reservar(cliente, servico, barbeiro, as(10, 0))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));

        assertThat(jdbc.queryForObject("SELECT expira_em FROM reserva WHERE id = ?", OffsetDateTime.class, antes)
                .toInstant()).isEqualTo(RelogioFixoDeTeste.AGORA.plusSeconds(3 * 60));
        assertThat(jdbc.queryForObject("SELECT expira_em FROM reserva WHERE id = ?", OffsetDateTime.class, depois)
                .toInstant()).isEqualTo(RelogioFixoDeTeste.AGORA.plusSeconds(30 * 60));
    }

    @Test
    @DisplayName("A67: intervalo 121, granularidade 25, expiração 2 e 61 são 400 VALIDACAO_INVALIDA no campo certo; fuso no corpo é 400 CAMPO_DESCONHECIDO; nada é gravado")
    void valoresInvalidos() throws Exception {
        String[][] casos = {
                {corpo(121, 30, 3, 0), "intervaloEntreAtendimentosMin"},
                {corpo(0, 25, 3, 0), "granularidadeMin"},
                {corpo(0, 30, 2, 0), "expiracaoPagamentoMin"},
                {corpo(0, 30, 61, 0), "expiracaoPagamentoMin"}};
        for (String[] caso : casos) {
            put(ROTA, admin, caso[0]).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                    .andExpect(jsonPath("$.errors[0].field").value(caso[1]));
        }
        put(ROTA, admin, """
                { "fuso": "UTC", "intervaloEntreAtendimentosMin": 0, "granularidadeMin": 30, "expiracaoPagamentoMin": 3, "version": 0 }
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));

        aindaComOsValoresDaV1();
    }

    @Test
    @DisplayName("A68: PUT com version 0 quando a configuração já está na version 1 é 409 CONFLITO_DE_VERSAO e a configuração fica intacta")
    void versaoVelha() throws Exception {
        put(ROTA, admin, corpo(0, 30, 5, 0)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));

        put(ROTA, admin, corpo(10, 15, 20, 0)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLITO_DE_VERSAO"));

        assertThat(jdbc.queryForMap("""
                SELECT intervalo_entre_atendimentos_min i, granularidade_min g, expiracao_pagamento_min e, version v
                FROM configuracao_agenda
                """)).containsEntry("i", 0).containsEntry("g", 30).containsEntry("e", 5).containsEntry("v", 1L);
    }
}
