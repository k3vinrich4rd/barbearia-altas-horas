package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/** A10 a A14 e A16 a A18: concluir e marcar falta, com a máquina de estados, o início, a versão e o escopo. */
class FechamentoAtendimentoIT extends PainelBase {

    @Test
    @DisplayName("A10: o barbeiro conclui a R1: 200, CONCLUIDO, version v+1, finalizado_por e finalizado_em gravados e a cobrança continua PAGA")
    void concluir() throws Exception {
        Massa m = massa();
        long v = versaoDa(m.r1());

        patchStatus(m.r1(), m.contaA(), corpoDeStatus("CONCLUIDO", v)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.version").value(v + 1))
                .andExpect(jsonPath("$.cobrancaStatus").value("PAGA"));

        assertThat(statusDaReserva(m.r1())).isEqualTo("CONCLUIDO");
        assertThat(jdbc.queryForObject("SELECT finalizado_por FROM reserva WHERE id = ?", UUID.class, m.r1()))
                .isEqualTo(m.contaA().id());
        assertThat(jdbc.queryForObject("SELECT finalizado_em FROM reserva WHERE id = ?", OffsetDateTime.class, m.r1())
                .toInstant()).isEqualTo(as(9, 30).toInstant());
        assertThat(statusDaCobrancaDaReserva(m.r1())).isEqualTo("PAGA");
    }

    @Test
    @DisplayName("A11: marcar falta mantém a cobrança PAGA (valor retido, R19) e não cria pendência de devolução")
    void faltou() throws Exception {
        Massa m = massa();

        patchStatus(m.r1(), m.contaA(), corpoDeStatus("NAO_COMPARECEU", versaoDa(m.r1()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAO_COMPARECEU"));

        assertThat(statusDaCobrancaDaReserva(m.r1())).isEqualTo("PAGA");
        get("/admin/pagamentos", m.admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.itens.length()").value(0));
    }

    @Test
    @DisplayName("A12: às 09:30 em ponto (início inclusivo) concluir dá 200; às 09:29 dá 422 TRANSICAO_INVALIDA e a reserva fica intacta")
    void aPartirDoInicio() throws Exception {
        Massa m = massa();
        UUID noInicio = reserva(m.barbeiroA(), m.servico(), as(9, 30), "CONFIRMADO", null, m.clienteDaR1(), null);

        patchStatus(noInicio, m.contaA(), corpoDeStatus("CONCLUIDO", versaoDa(noInicio))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        irPara(TERCA, 9, 29);
        long v = versaoDa(m.r6());
        patchStatus(m.r6(), m.admin(), corpoDeStatus("CONCLUIDO", v)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSICAO_INVALIDA"));
        assertThat(statusDaReserva(m.r6())).isEqualTo("CONFIRMADO");
        assertThat(versaoDa(m.r6())).isEqualTo(v);
        assertThat(linhasDeAuditoria()).isEqualTo(1);
    }

    @Test
    @DisplayName("A13: a máquina de estados barra concluir e cancelar reservas que não estão CONFIRMADO: 422 em cada uma, sem gravar nem auditar")
    void maquinaDeEstados() throws Exception {
        Massa m = massa();
        UUID aguardando = reserva(m.barbeiroA(), m.servico(), as(16, 0), "AGUARDANDO_PAGAMENTO", as(9, 45).toInstant(),
                m.clienteDaR1(), null);
        UUID expirada = reserva(m.barbeiroA(), m.servico(), as(16, 30), "EXPIRADO", null, m.clienteDaR1(), null);
        UUID concluida = reserva(m.barbeiroA(), m.servico(), as(17, 0), "CONCLUIDO", null, m.clienteDaR1(), null);
        UUID faltou = reserva(m.barbeiroA(), m.servico(), as(17, 30), "NAO_COMPARECEU", null, m.clienteDaR1(), null);
        UUID cancelada = m.r5();
        long auditoriaAntes = linhasDeAuditoria();

        for (UUID id : new UUID[] {aguardando, expirada, concluida, faltou, cancelada}) {
            patchStatus(id, m.admin(), corpoDeStatus("CONCLUIDO", versaoDa(id))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("TRANSICAO_INVALIDA"));
        }
        for (UUID id : new UUID[] {expirada, concluida, faltou, cancelada}) {
            patchStatus(id, m.admin(), corpoDeCancelamento("Barbeiro doente", versaoDa(id)))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TRANSICAO_INVALIDA"));
        }

        assertThat(statusDaReserva(aguardando)).isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(statusDaReserva(expirada)).isEqualTo("EXPIRADO");
        assertThat(statusDaReserva(concluida)).isEqualTo("CONCLUIDO");
        assertThat(statusDaReserva(faltou)).isEqualTo("NAO_COMPARECEU");
        assertThat(statusDaReserva(cancelada)).isEqualTo("CANCELADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE finalizado_em IS NOT NULL OR cancelado_em IS NOT NULL",
                Long.class)).isZero();
        assertThat(linhasDeAuditoria()).isEqualTo(auditoriaAntes).isZero();
    }

    @Test
    @DisplayName("A14: version desatualizada dá 409 CONFLITO_DE_VERSAO e a reserva continua CONFIRMADO, sem auditoria")
    void versaoDesatualizada() throws Exception {
        Massa m = massa();
        jdbc.update("UPDATE reserva SET version = 3 WHERE id = ?", m.r1());

        patchStatus(m.r1(), m.contaA(), corpoDeStatus("CONCLUIDO", 2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLITO_DE_VERSAO"));

        assertThat(statusDaReserva(m.r1())).isEqualTo("CONFIRMADO");
        assertThat(versaoDa(m.r1())).isEqualTo(3);
        assertThat(linhasDeAuditoria()).isZero();
    }

    @Test
    @DisplayName("A16: o barbeiro A recebe 404 ao fechar a reserva do barbeiro B e a de id inexistente; a R6 fica intacta")
    void reservaAlheia() throws Exception {
        Massa m = massa();
        long v = versaoDa(m.r6());

        patchStatus(m.r6(), m.contaA(), corpoDeStatus("CONCLUIDO", v)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        patchStatus(UUID.randomUUID(), m.contaA(), corpoDeStatus("CONCLUIDO", 0)).andExpect(status().isNotFound());

        assertThat(statusDaReserva(m.r6())).isEqualTo("CONFIRMADO");
        assertThat(versaoDa(m.r6())).isEqualTo(v);
        assertThat(linhasDeAuditoria()).isZero();
    }

    @Test
    @DisplayName("A17: o BARBEIRO não cancela: 403 ACESSO_NEGADO e a R1 fica intacta")
    void barbeiroNaoCancela() throws Exception {
        Massa m = massa();
        long v = versaoDa(m.r1());

        patchStatus(m.r1(), m.contaA(), corpoDeCancelamento("Barbeiro doente", v)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));

        assertThat(statusDaReserva(m.r1())).isEqualTo("CONFIRMADO");
        assertThat(versaoDa(m.r1())).isEqualTo(v);
        assertThat(jdbc.queryForObject("SELECT motivo_cancelamento FROM reserva WHERE id = ?", String.class, m.r1())).isNull();
        assertThat(linhasDeAuditoria()).isZero();
    }

    @Test
    @DisplayName("A18: status fora de CONCLUIDO, NAO_COMPARECEU e CANCELADO, version ausente ou campo desconhecido dão 400 e nada é gravado")
    void corpoInvalido() throws Exception {
        Massa m = massa();
        long v = versaoDa(m.r1());

        patchStatus(m.r1(), m.admin(), corpoDeStatus("CONFIRMADO", v)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        patchStatus(m.r1(), m.admin(), corpoDeStatus("EXPIRADO", v)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        patchStatus(m.r1(), m.admin(), "{ \"status\": \"CONCLUIDO\" }").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        patchStatus(m.r1(), m.admin(), "{ \"status\": \"CONCLUIDO\", \"version\": " + v + ", \"precoCentavos\": 1 }")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));

        assertThat(statusDaReserva(m.r1())).isEqualTo("CONFIRMADO");
        assertThat(versaoDa(m.r1())).isEqualTo(v);
        assertThat(linhasDeAuditoria()).isZero();
    }
}
