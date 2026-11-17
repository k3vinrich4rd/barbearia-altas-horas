package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.CPF;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.as;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.chaveNova;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDaCobranca;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.versaoVigente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste.GatewayFalso;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;

/** Idempotência da criação (R10, E17) e uma cobrança viva por reserva (E21). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, GatewayFalsoDeTeste.class})
class CobrancaIdempotenciaIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GatewayFalso gateway;

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

    @Test
    @DisplayName("A26: mesma chave e mesmo corpo → 200 com a mesma cobrança, sem nova chamada ao gateway e sem cpfMascarado")
    void repeticaoDevolveAMesma() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(10, 0));
        String chave = chaveNova();
        String corpo = corpoDaCobranca(reserva.id(), "PIX", CPF, versao);

        String primeira = cobrarComoVisitante(mvc, reserva, chave, corpo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(gateway.chamadasDeCriar.get()).isEqualTo(1);

        cobrarComoVisitante(mvc, reserva, chave, corpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((String) JsonPath.read(primeira, "$.id")))
                .andExpect(jsonPath("$.pix.copiaECola").value((String) JsonPath.read(primeira, "$.pix.copiaECola")))
                .andExpect(jsonPath("$.cpfMascarado").doesNotExist());

        assertThat(gateway.chamadasDeCriar.get()).isEqualTo(1);
        assertThat(gateway.chamadasDeBuscar.get()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cobranca WHERE reserva_id = ?::uuid", Long.class, reserva.id()))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("A27: mesma chave e corpo diferente (outro método ou outro CPF) → 422 IDEMPOTENCIA_CONFLITO")
    void mesmaChaveOutroCorpo() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(10, 30));
        String chave = chaveNova();
        cobrarComoVisitante(mvc, reserva, chave, corpoDaCobranca(reserva.id(), "PIX", CPF, versao))
                .andExpect(status().isCreated());

        cobrarComoVisitante(mvc, reserva, chave, corpoDaCobranca(reserva.id(), "CARTAO_CREDITO", CPF, versao))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCIA_CONFLITO"));
        cobrarComoVisitante(mvc, reserva, chave, corpoDaCobranca(reserva.id(), "PIX", "111.444.777-35", versao))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCIA_CONFLITO"));
        assertThat(gateway.chamadasDeCriar.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("A28: cobrança pendente e outra chave → 409 COBRANCA_EM_ANDAMENTO com o id da existente")
    void umaCobrancaViva() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(11, 0));
        String primeira = cobrarComoVisitante(mvc, reserva, chaveNova(), corpoDaCobranca(reserva.id(), "PIX", CPF, versao))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String idExistente = JsonPath.read(primeira, "$.id");

        cobrarComoVisitante(mvc, reserva, chaveNova(), corpoDaCobranca(reserva.id(), "PIX", CPF, versao))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COBRANCA_EM_ANDAMENTO"))
                .andExpect(jsonPath("$.detail").value(containsString(idExistente)));

        assertThat(gateway.chamadasDeCriar.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cobranca WHERE reserva_id = ?::uuid", Long.class, reserva.id()))
                .isEqualTo(1L);
    }
}
