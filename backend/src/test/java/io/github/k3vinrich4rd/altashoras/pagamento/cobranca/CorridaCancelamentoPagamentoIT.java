package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.GatewaySimulado;

/** A24: a janela entre confirmar a reserva e marcar a cobrança paga, com o cancelamento da barbearia no meio. */
class CorridaCancelamentoPagamentoIT extends PainelBase {

    @Autowired
    ProcessarPagamento processar;

    @Autowired
    GatewaySimulado gateway;

    @Test
    @DisplayName("A24: cancelamento (e o listener) entre a confirmação da reserva e a marcação da cobrança não vira PAGA: termina PAGA_APOS_EXPIRACAO, pendência do painel")
    void cancelouNaJanelaDoPagamento() throws Exception {
        Conta admin = conta("ADMIN", "BARBEIRO");
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        UUID reserva = reserva(barbeiro, servico, as(10, 0), "AGUARDANDO_PAGAMENTO", as(9, 45).toInstant(),
                cliente("Maria Souza Teste", "11988880001"), null);
        jdbc.update("UPDATE reserva SET aceite_politica_versao = 1, aceite_politica_em = ? WHERE id = ?", as(9, 0), reserva);
        cobranca(reserva, "PENDENTE", as(9, 0));
        String idProvedor = jdbc.queryForObject("SELECT id_cobranca_provedor FROM cobranca WHERE reserva_id = ?",
                String.class, reserva);
        gateway.marcarPago(idProvedor);

        processar.aposConfirmarReserva = () -> {
            try {
                // A reserva já está CONFIRMADO; a barbearia cancela e o listener cancela a cobrança ainda PENDENTE.
                patchStatus(reserva, admin, corpoDeCancelamento("Barbeiro doente", versaoDa(reserva)))
                        .andExpect(status().isOk());
                esperar(() -> "CANCELADA".equals(statusDaCobrancaDaReserva(reserva)));
            } catch (Exception erro) {
                throw new IllegalStateException(erro);
            }
        };
        try {
            processar.porIdProvedor(idProvedor);
        } finally {
            processar.aposConfirmarReserva = () -> { };
        }

        assertThat(statusDaReserva(reserva)).isEqualTo("CANCELADO");
        assertThat(statusDaCobrancaDaReserva(reserva)).isEqualTo("PAGA_APOS_EXPIRACAO");
        get("/admin/pagamentos", admin).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(1));
    }
}
