package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A8 e A17: a prévia GET /folgas/conflitos (E12, E24) lista quem já está marcado, só lê e não confirma barbeiro inexistente. */
class ConflitosDeFolgaIT extends FeriasBase {

    @Test
    @DisplayName("A8: com a CONFIRMADO de 22/12 15:00–15:30 e a AGUARDANDO_PAGAMENTO no prazo de 15:30–16:00, GET /folgas/conflitos de 21/12 a 23/12 dá 200 com as 2 em ordem de início, com reservaId, inicio, fim, servico, clienteNome, origem e status; sem e-mail, CPF, WhatsApp nem observação; nada gravado")
    void previaListaAsAtivas() throws Exception {
        UUID[] reservas = reservasDoA8();
        // Encostar não é conflito (R2): a reserva das 14:30 termina às 15:00, a prévia começa no dia 21.
        String corpo = conflitos(eduardo, admin, dez(21, 0, 0), dez(23, 0, 0)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reservaId").value(reservas[0].toString()))
                .andExpect(jsonPath("$[0].inicio").value("2026-12-22T15:00:00-03:00"))
                .andExpect(jsonPath("$[0].fim").value("2026-12-22T15:30:00-03:00"))
                .andExpect(jsonPath("$[0].servico.id").value(servico.toString()))
                .andExpect(jsonPath("$[0].servico.nome").value(org.hamcrest.Matchers.startsWith("Corte")))
                .andExpect(jsonPath("$[0].clienteNome").value("Cliente Exemplo"))
                .andExpect(jsonPath("$[0].origem").value("CLIENTE"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMADO"))
                .andExpect(jsonPath("$[1].reservaId").value(reservas[1].toString()))
                .andExpect(jsonPath("$[1].inicio").value("2026-12-22T15:30:00-03:00"))
                .andExpect(jsonPath("$[1].origem").value("GUEST"))
                .andExpect(jsonPath("$[1].status").value("AGUARDANDO_PAGAMENTO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("@", "email", "cpf", "whatsapp", "observacao", "11988880001", "11988880002");
        assertThat(folgasGravadas()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_admin", Long.class)).isZero();
        conflitos(eduardo, admin, dez(22, 16, 0), dez(22, 18, 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("A17: GET /folgas/conflitos de um barbeiro inexistente dá 404")
    void barbeiroInexistente() throws Exception {
        conflitos(UUID.randomUUID(), admin, dez(21, 0, 0), dez(23, 0, 0)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
    }
}
