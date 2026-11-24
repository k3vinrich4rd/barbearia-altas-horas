package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.CPF;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.chaveNova;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarComoCliente;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarPixComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDaCobranca;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.clienteLogado;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoCliente;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.versaoVigente;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import io.github.k3vinrich4rd.altashoras.agenda.Reservas;
import io.github.k3vinrich4rd.altashoras.agenda.ResultadoConfirmacao;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;

/**
 * A1 a A5 da Fatia 13: o evento {@code ReservaConfirmada} sai do outbox UMA vez, só quando o pagamento muda a reserva para
 * CONFIRMADO, e nunca leva dado pessoal. Usa a reserva e a cobrança de verdade (a API do cliente e a do visitante) e a
 * confirmação do modo simulado, que passa pelo mesmo caminho do webhook.
 */
class ReservaConfirmadaEventoIT extends NotificacaoBase {

    private String confirmarComoVisitante(ReservaGuest reserva, String cobrancaId) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/pagamentos/simulacao/" + cobrancaId + "/confirmar").header("X-Guest-Token", reserva.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("A1: cliente com cadastro paga: CONFIRMADO e uma publicação de ReservaConfirmada no outbox, sem nome, WhatsApp, e-mail nem CPF no JSON")
    void clienteComCadastroPaga() throws Exception {
        relogio.reiniciar();
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        UUID servico = servicoDeTeste("Corte Degradê");
        String acesso = clienteLogado(mvc);
        String reserva = reservarComoCliente(mvc, acesso, barbeiro.id(), servico, as(10, 0));
        String cobranca = cobrarComoCliente(mvc, acesso, chaveNova(), corpoDaCobranca(reserva, "PIX", CPF, versaoVigente(jdbc)))
                .andReturn().getResponse().getContentAsString();
        String cobrancaId = JsonPath.read(cobranca, "$.id");

        mvc.perform(MockMvcRequestBuilders.post("/pagamentos/simulacao/" + cobrancaId + "/confirmar").header(HttpHeaders.AUTHORIZATION, "Bearer " + acesso))
                .andExpect(status().isOk());

        assertThat(statusDaReserva(UUID.fromString(reserva))).isEqualTo("CONFIRMADO");
        assertThat(publicacoesDeReservaConfirmada()).isEqualTo(1);
        String json = jdbc.queryForObject("SELECT serialized_event FROM event_publication WHERE event_type LIKE '%ReservaConfirmada'",
                String.class);
        assertThat(json).contains(reserva).contains(barbeiro.id().toString())
                .doesNotContain("Cliente Teste").doesNotContain("90000").doesNotContain("@exemplo.test")
                .doesNotContain("\"nome").doesNotContain("whatsapp").doesNotContain("cpf");
    }

    @Test
    @DisplayName("A2: visitante (sem cadastro) paga: o mesmo evento, uma vez")
    void visitantePaga() throws Exception {
        relogio.reiniciar();
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro.id(), servicoDeTeste("Corte Clássico"), as(10, 0));
        String cobranca = cobrarPixComoVisitante(mvc, jdbc, reserva);

        confirmarComoVisitante(reserva, JsonPath.read(cobranca, "$.id"));

        assertThat(statusDaReserva(UUID.fromString(reserva.id()))).isEqualTo("CONFIRMADO");
        assertThat(publicacoesDeReservaConfirmada()).isEqualTo(1);
        String json = jdbc.queryForObject("SELECT serialized_event FROM event_publication WHERE event_type LIKE '%ReservaConfirmada'",
                String.class);
        assertThat(json).contains(reserva.id()).doesNotContain("Visitante").doesNotContain("90000");
    }

    @Test
    @DisplayName("A3: reserva já CONFIRMADO, o webhook chega de novo e a leitura do agendamento concilia: nenhum evento novo, um no total")
    void chamadaRepetidaNaoPublica() throws Exception {
        relogio.reiniciar();
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro.id(), servicoDeTeste("Corte Clássico"), as(10, 0));
        String cobrancaId = JsonPath.read(cobrarPixComoVisitante(mvc, jdbc, reserva), "$.id");
        confirmarComoVisitante(reserva, cobrancaId);

        confirmarComoVisitante(reserva, cobrancaId); // o "webhook" de novo
        mvc.perform(MockMvcRequestBuilders.get("/agendamentos/guest/" + reserva.id()).header("X-Guest-Token", reserva.token()))
                .andExpect(status().isOk()); // a leitura concilia (E25)
        assertThat(reservas.confirmarPagamento(UUID.fromString(reserva.id()))).isEqualTo(ResultadoConfirmacao.CONFIRMADA);

        assertThat(publicacoesDeReservaConfirmada()).isEqualTo(1);
    }

    @Test
    @DisplayName("A4: reserva EXPIRADO paga depois, com o horário livre: vira CONFIRMADO e publica um evento")
    void expiradaPagaComHorarioLivre() {
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        UUID reserva = comAceite(reserva(barbeiro.id(), servicoDeTeste("Corte Clássico"), as(10, 0), "EXPIRADO", null, null, "Visitante Dois"));

        ResultadoConfirmacao resultado = reservas.confirmarPagamento(reserva);

        assertThat(resultado).isEqualTo(ResultadoConfirmacao.CONFIRMADA);
        assertThat(statusDaReserva(reserva)).isEqualTo("CONFIRMADO");
        assertThat(publicacoesDeReservaConfirmada()).isEqualTo(1);
    }

    @Test
    @DisplayName("A5: reserva EXPIRADO paga depois com o horário já de outra pessoa: continua EXPIRADO, nenhum evento, nenhum aviso")
    void expiradaPagaComHorarioOcupado() throws Exception {
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        UUID servico = servicoDeTeste("Corte Clássico");
        UUID expirada = comAceite(reserva(barbeiro.id(), servico, as(10, 0), "EXPIRADO", null, null, "Visitante Tres"));
        reserva(barbeiro.id(), servico, as(10, 0), "CONFIRMADO", null, null, "Visitante Quatro");

        ResultadoConfirmacao resultado = reservas.confirmarPagamento(expirada);

        assertThat(resultado).isEqualTo(ResultadoConfirmacao.HORARIO_OCUPADO);
        assertThat(statusDaReserva(expirada)).isEqualTo("EXPIRADO");
        assertThat(publicacoesDeReservaConfirmada()).isZero();
        Thread.sleep(300);
        assertThat(avisosDa(barbeiro.conta().id())).isZero();
        assertThat(falsoWebPush.envios()).isEmpty();
    }
}
