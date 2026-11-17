package io.github.k3vinrich4rd.altashoras.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** A39: o fluxo do painel inteiro não deixa nome, WhatsApp, e-mail, token, CPF nem motivo no log (R30). */
@ExtendWith(OutputCaptureExtension.class)
class SemDadoPessoalNoLogPainelIT extends PainelBase {

    @Test
    @DisplayName("A39: agenda, fechar, cancelar com motivo, devolver e os caminhos de erro não deixam nome, WhatsApp, e-mail, token, CPF nem motivo no log")
    void nadaNoLog(CapturedOutput saida) throws Exception {
        Massa m = massa();
        UUID emDevolucao = cobranca(reserva(m.barbeiroA(), m.servico(), as(0, 0), "CANCELADO", null, m.clienteDaR1(), null),
                "DEVOLUCAO_PENDENTE", as(8, 0));
        String emailDoCliente = jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, m.clienteDaR1());
        String emailDoVisitante = jdbc.queryForObject("SELECT email FROM reserva_guest WHERE reserva_id = ?", String.class, m.r2());

        get("/admin/agenda?data=2026-12-01", m.contaA());
        get("/admin/agenda?data=2026-12-01&visao=SEMANA", m.admin());
        get("/admin/pagamentos", m.admin());
        patchStatus(m.r1(), m.contaA(), corpoDeStatus("CONCLUIDO", versaoDa(m.r1())));
        patchStatus(m.r2(), m.admin(), corpoDeCancelamento("Cliente João pediu para desmarcar", versaoDa(m.r2())));
        patchStatus(m.r6(), m.contaA(), corpoDeStatus("CONCLUIDO", 0)); // 404
        patchStatus(m.r5(), m.admin(), corpoDeCancelamento("Cliente João pediu para desmarcar", 0)); // 422
        patchStatus(m.r1(), m.admin(), corpoDeCancelamento("Cliente João pediu para desmarcar", 99)); // 409
        patchStatus(m.r6(), m.admin(), corpoDeCancelamento("x".repeat(301), 0)); // 400
        post("/admin/pagamentos/" + emDevolucao + "/devolucao", m.admin());
        post("/admin/pagamentos/" + emDevolucao + "/devolucao", m.admin()); // 422
        get("/admin/auditoria", m.admin());
        esperarOutboxVazio();

        assertThat(saida.getAll())
                .doesNotContain("Maria Souza").doesNotContain("Visitante Dois").doesNotContain("Ana Costa")
                .doesNotContain("11988880001").doesNotContain("11988880002").doesNotContain("11988880006")
                .doesNotContain(emailDoCliente).doesNotContain(emailDoVisitante).doesNotContain("@exemplo.test")
                .doesNotContain("Cliente João").doesNotContain("desmarcar")
                .doesNotContain(m.admin().token()).doesNotContain(m.contaA().token()).doesNotContain("eyJ")
                .doesNotContain("52998224725").doesNotContain("529.982.247-25");
    }
}
