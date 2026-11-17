package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.CPF;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.as;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.chaveNova;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.clienteLogado;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarComoCliente;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDaCobranca;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoCliente;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.versaoVigente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste.GatewayFalso;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;

/** Criar a cobrança: Pix, cartão, aceite da política, CPF, dono da reserva (E14 a E18). Gateway falso, sem rede. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, GatewayFalsoDeTeste.class})
class CobrancaIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GatewayFalso gateway;

    @Autowired
    RelogioAjustavel relogio;

    private UUID barbeiro;
    private UUID servico;
    private int versao;

    @BeforeEach
    void dados() {
        gateway.reiniciar();
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, 30);
        versao = versaoVigente(jdbc);
    }

    @AfterEach
    void voltarRelogio() {
        relogio.reiniciar();
    }

    private long cobrancasDa(String reservaId) {
        return jdbc.queryForObject("SELECT count(*) FROM cobranca WHERE reserva_id = ?::uuid", Long.class, reservaId);
    }

    @Test
    @DisplayName("A22: reserva pendente, aceite, Pix e CPF válido → 201 com valor, copia e cola, QR e cpfMascarado; nenhum CPF no banco")
    void cobrancaPix() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(10, 0));

        cobrarComoVisitante(mvc, reserva, chaveNova(), corpoDaCobranca(reserva.id(), "PIX", CPF, versao))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservaId").value(reserva.id()))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.metodo").value("PIX"))
                .andExpect(jsonPath("$.valorCentavos").value(5500))
                .andExpect(jsonPath("$.pix.copiaECola").value(startsWith("00020126FAKE")))
                .andExpect(jsonPath("$.pix.qrCodeBase64").value("UVJDT0RFLUZBTFNP"))
                .andExpect(jsonPath("$.pix.expiraEm").value("2026-11-17T08:15:00-03:00"))
                .andExpect(jsonPath("$.cartao").doesNotExist())
                .andExpect(jsonPath("$.cpfMascarado").value("***.***.***-25"));

        assertThat(gateway.chamadasDeCriar.get()).isEqualTo(1);
        // O CPF só passa pela API: nenhuma coluna da cobrança (nem do resto do banco que a toca) o guarda.
        String linha = jdbc.queryForObject("SELECT row_to_json(c)::text FROM cobranca c WHERE reserva_id = ?::uuid",
                String.class, reserva.id());
        assertThat(linha).doesNotContain("52998224725").doesNotContain("529.982").contains("PENDENTE");
        String reservaNoBanco = jdbc.queryForObject(
                "SELECT row_to_json(r)::text || coalesce((SELECT row_to_json(g)::text FROM reserva_guest g WHERE g.reserva_id = r.id), '') "
                        + "FROM reserva r WHERE r.id = ?::uuid", String.class, reserva.id());
        assertThat(reservaNoBanco).doesNotContain("52998224725").doesNotContain("529.982");
    }

    @Test
    @DisplayName("A23: cartão em outra reserva → 201 com cartao.urlCheckout e pix nulo; nenhum dado de cartão no corpo")
    void cobrancaCartao() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(10, 30));

        String corpo = cobrarComoVisitante(mvc, reserva, chaveNova(), corpoDaCobranca(reserva.id(), "CARTAO_CREDITO", CPF, versao))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metodo").value("CARTAO_CREDITO"))
                .andExpect(jsonPath("$.cartao.urlCheckout").value(startsWith("https://checkout.exemplo.test/")))
                .andExpect(jsonPath("$.cartao.expiraEm").value("2026-11-17T08:15:00-03:00"))
                .andExpect(jsonPath("$.pix").value((Object) null))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> resposta = JsonPath.read(corpo, "$");
        assertThat(resposta.keySet()).containsExactlyInAnyOrder("id", "reservaId", "status", "metodo", "valorCentavos",
                "pix", "cartao", "cpfMascarado");
    }

    @Test
    @DisplayName("A24: sem aceitePolitica, aceite false ou versaoPolitica diferente da vigente → 422 ACEITE_POLITICA_OBRIGATORIO e nenhuma cobrança")
    void aceiteObrigatorio() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(11, 0));
        String semAceite = """
                { "reservaId": "%s", "metodo": "PIX", "cpf": "%s", "versaoPolitica": %d }
                """.formatted(reserva.id(), CPF, versao);
        String aceiteFalso = """
                { "reservaId": "%s", "metodo": "PIX", "cpf": "%s", "aceitePolitica": false, "versaoPolitica": %d }
                """.formatted(reserva.id(), CPF, versao);
        String versaoAntiga = """
                { "reservaId": "%s", "metodo": "PIX", "cpf": "%s", "aceitePolitica": true, "versaoPolitica": %d }
                """.formatted(reserva.id(), CPF, versao - 1);

        for (String corpo : new String[] {semAceite, aceiteFalso, versaoAntiga}) {
            cobrarComoVisitante(mvc, reserva, chaveNova(), corpo)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("ACEITE_POLITICA_OBRIGATORIO"));
        }
        assertThat(cobrancasDa(reserva.id())).isZero();
        assertThat(gateway.chamadasDeCriar.get()).isZero();
        assertThat(jdbc.queryForObject("SELECT aceite_politica_em FROM reserva WHERE id = ?::uuid", Object.class,
                reserva.id())).isNull();
    }

    @Test
    @DisplayName("A25: CPF com dígito errado → 400 CPF_INVALIDO; sem X-Idempotency-Key → 400 IDEMPOTENCY_KEY_AUSENTE")
    void cpfEChave() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(11, 30));

        cobrarComoVisitante(mvc, reserva, chaveNova(), corpoDaCobranca(reserva.id(), "PIX", "529.982.247-24", versao))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CPF_INVALIDO"));
        mvc.perform(post("/pagamentos/cobrancas").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Guest-Token", reserva.token())
                        .content(corpoDaCobranca(reserva.id(), "PIX", CPF, versao)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_AUSENTE"));
        assertThat(cobrancasDa(reserva.id())).isZero();
    }

    @Test
    @DisplayName("A29: reserva expirada → 422 RESERVA_EXPIRADA; reserva de outro dono → 404; token de visitante errado → 404")
    void reservaExpiradaEDono() throws Exception {
        // Expirada: o prazo de 15 min passa (o job ainda nem rodou: a regra olha o expira_em).
        ReservaGuest expirada = reservarComoVisitante(mvc, barbeiro, servico, as(13, 0));
        relogio.avancar(Duration.ofMinutes(16));
        cobrarComoVisitante(mvc, expirada, chaveNova(), corpoDaCobranca(expirada.id(), "PIX", CPF, versao))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RESERVA_EXPIRADA"));
        assertThat(cobrancasDa(expirada.id())).isZero();
        relogio.reiniciar();

        // De outro dono: o cliente B tenta cobrar a reserva do cliente A.
        String acessoA = clienteLogado(mvc);
        String acessoB = clienteLogado(mvc);
        String reservaDeA = reservarComoCliente(mvc, acessoA, barbeiro, servico, as(14, 0));
        cobrarComoCliente(mvc, acessoB, chaveNova(), corpoDaCobranca(reservaDeA, "PIX", CPF, versao))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));

        // Token de visitante errado, e sem nenhuma credencial (401).
        ReservaGuest guest = reservarComoVisitante(mvc, barbeiro, servico, as(14, 30));
        cobrarComoVisitante(mvc, new ReservaGuest(guest.id(), "g1_errado"), chaveNova(),
                corpoDaCobranca(guest.id(), "PIX", CPF, versao))
                .andExpect(status().isNotFound());
        mvc.perform(post("/pagamentos/cobrancas").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", chaveNova()).content(corpoDaCobranca(guest.id(), "PIX", CPF, versao)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));
        assertThat(cobrancasDa(reservaDeA) + cobrancasDa(guest.id())).isZero();
        assertThat(gateway.chamadasDeCriar.get()).isZero();
    }

    @Test
    @DisplayName("A31: depois da cobrança, a reserva guarda a versão da política aceita e a data do aceite")
    void aceiteNaReserva() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(15, 0));
        assertThat(jdbc.queryForObject("SELECT aceite_politica_versao FROM reserva WHERE id = ?::uuid", Integer.class,
                reserva.id())).isNull();

        cobrarComoVisitante(mvc, reserva, chaveNova(), corpoDaCobranca(reserva.id(), "PIX", CPF, versao))
                .andExpect(status().isCreated());

        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT aceite_politica_versao, aceite_politica_em FROM reserva WHERE id = ?::uuid", reserva.id());
        assertThat(linha.get("aceite_politica_versao")).isEqualTo(versao);
        assertThat(((java.sql.Timestamp) linha.get("aceite_politica_em")).toInstant())
                .isEqualTo(RelogioFixoDeTeste.AGORA);
    }
}
