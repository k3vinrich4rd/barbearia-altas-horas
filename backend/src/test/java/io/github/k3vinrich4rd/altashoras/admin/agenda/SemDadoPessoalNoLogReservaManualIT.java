package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/** A28 da Fatia 6D: reserva manual e WhatsApp do barbeiro não deixam nome, observação, número nem token no log (R30). */
@ExtendWith(OutputCaptureExtension.class)
class SemDadoPessoalNoLogReservaManualIT extends PainelBase {

    private void criar(Conta quem, String corpo) throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/admin/agendamentos").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + quem.token()).content(corpo));
    }

    @Test
    @DisplayName("A28: criar, fechar, cancelar e errar uma reserva manual, mais cadastrar o WhatsApp do barbeiro, não deixam nome, observação, WhatsApp nem token no log")
    void nadaNoLog(CapturedOutput saida) throws Exception {
        Massa m = massa();
        String ok = """
                { "servicoId": "%s", "barbeiroId": "%s", "inicio": "%s", "nomeCliente": "Zeferino Ficticio",
                  "observacao": "pediu barba e pagou em dinheiro" }
                """;
        criar(m.contaA(), ok.formatted(m.servico(), m.barbeiroA(), as(15, 0)));
        criar(m.contaA(), ok.formatted(m.servico(), m.barbeiroA(), as(15, 0))); // 409
        criar(m.contaA(), ok.formatted(m.servico(), m.barbeiroB(), as(16, 0))); // 404
        criar(m.contaA(), ok.formatted(m.servico(), m.barbeiroA(), as(12, 0))); // 422
        criar(m.contaA(), ok.replace("}", ", \"whatsapp\": \"11977776666\" }").formatted(m.servico(), m.barbeiroA(), as(16, 0))); // 400
        UUID id = jdbc.queryForObject("SELECT id FROM reserva WHERE origem = 'MANUAL'", UUID.class);
        patchStatus(id, m.admin(), corpoDeCancelamento("Zeferino desistiu", 0));
        mvc.perform(MockMvcRequestBuilders.post("/admin/barbeiros").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + m.admin().token())
                .content("{ \"nomeExibicao\": \"Barbeiro Novo\", \"whatsapp\": \"(11) 95555-4444\" }"));
        get("/admin/agenda?data=2026-12-01", m.admin());
        mvc.perform(MockMvcRequestBuilders.get("/barbeiros"));
        esperarOutboxVazio();

        assertThat(saida.getAll()).doesNotContain("Zeferino").doesNotContain("pediu barba").doesNotContain("dinheiro")
                .doesNotContain("11977776666").doesNotContain("11955554444").doesNotContain("95555-4444")
                .doesNotContain(m.contaA().token()).doesNotContain(m.admin().token()).doesNotContain("eyJ");
    }
}
