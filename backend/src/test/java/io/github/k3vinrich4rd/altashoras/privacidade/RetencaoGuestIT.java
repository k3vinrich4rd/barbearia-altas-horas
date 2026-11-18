package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.agenda.reserva.TokenGuest;

/** A49 a A54: a retenção do contato do visitante sem conta, 90 dias depois do FIM da reserva (R29, E19, H13). */
class RetencaoGuestIT extends PrivacidadeBase {

    private static final LocalDate DIA_DOS_90_DIAS = LocalDate.of(2026, 9, 2);

    @Autowired
    RetencaoDeDados retencao;

    private UUID servico;
    private UUID barbeiro;

    private UUID visitante(OffsetDateTime inicio, String nome) {
        if (servico == null) {
            servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
            barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        }
        return reserva(barbeiro, servico, inicio, "CONCLUIDO", null, null, nome);
    }

    private Map<String, Object> guest(UUID reservaId) {
        return jdbc.queryForMap("SELECT nome, email, whatsapp, token_hash, anonimizado_em FROM reserva_guest WHERE reserva_id = ?",
                reservaId);
    }

    /** Guarda o hash de um token que o teste conhece, para provar o link do visitante (A52). */
    private String tokenConhecido(UUID reservaId) {
        String token = TokenGuest.novo();
        jdbc.update("UPDATE reserva_guest SET token_hash = ? WHERE reserva_id = ?", TokenGuest.hash(token), reservaId);
        return token;
    }

    private int guestsAnonimizadosPeloJob() {
        return retencao.executarEm(relogio.instant()).get("reserva_guest");
    }

    @Test
    @DisplayName("A49: reserva de visitante com fim em 02/09/2026 09:30 (exatamente 90 dias): o job de 01/12 09:30 anonimiza nome, e-mail, WhatsApp e marca anonimizado_em")
    void noDiaExatoDos90Dias() {
        UUID reserva = visitante(DadosDeTeste.em(DIA_DOS_90_DIAS, 9, 0), "Visitante Fictício"); // fim 09:30

        retencao.executar();

        Map<String, Object> linha = guest(reserva);
        assertThat(linha).containsEntry("nome", "Visitante anonimizado")
                .containsEntry("email", "anonimizado-" + reserva + "@anonimo.invalid")
                .containsEntry("whatsapp", "00000000000");
        assertThat(instante(linha.get("anonimizado_em"))).isEqualTo(relogio.instant());
    }

    @Test
    @DisplayName("A50: reserva de visitante com fim em 02/09/2026 09:31 (um minuto antes dos 90 dias): os dados continuam intactos e anonimizado_em fica nulo")
    void umMinutoAntesDosNoventaDias() {
        UUID reserva = visitante(DadosDeTeste.em(DIA_DOS_90_DIAS, 9, 1), "Visitante Fictício"); // fim 09:31

        retencao.executar();

        Map<String, Object> linha = guest(reserva);
        assertThat(linha).containsEntry("nome", "Visitante Fictício").containsEntry("whatsapp", "11988880002");
        assertThat(linha.get("anonimizado_em")).isNull();
    }

    @Test
    @DisplayName("A51: rodar o job uma segunda vez não afeta nenhuma linha e anonimizado_em continua o do primeiro job")
    void idempotente() {
        UUID reserva = visitante(DadosDeTeste.em(DIA_DOS_90_DIAS, 9, 0), "Visitante Fictício");
        assertThat(guestsAnonimizadosPeloJob()).isEqualTo(1);
        Object primeiro = guest(reserva).get("anonimizado_em");
        relogio.avancar(Duration.ofHours(2));

        assertThat(guestsAnonimizadosPeloJob()).isZero();
        retencao.executar();

        assertThat(guest(reserva).get("anonimizado_em")).isEqualTo(primeiro);
    }

