package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.NotificacaoBase;

/**
 * A6 e A7 da Fatia 13: sem pagamento confirmado não há aviso. Reserva que expirou, reserva cancelada pelo painel e reserva
 * manual (R33, já nasce CONFIRMADO pelo próprio barbeiro) nunca publicam {@code ReservaConfirmada} nem gravam aviso. Fica no
 * pacote da reserva porque o job de expiração é interno da agenda.
 */
class SemAvisoSemPagamentoIT extends NotificacaoBase {

    @Autowired
    ExpiracaoJob job;

    @Test
    @DisplayName("A6: reserva que expirou sem pagar e outra cancelada pelo painel: nenhum ReservaConfirmada, nenhum aviso")
    void expirouOuCancelou() throws Exception {
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        Conta admin = conta("ADMIN", "BARBEIRO");
        UUID servico = servicoDeTeste("Corte Clássico");
        UUID vaiExpirar = reservaAguardando(barbeiro.id(), servico, as(10, 0));
        UUID vaiCancelar = reserva(barbeiro.id(), servico, as(11, 0), "CONFIRMADO", null, null, "Visitante Dois");

        relogio.avancar(Duration.ofMinutes(30));
        job.executar();
        esperarOutboxVazio();
        patchStatus(vaiCancelar, comTokenNovo(admin, "ADMIN", "BARBEIRO"), corpoDeCancelamento("Falta de energia", versaoDa(vaiCancelar))).andExpect(status().isOk());
        esperarOutboxVazio();

        assertThat(statusDaReserva(vaiExpirar)).isEqualTo("EXPIRADO");
        assertThat(statusDaReserva(vaiCancelar)).isEqualTo("CANCELADO");
        assertThat(publicacoesDeReservaConfirmada()).isZero();
        assertThat(avisosDa(barbeiro.conta().id())).isZero();
        assertThat(falsoWebPush.envios()).isEmpty();
    }

    @Test
    @DisplayName("A7: reserva manual (POST /admin/agendamentos) nasce CONFIRMADO e não publica o evento nem gera aviso")
    void reservaManualNaoAvisa() throws Exception {
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        jdbc.update("UPDATE barbeiro SET whatsapp = '11988887777' WHERE id = ?", barbeiro.id());
        UUID servico = servicoDeTeste("Corte Clássico");
        String corpo = "{ \"servicoId\": \"" + servico + "\", \"barbeiroId\": \"" + barbeiro.id() + "\", \"inicio\": \""
                + as(10, 0) + "\", \"nomeCliente\": \"Cliente da Porta\" }";

        mvc.perform(MockMvcRequestBuilders.post("/admin/agendamentos").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + barbeiro.conta().token()).content(corpo))
                .andExpect(status().isCreated());
        esperarOutboxVazio();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE status = 'CONFIRMADO' AND barbeiro_id = ?",
                Long.class, barbeiro.id())).isEqualTo(1);
        assertThat(publicacoesDeReservaConfirmada()).isZero();
        assertThat(avisosDa(barbeiro.conta().id())).isZero();
        assertThat(falsoWebPush.envios()).isEmpty();
    }
}
