package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** A44 a A46 da Fatia 13: o aviso só vai ao barbeiro da reserva, e cada conta só vê e mexe no que é dela. */
class IsolamentoDeNotificacoesIT extends NotificacaoBase {

    private static final String ENDPOINT_A = "https://fcm.googleapis.com/fcm/send/aparelho-de-a";
    private static final String ENDPOINT_B = "https://fcm.googleapis.com/fcm/send/aparelho-de-b";
    private static final String ENDPOINT_EDUARDO = "https://fcm.googleapis.com/fcm/send/aparelho-do-eduardo";

    private void confirmar(UUID reserva) throws Exception {
        reservas.confirmarPagamento(reserva);
        esperarOutboxVazio();
    }

    @Test
    @DisplayName("A44: barbeiros A e B com um aparelho cada; a reserva de B (cliente com cadastro e outra de visitante) é confirmada: só o aparelho de B recebe, e A tem zero envios e zero avisos")
    void soOBarbeiroDaReservaRecebe() throws Exception {
        Barbeiro a = barbeiroComConta("BARBEIRO");
        Barbeiro b = barbeiroComConta("BARBEIRO");
        inscrever(a.conta().id(), ENDPOINT_A);
        inscrever(b.conta().id(), ENDPOINT_B);
        UUID servico = servicoDeTeste("Corte Clássico");
        UUID cliente = cliente("Maria Souza Teste", "11988880001");
        UUID doCliente = comAceite(reserva(b.id(), servico, as(10, 0), "AGUARDANDO_PAGAMENTO",
                relogio.instant().plusSeconds(1200), cliente, null));
        UUID doVisitante = reservaAguardando(b.id(), servico, as(11, 0));

        confirmar(doCliente);
        confirmar(doVisitante);

        assertThat(falsoWebPush.envios()).hasSize(2).allSatisfy(e -> {
            assertThat(e.destino().usuarioId()).isEqualTo(b.conta().id());
            assertThat(e.destino().aparelho().endpoint()).isEqualTo(ENDPOINT_B);
        });
        assertThat(avisosDa(b.conta().id())).isEqualTo(2);
        assertThat(avisosDa(a.conta().id())).isZero();
    }

    @Test
    @DisplayName("A45: o Eduardo (ADMIN) também tem aparelho; a reserva do convidado B é confirmada: só B recebe, o Eduardo não; e no sentido contrário só o Eduardo recebe")
    void donoNaoRecebeOsDoConvidado() throws Exception {
        Barbeiro eduardo = barbeiroComConta("ADMIN", "BARBEIRO");
        Barbeiro b = barbeiroComConta("BARBEIRO");
        inscrever(eduardo.conta().id(), ENDPOINT_EDUARDO);
        inscrever(b.conta().id(), ENDPOINT_B);
        UUID servico = servicoDeTeste("Corte Clássico");

        confirmar(reservaAguardando(b.id(), servico, as(10, 0)));
        assertThat(falsoWebPush.envios()).hasSize(1);
        assertThat(falsoWebPush.envios().getFirst().destino().usuarioId()).isEqualTo(b.conta().id());
        assertThat(avisosDa(eduardo.conta().id())).isZero();

        confirmar(reservaAguardando(eduardo.id(), servico, as(11, 0)));
        assertThat(falsoWebPush.envios()).hasSize(2);
        assertThat(falsoWebPush.envios().get(1).destino().usuarioId()).isEqualTo(eduardo.conta().id());
        assertThat(avisosDa(b.conta().id())).isEqualTo(1);
    }

    @Test
    @DisplayName("A46: A lista, marca como lidas e tenta remover o aparelho de B (e passa usuarioId e barbeiroId de B): vê só os seus, os de B continuam não lidos, a inscrição de B continua lá e os parâmetros são ignorados sem sinal")
    void contaNaoMexeNoDeOutra() throws Exception {
        Barbeiro a = barbeiroComConta("BARBEIRO");
        Barbeiro b = barbeiroComConta("BARBEIRO");
        inscrever(b.conta().id(), ENDPOINT_B);
        UUID servico = servicoDeTeste("Corte Clássico");
        confirmar(reservaAguardando(a.id(), servico, as(10, 0)));
        confirmar(reservaAguardando(b.id(), servico, as(11, 0)));
        confirmar(reservaAguardando(b.id(), servico, as(12, 0)));

        get("/admin/notificacoes?usuarioId=" + b.conta().id() + "&barbeiroId=" + b.id(), a.conta()).andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas").value(1)).andExpect(jsonPath("$.itens.length()").value(1));
        post("/admin/notificacoes/marcar-lidas?usuarioId=" + b.conta().id(), a.conta()).andExpect(status().isNoContent());
        mvc.perform(MockMvcRequestBuilders.delete("/admin/notificacoes/inscricao")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + a.conta().token()).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"endpoint\": \"" + ENDPOINT_B + "\" }")).andExpect(status().isNoContent());

        get("/admin/notificacoes", a.conta()).andExpect(jsonPath("$.naoLidas").value(0));
        get("/admin/notificacoes", b.conta()).andExpect(jsonPath("$.naoLidas").value(2)).andExpect(jsonPath("$.itens.length()").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inscricao_push WHERE endpoint = ?", Long.class, ENDPOINT_B)).isEqualTo(1);
    }
}
