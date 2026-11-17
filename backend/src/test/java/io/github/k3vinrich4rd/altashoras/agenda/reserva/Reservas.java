package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/** Atalhos dos testes de reserva. "Agora" é terça 17/11/2026 08h00; a terça 01/12/2026 é o dia de teste. */
final class Reservas {

    static final LocalDate TERCA = LocalDate.of(2026, 12, 1);

    private Reservas() {
    }

    static OffsetDateTime as(int hora, int minuto) {
        return DadosDeTeste.em(TERCA, hora, minuto);
    }

    static String corpo(UUID servico, UUID barbeiro, OffsetDateTime inicio) {
        return """
                { "servicoId": "%s", "barbeiroId": "%s", "inicio": "%s" }
                """.formatted(servico, barbeiro, inicio);
    }

    static String corpoGuest(UUID servico, UUID barbeiro, OffsetDateTime inicio) {
        return """
                { "servicoId": "%s", "barbeiroId": "%s", "inicio": "%s",
                  "nome": "Visitante Teste", "email": "Visitante@Exemplo.test", "whatsapp": "(11) 90000-0002" }
                """.formatted(servico, barbeiro, inicio);
    }

    static ResultActions reservar(MockMvc mvc, String acesso, String corpo) throws Exception {
        return mvc.perform(post("/agendamentos").header(HttpHeaders.AUTHORIZATION, "Bearer " + acesso)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    static ResultActions reservarComoVisitante(MockMvc mvc, String ip, String corpo) throws Exception {
        return mvc.perform(post("/agendamentos/guest").contentType(MediaType.APPLICATION_JSON).content(corpo)
                .with(ContaDeTeste.ip(ip)));
    }

    /** Conta nova e logada; devolve o access token. */
    static String clienteLogado(MockMvc mvc) throws Exception {
        ContaDeTeste.Conta conta = ContaDeTeste.cadastrar(mvc);
        return ContaDeTeste.entrar(mvc, conta.email(), conta.senha()).acesso();
    }
}
