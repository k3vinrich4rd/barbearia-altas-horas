package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.CPF;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.as;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.chaveNova;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDaCobranca;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.versaoVigente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste.GatewayFalso;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;

/** O gateway fora do ar na criação (E14, E27): a cobrança fica gravada e a repetição não cria outra no gateway. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, GatewayFalsoDeTeste.class})
class CobrancaGatewayForaIT {

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

    private Map<String, Object> cobrancaDa(String reservaId) {
        return jdbc.queryForMap("SELECT status, id_cobranca_provedor, id FROM cobranca WHERE reserva_id = ?::uuid", reservaId);
    }

    @Test
    @DisplayName("A30: gateway com timeout → 503 GATEWAY_INDISPONIVEL, cobrança PENDENTE sem id do provedor; repetir procura pelo externalReference antes de criar outra")
    void gatewayForaDoAr() throws Exception {
        // Caso 1: o gateway chegou a criar a cobrança, só a resposta se perdeu. A repetição a encontra e NÃO cria outra.
        ReservaGuest perdida = reservarComoVisitante(mvc, barbeiro, servico, as(10, 0));
        String chave = chaveNova();
        String corpo = corpoDaCobranca(perdida.id(), "PIX", CPF, versao);
        gateway.ficarIndisponivel(true);

        cobrarComoVisitante(mvc, perdida, chave, corpo)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GATEWAY_INDISPONIVEL"));
        Map<String, Object> pendente = cobrancaDa(perdida.id());
        assertThat(pendente.get("status")).isEqualTo("PENDENTE");
        assertThat(pendente.get("id_cobranca_provedor")).isNull();
        assertThat(gateway.chamadasDeCriar.get()).isEqualTo(1);

        gateway.voltar();
        relogio.avancar(Duration.ofSeconds(16)); // passou o tempo máximo da criação: pode retomar
        cobrarComoVisitante(mvc, perdida, chave, corpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.pix.copiaECola").exists());
        assertThat(gateway.chamadasDeBuscar.get()).isEqualTo(1);
        assertThat(gateway.chamadasDeCriar.get()).as("achou pelo externalReference: nenhuma cobrança nova").isEqualTo(1);
        Map<String, Object> completa = cobrancaDa(perdida.id());
        assertThat(completa.get("id_cobranca_provedor")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cobranca WHERE reserva_id = ?::uuid", Long.class, perdida.id()))
                .isEqualTo(1L);

        // Caso 2: o gateway nem chegou a criar. A repetição procura, não acha e só então cria.
        ReservaGuest nunca = reservarComoVisitante(mvc, barbeiro, servico, as(10, 30));
        String outraChave = chaveNova();
        String outroCorpo = corpoDaCobranca(nunca.id(), "PIX", CPF, versao);
        gateway.ficarIndisponivel(false);
        cobrarComoVisitante(mvc, nunca, outraChave, outroCorpo).andExpect(status().isServiceUnavailable());
        assertThat(cobrancaDa(nunca.id()).get("id_cobranca_provedor")).isNull();

        gateway.voltar();
        relogio.avancar(Duration.ofSeconds(16));
        int buscasAntes = gateway.chamadasDeBuscar.get();
        int criacoesAntes = gateway.chamadasDeCriar.get();
        cobrarComoVisitante(mvc, nunca, outraChave, outroCorpo).andExpect(status().isCreated());
        assertThat(gateway.chamadasDeBuscar.get()).isEqualTo(buscasAntes + 1);
        assertThat(gateway.chamadasDeCriar.get()).isEqualTo(criacoesAntes + 1);
        assertThat(cobrancaDa(nunca.id()).get("id_cobranca_provedor")).isNotNull();
    }

    @Test
    @DisplayName("A30: cobrança sem id do provedor e recente (outra requisição pode estar criando) → 409 COBRANCA_EM_ANDAMENTO sem chamar o gateway; 16 s depois, retoma")
    void naoRetomaCobrancaRecente() throws Exception {
        ReservaGuest reserva = reservarComoVisitante(mvc, barbeiro, servico, as(11, 0));
        String chave = chaveNova();
        String corpo = corpoDaCobranca(reserva.id(), "PIX", CPF, versao);
        gateway.ficarIndisponivel(false);
        cobrarComoVisitante(mvc, reserva, chave, corpo).andExpect(status().isServiceUnavailable());
        gateway.voltar();
        int criacoes = gateway.chamadasDeCriar.get();

        cobrarComoVisitante(mvc, reserva, chave, corpo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COBRANCA_EM_ANDAMENTO"));
        assertThat(gateway.chamadasDeCriar.get()).isEqualTo(criacoes);
        assertThat(gateway.chamadasDeBuscar.get()).isZero();

        relogio.avancar(Duration.ofSeconds(16));
        cobrarComoVisitante(mvc, reserva, chave, corpo).andExpect(status().isCreated());
        assertThat(gateway.chamadasDeBuscar.get()).isEqualTo(1);
        assertThat(cobrancaDa(reserva.id()).get("id_cobranca_provedor")).isNotNull();
    }
}
