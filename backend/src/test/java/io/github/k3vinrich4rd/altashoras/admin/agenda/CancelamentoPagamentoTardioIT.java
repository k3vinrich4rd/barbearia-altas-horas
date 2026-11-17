package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarPixComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.IncompleteEventPublications;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;

/**
 * A24: o cliente paga um Pix de uma reserva que a barbearia já cancelou. Usa a reserva e a cobrança de verdade (pela API
 * do visitante) e a confirmação do modo simulado, que passa pelo mesmo caminho do webhook.
 */
class CancelamentoPagamentoTardioIT extends PainelBase {

    @Autowired
    IncompleteEventPublications incompletas;

    private record Pedido(Conta admin, ReservaGuest reserva, String cobrancaId) {
    }

    /** Reserva do visitante com a cobrança Pix PENDENTE, no relógio de 17/11 (a antecedência mínima deixa reservar). */
    private Pedido pedidoDoVisitante() throws Exception {
        relogio.reiniciar();
        Conta admin = conta("ADMIN", "BARBEIRO");
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(10, 0));
        String cobranca = cobrarPixComoVisitante(mvc, jdbc, reserva);
        return new Pedido(admin, reserva, JsonPath.read(cobranca, "$.id"));
    }

    private void cancelar(Pedido p) throws Exception {
        UUID id = UUID.fromString(p.reserva().id());
        patchStatus(id, p.admin(), corpoDeCancelamento("Falta de energia", versaoDa(id))).andExpect(status().isOk());
    }

    private void clienteConfirmaOPix(Pedido p) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/pagamentos/simulacao/" + p.cobrancaId() + "/confirmar").header("X-Guest-Token", p.reserva().token()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A24: pagamento confirmado depois do cancelamento: a reserva continua CANCELADO e a cobrança vira PAGA_APOS_EXPIRACAO, que aparece como pendência")
    void pagouDepoisDoCancelamento() throws Exception {
        Pedido p = pedidoDoVisitante();
        cancelar(p);
        esperar(() -> "CANCELADA".equals(statusDaCobrancaDaReserva(UUID.fromString(p.reserva().id()))));

        clienteConfirmaOPix(p);

        UUID reserva = UUID.fromString(p.reserva().id());
        assertThat(statusDaReserva(reserva)).isEqualTo("CANCELADO");
        assertThat(statusDaCobrancaDaReserva(reserva)).isEqualTo("PAGA_APOS_EXPIRACAO");
        get("/admin/pagamentos", p.admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].status").value("PAGA_APOS_EXPIRACAO"))
                .andExpect(jsonPath("$.itens[0].reservaId").value(p.reserva().id()));
    }

    @Test
    @DisplayName("A24: na ordem inversa (pagou antes de o evento ser tratado), o evento tratado depois não desfaz nada: a cobrança termina PAGA_APOS_EXPIRACAO")
    void pagouAntesDoEventoSerTratado() throws Exception {
        Pedido p = pedidoDoVisitante();
        UUID reserva = UUID.fromString(p.reserva().id());
        falharNasCobrancas();
        try {
            cancelar(p);
            esperarEntregaFalhar();
            assertThat(statusDaCobrancaDaReserva(reserva)).isEqualTo("PENDENTE");

            clienteConfirmaOPix(p);
            assertThat(statusDaCobrancaDaReserva(reserva)).isEqualTo("PAGA_APOS_EXPIRACAO");
        } finally {
            consertarAsCobrancas();
        }

        incompletas.resubmitIncompletePublications(publicacao -> true);
        esperarOutboxVazio();

        assertThat(statusDaReserva(reserva)).isEqualTo("CANCELADO");
        assertThat(statusDaCobrancaDaReserva(reserva)).isEqualTo("PAGA_APOS_EXPIRACAO");
    }
}
