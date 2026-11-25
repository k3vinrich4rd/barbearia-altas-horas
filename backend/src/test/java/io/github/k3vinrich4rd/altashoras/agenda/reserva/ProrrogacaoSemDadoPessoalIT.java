package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.em;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogar;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.ProrrogacaoDeTeste.prorrogarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.as;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpo;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpoGuest;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.reservar;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.reservarComoVisitante;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/**
 * A20 da Fatia 14A (E11, R30): a trilha da prorrogação é a coluna, o evento do outbox e uma linha de log técnica, e nenhuma
 * das três leva nome, e-mail, WhatsApp, CPF ou token. Captura a saída real do processo.
 */
@ExtendWith(OutputCaptureExtension.class)
class ProrrogacaoSemDadoPessoalIT extends PainelBase {

    @Test
    @DisplayName("A20: o fluxo do A1 deixa PrazoDaReservaProrrogado (reservaId, expiraEmAnterior, expiraEmNovo) e a linha reserva.prorrogada com o id; nenhum nome, e-mail, WhatsApp, CPF ou token no JSON nem no log")
    void trilhaSemDadoPessoal(CapturedOutput saida) throws Exception {
        relogio.definir(em(10, 0, 0));
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        Conta cliente = conta("CLIENTE");
        String doCliente = JsonPath.read(reservar(mvc, cliente.token(), corpo(servico, barbeiro, as(15, 0)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String criada = reservarComoVisitante(mvc, ContaDeTeste.ipNovo(), corpoGuest(servico, barbeiro, as(16, 0)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String doVisitante = JsonPath.read(criada, "$.id");
        String token = JsonPath.read(criada, "$.tokenGuest");

        relogio.definir(em(10, 2, 30));
        prorrogar(mvc, cliente.token(), doCliente).andExpect(status().isCreated());
        prorrogarComoVisitante(mvc, doVisitante, token).andExpect(status().isCreated());

        List<String> eventos = jdbc.queryForList("""
                SELECT serialized_event FROM event_publication WHERE event_type LIKE '%PrazoDaReservaProrrogado'
                ORDER BY publication_date
                """, String.class);
        assertThat(eventos).hasSize(2);
        for (String json : eventos) {
            assertThat(JsonPath.<String>read(json, "$.reservaId")).isIn(doCliente, doVisitante);
            assertThat(JsonPath.<Object>read(json, "$.expiraEmAnterior")).isNotNull();
            assertThat(JsonPath.<Object>read(json, "$.expiraEmNovo")).isNotNull();
            assertThat(JsonPath.<java.util.Map<String, Object>>read(json, "$")).containsOnlyKeys("reservaId",
                    "expiraEmAnterior", "expiraEmNovo");
        }

        assertThat(saida.getAll()).contains("reserva.prorrogada id=" + doCliente)
                .contains("reserva.prorrogada id=" + doVisitante);
        String tudo = String.join("\n", eventos) + "\n" + saida.getAll();
        assertThat(tudo).doesNotContain("Visitante Teste").doesNotContainIgnoringCase("visitante@exemplo.test")
                .doesNotContain("90000-0002").doesNotContain("11900000002").doesNotContain("Equipe Teste")
                .doesNotContain("11900000009").doesNotContain(token).doesNotContain("52998224725")
                .doesNotContain("529.982.247-25").doesNotContain(cliente.token());
    }
}
