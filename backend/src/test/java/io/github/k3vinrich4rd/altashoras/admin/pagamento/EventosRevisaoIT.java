package io.github.k3vinrich4rd.altashoras.admin.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/** A32 e A33: eventos do gateway que esgotaram as tentativas (H20) e o "marcar revisado" (E14). */
class EventosRevisaoIT extends PainelBase {

    private UUID evento(String resultado, OffsetDateTime recebidoEm) {
        return jdbc.queryForObject("""
                INSERT INTO evento_webhook (provedor, id_evento_provedor, tipo, id_cobranca_provedor, recebido_em, resultado,
                                            tentativas, ultimo_erro)
                VALUES ('SIMULADO', ?, 'PAYMENT_RECEIVED', ?, ?, ?, 5, 'SEM_COBRANCA')
                RETURNING id
                """, UUID.class, "evt_" + UUID.randomUUID(), "sim_" + UUID.randomUUID(), recebidoEm, resultado);
    }

    @Test
    @DisplayName("A32: a lista traz só os eventos em REVISAO_MANUAL, o mais antigo primeiro, com id, tipo, recebidoEm, tentativas e ultimoErro e sem payload nem dado pessoal")
    void listar() throws Exception {
        Conta admin = conta("ADMIN", "BARBEIRO");
        UUID das9 = evento("REVISAO_MANUAL", as(9, 0));
        UUID das8 = evento("REVISAO_MANUAL", as(8, 0));
        evento("PROCESSADO", as(7, 0));

        String corpo = get("/admin/pagamentos/eventos", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[0].id").value(das8.toString()))
                .andExpect(jsonPath("$.itens[1].id").value(das9.toString()))
                .andExpect(jsonPath("$.itens[0].tipo").value("PAYMENT_RECEIVED"))
                .andExpect(jsonPath("$.itens[0].recebidoEm").value("2026-12-01T08:00:00-03:00"))
                .andExpect(jsonPath("$.itens[0].tentativas").value(5))
                .andExpect(jsonPath("$.itens[0].ultimoErro").value("SEM_COBRANCA"))
                .andExpect(jsonPath("$.itens[0].idCobrancaProvedor").isNotEmpty())
                .andExpect(jsonPath("$.itens[0].payload").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("@").doesNotContainIgnoringCase("payload").doesNotContainIgnoringCase("cpf")
                .doesNotContainIgnoringCase("email");
    }

    @Test
    @DisplayName("A33: marcar revisado dá 200 (REVISADO, processadoEm preenchido), tira da lista e audita EVENTO_REVISADO; revisar de novo é 422 e id inexistente é 404")
    void revisar() throws Exception {
        Conta admin = conta("ADMIN", "BARBEIRO");
        UUID evento = evento("REVISAO_MANUAL", as(8, 0));

        post("/admin/pagamentos/eventos/" + evento + "/revisao", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("REVISADO"))
                .andExpect(jsonPath("$.processadoEm").value("2026-12-01T09:30:00-03:00"));

        assertThat(jdbc.queryForObject("SELECT resultado FROM evento_webhook WHERE id = ?", String.class, evento))
                .isEqualTo("REVISADO");
        assertThat(jdbc.queryForObject("SELECT processado_em FROM evento_webhook WHERE id = ?", OffsetDateTime.class, evento))
                .isNotNull();
        get("/admin/pagamentos/eventos", admin).andExpect(jsonPath("$.total").value(0));
        assertThat(jdbc.queryForList("SELECT acao FROM auditoria_admin WHERE recurso_id = ?", String.class, evento))
                .containsExactly("EVENTO_REVISADO");

        post("/admin/pagamentos/eventos/" + evento + "/revisao", admin).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSICAO_INVALIDA"));
        post("/admin/pagamentos/eventos/" + UUID.randomUUID() + "/revisao", admin).andExpect(status().isNotFound());
        assertThat(linhasDeAuditoria()).isEqualTo(1);
    }
}
