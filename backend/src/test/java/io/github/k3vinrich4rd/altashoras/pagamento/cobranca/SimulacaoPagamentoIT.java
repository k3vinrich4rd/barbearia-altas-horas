package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.CPF;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.as;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.chaveNova;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.clienteLogado;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarComoCliente;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDaCobranca;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoCliente;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaCobranca;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaReserva;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.versaoVigente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/**
 * O modo simulado do beta (E28, D59), SEM gateway falso e sem nenhuma conta externa: reservar, cobrar e confirmar como
 * dono passa pelo mesmo caminho do webhook. O modo asaas (rota inexistente) está em {@link SimulacaoDesligadaIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class SimulacaoPagamentoIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("A45: modo simulado → reservar, cobrar e confirmar como dono deixa a reserva CONFIRMADO pelo mesmo fluxo do webhook; outro usuário → 404")
    void confirmacaoSimulada() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        String dono = clienteLogado(mvc);
        String outro = clienteLogado(mvc);
        String reservaId = reservarComoCliente(mvc, dono, barbeiro, servico, as(10, 0));

        String corpo = cobrarComoCliente(mvc, dono, chaveNova(), corpoDaCobranca(reservaId, "PIX", CPF, versaoVigente(jdbc)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pix.copiaECola").value(startsWith("SIMULADO-")))
                .andExpect(jsonPath("$.pix.qrCodeBase64").value((Object) null))
                .andReturn().getResponse().getContentAsString();
        String cobrancaId = JsonPath.read(corpo, "$.id");
        assertThat(jdbc.queryForObject("SELECT provedor FROM cobranca WHERE id = ?::uuid", String.class, cobrancaId))
                .isEqualTo("SIMULADO");
        String idNoGateway = jdbc.queryForObject("SELECT id_cobranca_provedor FROM cobranca WHERE id = ?::uuid",
                String.class, cobrancaId);
        assertThat(idNoGateway).startsWith("sim_");

        // Outro usuário e quem não está logado: 404 e 401 antes de pagar, nada muda.
        mvc.perform(post("/pagamentos/simulacao/" + cobrancaId + "/confirmar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outro))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        mvc.perform(post("/pagamentos/simulacao/" + cobrancaId + "/confirmar"))
                .andExpect(status().isUnauthorized());
        assertThat(statusDaReserva(jdbc, reservaId)).isEqualTo("AGUARDANDO_PAGAMENTO");

        // O dono confirma: o mesmo caminho do webhook (evento gravado, gateway reconsultado, reserva confirmada).
        mvc.perform(post("/pagamentos/simulacao/" + cobrancaId + "/confirmar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cobrancaId))
                .andExpect(jsonPath("$.status").value("PAGA"));

        assertThat(statusDaReserva(jdbc, reservaId)).isEqualTo("CONFIRMADO");
        assertThat(statusDaCobranca(jdbc, reservaId)).isEqualTo("PAGA");
        Map<String, Object> evento = jdbc.queryForMap(
                "SELECT provedor, tipo, processado_em, resultado FROM evento_webhook WHERE id_evento_provedor = ?",
                "sim_" + cobrancaId);
        assertThat(evento.get("provedor")).isEqualTo("SIMULADO");
        assertThat(evento.get("tipo")).isEqualTo("PAYMENT_RECEIVED");
        assertThat(evento.get("processado_em")).isNotNull();
        assertThat(evento.get("resultado")).isEqualTo("PROCESSADO");

        // Repetir é inofensivo: o evento já processado não faz nada.
        mvc.perform(post("/pagamentos/simulacao/" + cobrancaId + "/confirmar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGA"));
    }

    @Test
    @DisplayName("A45: anônimo recebe a mesma resposta (401) para cobrança inexistente e existente, sem oráculo de existência")
    void anonimoSemOraculo() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        String dono = clienteLogado(mvc);
        String reservaId = reservarComoCliente(mvc, dono, barbeiro, servico, as(11, 0));
        String existente = JsonPath.read(cobrarComoCliente(mvc, dono, chaveNova(),
                corpoDaCobranca(reservaId, "PIX", CPF, versaoVigente(jdbc))).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/pagamentos/simulacao/" + UUID.randomUUID() + "/confirmar")).andExpect(status().isUnauthorized());
        mvc.perform(post("/pagamentos/simulacao/" + existente + "/confirmar")).andExpect(status().isUnauthorized());
        assertThat(statusDaReserva(jdbc, reservaId)).isEqualTo("AGUARDANDO_PAGAMENTO");
    }
}
