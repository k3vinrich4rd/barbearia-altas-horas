package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.TOKEN_DO_WEBHOOK;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.as;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.cobrarPixComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDaReservaGuest;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.corpoDoWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.enviarWebhook;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.esperar;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.idDoProvedor;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.reservarComoVisitante;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaCobranca;
import static io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.statusDaReserva;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste;
import io.github.k3vinrich4rd.altashoras.pagamento.GatewayFalsoDeTeste.GatewayFalso;
import io.github.k3vinrich4rd.altashoras.pagamento.PagamentosDeTeste.ReservaGuest;
import io.github.k3vinrich4rd.altashoras.plataforma.EmissorDeToken;

/**
 * A4 a A6 da Fatia 6D (R7, D121): o prazo de pagamento é de 3 minutos. Os valores vêm da regra, não do palpite: a reserva
 * feita às 08:00:00 expira às 08:03:00; às 08:02:59 ainda ocupa o horário e às 08:03:00 já libera (a disponibilidade
 * olha {@code expira_em > agora}); um pagamento que chega depois dos 3 minutos, com o horário já de outra pessoa, vira
 * {@code PAGA_APOS_EXPIRACAO} e aparece na lista de devoluções.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, GatewayFalsoDeTeste.class})
class PrazoDeTresMinutosIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GatewayFalso gateway;

    @Autowired
    RelogioAjustavel relogio;

    @Autowired
    EmissorDeToken emissor;

    private UUID barbeiro;
    private UUID servico;

    @BeforeEach
    void dados() {
        gateway.reiniciar();
        jdbc.update("DELETE FROM cobranca");
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, 30);
    }

    @AfterEach
    void voltarRelogio() {
        relogio.reiniciar();
    }

    private OffsetDateTime expiraEm(String reservaId) {
        return jdbc.queryForObject("SELECT expira_em FROM reserva WHERE id = ?::uuid", OffsetDateTime.class, reservaId);
    }

    private ResultActions horariosDo(UUID barbeiroId) throws Exception {
        return mvc.perform(get("/disponibilidade").param("servicoId", servico.toString())
                .param("data", "2026-12-01").param("barbeiroId", barbeiroId.toString()));
    }

    private ResultActions reservarGuest(OffsetDateTime inicio) throws Exception {
        return mvc.perform(post("/agendamentos/guest").contentType(MediaType.APPLICATION_JSON)
                .content(corpoDaReservaGuest(servico, barbeiro, inicio))
                .with(ContaDeTeste.ip(ContaDeTeste.ipNovo())));
    }

    @Test
    @DisplayName("A4: a reserva do visitante feita às 08:00:00 nasce com expira_em às 08:03:00 (agora + 3 min) e expiraEm na resposta com o mesmo instante")
    void expiraEmTresMinutos() throws Exception {
        String corpo = reservarGuest(as(10, 0)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiraEm").value("2026-11-17T08:03:00-03:00"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(corpo, "$.id");
        assertThat(expiraEm(id).toInstant()).isEqualTo(RelogioFixoDeTeste.AGORA.plus(Duration.ofMinutes(3)));
        assertThat(jdbc.queryForObject("SELECT expiracao_pagamento_min FROM configuracao_agenda", Integer.class))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("A5: às 08:02:59 o horário reservado ainda está ocupado (some da disponibilidade e outra reserva dá 409); às 08:03:00 ele volta e a nova reserva dá 201")
    void ocupaAteOsTresMinutos() throws Exception {
        reservarGuest(as(10, 0)).andExpect(status().isCreated());

        relogio.avancar(Duration.ofMinutes(2).plusSeconds(59));
        horariosDo(barbeiro).andExpect(status().isOk())
                .andExpect(jsonPath("$.barbeiros[0].horarios[?(@ == '2026-12-01T10:00:00-03:00')]").isEmpty());
        reservarGuest(as(10, 0)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORARIO_INDISPONIVEL"));

        relogio.avancar(Duration.ofSeconds(1));
        horariosDo(barbeiro).andExpect(status().isOk())
                .andExpect(jsonPath("$.barbeiros[0].horarios[?(@ == '2026-12-01T10:00:00-03:00')]").isNotEmpty());
        reservarGuest(as(10, 0)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A6: o Pix aprovado 4 min depois (prazo de 3), com o horário já de outro cliente, deixa a reserva EXPIRADO, a cobrança PAGA_APOS_EXPIRACAO e a pendência de devolução aparece no painel do admin")
    void pagamentoDepoisDosTresMinutosVaiParaDevolucao() throws Exception {
        ReservaGuest primeira = reservarComoVisitante(mvc, barbeiro, servico, as(10, 30));
        cobrarPixComoVisitante(mvc, jdbc, primeira);

        relogio.avancar(Duration.ofMinutes(4));
        ReservaGuest segunda = reservarComoVisitante(mvc, barbeiro, servico, as(10, 30));
        assertThat(statusDaReserva(jdbc, segunda.id())).isEqualTo("AGUARDANDO_PAGAMENTO");
        esperar(() -> "CANCELADA".equals(statusDaCobranca(jdbc, primeira.id())));

        gateway.marcarPago(idDoProvedor(jdbc, primeira.id()));
        enviarWebhook(mvc, TOKEN_DO_WEBHOOK,
                corpoDoWebhook("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idDoProvedor(jdbc, primeira.id())))
                .andExpect(status().isOk());

        assertThat(statusDaReserva(jdbc, primeira.id())).isEqualTo("EXPIRADO");
        assertThat(statusDaCobranca(jdbc, primeira.id())).isEqualTo("PAGA_APOS_EXPIRACAO");
        String cobranca = jdbc.queryForObject("SELECT id::text FROM cobranca WHERE reserva_id = ?::uuid", String.class,
                primeira.id());
        mvc.perform(get("/admin/pagamentos").param("status", "PAGA_APOS_EXPIRACAO")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].id").value(hasItem(cobranca)));
    }

    private String tokenDoAdmin() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id, nome, email, whatsapp, deve_trocar_senha) VALUES (?, 'Admin Teste', ?, '11900000009', false)",
                id, "admin." + id + "@exemplo.test");
        jdbc.update("INSERT INTO usuario_papel (usuario_id, papel) VALUES (?, 'ADMIN')", id);
        return emissor.emitir(id, List.of("ADMIN"), 0, false).valor();
    }
}