    @Test
    @DisplayName("A52: o link guardado pelo visitante (id da reserva e o token antigo) dá 404 depois do job")
    void linkDeixaDeValer() throws Exception {
        UUID reserva = visitante(DadosDeTeste.em(DIA_DOS_90_DIAS, 9, 0), "Visitante Fictício");
        String token = tokenConhecido(reserva);
        mvc.perform(MockMvcRequestBuilders.get("/agendamentos/guest/" + reserva).header("X-Guest-Token", token))
                .andExpect(status().isOk());

        retencao.executar();

        mvc.perform(MockMvcRequestBuilders.get("/agendamentos/guest/" + reserva).header("X-Guest-Token", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A53: a reserva e a cobrança ficam idênticas depois do job, e na agenda do painel o contato aparece como Visitante anonimizado")
    void reservaECobrancaNaoMudam() throws Exception {
        UUID reserva = visitante(DadosDeTeste.em(DIA_DOS_90_DIAS, 9, 0), "Visitante Fictício");
        cobranca(reserva, "PAGA", DadosDeTeste.em(DIA_DOS_90_DIAS, 8, 0));
        String reservaAntes = jdbc.queryForObject("SELECT row_to_json(r)::text FROM reserva r WHERE id = ?", String.class, reserva);
        String cobrancaAntes = jdbc.queryForObject("SELECT row_to_json(c)::text FROM cobranca c WHERE reserva_id = ?",
                String.class, reserva);

        retencao.executar();

        assertThat(jdbc.queryForObject("SELECT row_to_json(r)::text FROM reserva r WHERE id = ?", String.class, reserva))
                .isEqualTo(reservaAntes);
        assertThat(jdbc.queryForObject("SELECT row_to_json(c)::text FROM cobranca c WHERE reserva_id = ?", String.class, reserva))
                .isEqualTo(cobrancaAntes);
        Conta admin = conta("ADMIN", "BARBEIRO");
        String corpo = get("/admin/agenda?data=2026-09-02", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + reserva + "')].cliente.nome").value("Visitante anonimizado"))
                .andReturn().getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("Visitante Fictício").doesNotContain("11988880002");
        assertThat((List<?>) JsonPath.read(corpo, "$[?(@.id == '" + reserva + "')]")).hasSize(1);
    }

}

/**
 * O prazo é configurável (H13): outra propriedade sobe outro contexto, então fica numa classe própria (no mesmo arquivo,
 * para o conferir-spec achar o critério) e não mexe nos critérios acima.
 */
@TestPropertySource(properties = "app.privacidade.retencao-guest-dias=30")
class RetencaoGuestPrazoConfiguradoIT extends PrivacidadeBase {

    @Autowired
    RetencaoDeDados retencao;

    private UUID visitante(UUID servico, UUID barbeiro, OffsetDateTime inicio, String nome) {
        return reserva(barbeiro, servico, inicio, "CONCLUIDO", null, null, nome);
    }

    private Map<String, Object> guest(UUID reservaId) {
        return jdbc.queryForMap("SELECT nome, anonimizado_em FROM reserva_guest WHERE reserva_id = ?", reservaId);
    }

    @Test
    @DisplayName("A54: com app.privacidade.retencao-guest-dias=30, a reserva com fim há 31 dias é anonimizada e a com fim há 29 dias não")
    void prazoVemDaPropriedade() {
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        OffsetDateTime iniciaHa31Dias = DadosDeTeste.em(LocalDate.of(2026, 10, 31), 9, 0); // fim 09:30: 31 dias antes
        OffsetDateTime iniciaHa29Dias = DadosDeTeste.em(LocalDate.of(2026, 11, 2), 9, 0); // fim 09:30: 29 dias antes
        assertThat(Duration.between(iniciaHa31Dias.plusMinutes(30).toInstant(), relogio.instant())).isEqualTo(Duration.ofDays(31));
        assertThat(Duration.between(iniciaHa29Dias.plusMinutes(30).toInstant(), relogio.instant())).isEqualTo(Duration.ofDays(29));
        UUID velha = visitante(servico, barbeiro, iniciaHa31Dias, "Visitante Antigo");
        UUID recente = visitante(servico, barbeiro, iniciaHa29Dias, "Visitante Recente");

        retencao.executar();

        assertThat(guest(velha)).containsEntry("nome", "Visitante anonimizado");
        assertThat(guest(recente)).containsEntry("nome", "Visitante Recente");
        assertThat(guest(recente).get("anonimizado_em")).isNull();
    }
}
