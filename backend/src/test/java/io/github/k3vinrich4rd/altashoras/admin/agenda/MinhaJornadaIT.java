package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/**
 * A61 da Fatia 7B (D142, contrato 1.7.0): {@code GET /admin/minha-jornada}, a jornada do barbeiro da própria conta. É o
 * que a agenda do BARBEIRO precisa para listrar o almoço, já que ele não lê {@code /admin/barbeiros}.
 */
class MinhaJornadaIT extends PainelBase {

    private static final String ROTA = "/admin/minha-jornada";

    @Test
    @DisplayName("A61: o BARBEIRO lê só a jornada do barbeiro ligado à conta dele (terça a sábado, 07:00-12:00 e 13:00-20:00); a de outro barbeiro nunca aparece; conta sem vínculo é 404; CLIENTE 403, sem token 401, senha inicial 403")
    void barbeiroLeAPropriaJornada() throws Exception {
        Massa m = massa();
        jdbc.update("DELETE FROM jornada WHERE barbeiro_id = ?", m.barbeiroB());
        jdbc.update("INSERT INTO jornada (barbeiro_id, dia_semana, hora_inicio, hora_fim) VALUES (?, 1, '10:00', '11:00')",
                m.barbeiroB());

        get(ROTA, m.contaA()).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(10)).andExpect(jsonPath("$[0].diaSemana").value(2))
                .andExpect(jsonPath("$[0].horaInicio").value("07:00")).andExpect(jsonPath("$[0].horaFim").value("12:00"))
                .andExpect(jsonPath("$[1].horaInicio").value("13:00")).andExpect(jsonPath("$[1].horaFim").value("20:00"))
                .andExpect(jsonPath("$[?(@.diaSemana == 1)]").isEmpty());
        get(ROTA, m.contaB()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].diaSemana").value(1)).andExpect(jsonPath("$[0].horaInicio").value("10:00"));

        get(ROTA, conta("BARBEIRO")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        get(ROTA, m.admin()).andExpect(status().isNotFound());
        get(ROTA, conta("CLIENTE")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        get(ROTA, conta(true, "BARBEIRO")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TROCA_DE_SENHA_OBRIGATORIA"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(ROTA))
                .andExpect(status().isUnauthorized());
    }
}
