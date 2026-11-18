package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/** A44 a A46: o que acontece com as reservas na exclusão: futuras não pagas cancelam, futura paga bloqueia, passadas ficam (E17, D-1). */
class ExclusaoReservasIT extends PrivacidadeBase {

    private static final LocalDate QUARTA = LocalDate.of(2026, 12, 2);

    private List<String> horarios(UUID servico, UUID barbeiro) throws Exception {
        String corpo = mvc.perform(MockMvcRequestBuilders.get("/disponibilidade").param("servicoId", servico.toString())
                        .param("data", QUARTA.toString()).param("barbeiroId", barbeiro.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.barbeiros[0].horarios");
    }

    @Test
    @DisplayName("A44: reserva futura AGUARDANDO_PAGAMENTO e cobrança PENDENTE: 204, a reserva vira CANCELADO com o motivo da exclusão e o próprio usuário como autor, a cobrança vira CANCELADA e o horário de 02/12 10:00 volta")
    void futuraNaoPagaCancela() throws Exception {
        Cliente cliente = novoCliente(false);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID reserva = reserva(barbeiro, servico, DadosDeTeste.em(QUARTA, 10, 0), "AGUARDANDO_PAGAMENTO",
                relogio.instant().plus(Duration.ofMinutes(10)), cliente.id(), null);
        cobranca(reserva, "PENDENTE", DadosDeTeste.em(LocalDate.of(2026, 12, 1), 9, 25));
        assertThat(horarios(servico, barbeiro)).noneMatch(h -> h.contains("T10:00:00"));

        delete("/me", cliente.acesso()).andExpect(status().isNoContent());

        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT status, motivo_cancelamento, cancelado_por, cancelado_em FROM reserva WHERE id = ?", reserva);
        assertThat(linha).containsEntry("status", "CANCELADO")
                .containsEntry("motivo_cancelamento", "Conta excluída pelo titular").containsEntry("cancelado_por", cliente.id());
        assertThat(instante(linha.get("cancelado_em"))).isEqualTo(relogio.instant());
        // A cobrança é tratada pelo evento ReservaCancelada, depois do commit (o mesmo caminho do cancelamento do painel).
        esperar(() -> "CANCELADA".equals(statusDaCobrancaDaReserva(reserva)));
        assertThat(horarios(servico, barbeiro)).anyMatch(h -> h.contains("2026-12-02T10:00:00"));
    }

    @Test
    @DisplayName("A45: com uma reserva futura CONFIRMADO (cobrança PAGA) e outra AGUARDANDO_PAGAMENTO: 409 EXCLUSAO_BLOQUEADA_POR_RESERVA e nada muda (conta, reservas, cobrança, solicitacao_titular)")
    void futuraPagaBloqueia() throws Exception {
        Cliente cliente = novoCliente(true);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID paga = reserva(barbeiro, servico, DadosDeTeste.em(QUARTA, 10, 0), "CONFIRMADO", null, cliente.id(), null);
        cobranca(paga, "PAGA", DadosDeTeste.em(LocalDate.of(2026, 12, 1), 9, 0));
        UUID pendente = reserva(barbeiro, servico, DadosDeTeste.em(QUARTA, 11, 0), "AGUARDANDO_PAGAMENTO",
                relogio.instant().plus(Duration.ofMinutes(10)), cliente.id(), null);
        long consentimentosAntes = linhasDeConsentimento(cliente.id());
        long refreshAntes = contar("SELECT count(*) FROM refresh_token WHERE usuario_id = ?", cliente.id());

        delete("/me", cliente.acesso()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXCLUSAO_BLOQUEADA_POR_RESERVA"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        assertThat(jdbc.queryForMap("SELECT nome, email, ativo, anonimizado_em FROM usuario WHERE id = ?", cliente.id()))
                .containsEntry("nome", "Cliente Teste").containsEntry("email", cliente.email())
                .containsEntry("ativo", true).containsEntry("anonimizado_em", null);
        assertThat(statusDaReserva(paga)).isEqualTo("CONFIRMADO");
        assertThat(statusDaReserva(pendente)).isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(statusDaCobrancaDaReserva(paga)).isEqualTo("PAGA");
        assertThat(contar("SELECT count(*) FROM solicitacao_titular WHERE usuario_id = ?", cliente.id())).isZero();
        assertThat(linhasDeConsentimento(cliente.id())).isEqualTo(consentimentosAntes);
        assertThat(contar("SELECT count(*) FROM refresh_token WHERE usuario_id = ?", cliente.id())).isEqualTo(refreshAntes);
        get("/me", cliente.acesso()).andExpect(status().isOk()); // a sessão continua valendo
    }

    @Test
    @DisplayName("A46: reservas passadas (CONCLUIDO e NAO_COMPARECEU) continuam com o mesmo status e preço, e na agenda do painel o cliente aparece como Conta excluída sem WhatsApp")
    void passadasFicam() throws Exception {
        Cliente cliente = novoCliente(false);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID concluida = reserva(barbeiro, servico, DadosDeTeste.em(LocalDate.of(2026, 11, 30), 10, 0), "CONCLUIDO", null,
                cliente.id(), null);
        UUID faltou = reserva(barbeiro, servico, DadosDeTeste.em(LocalDate.of(2026, 11, 29), 10, 0), "NAO_COMPARECEU", null,
                cliente.id(), null);

        delete("/me", cliente.acesso()).andExpect(status().isNoContent());

        assertThat(statusDaReserva(concluida)).isEqualTo("CONCLUIDO");
        assertThat(statusDaReserva(faltou)).isEqualTo("NAO_COMPARECEU");
        assertThat(jdbc.queryForList("SELECT preco_centavos FROM reserva WHERE cliente_id = ?", Long.class, cliente.id()))
                .containsExactly(4500L, 4500L);
        Conta admin = conta("ADMIN", "BARBEIRO");
        String corpo = get("/admin/agenda?data=2026-11-30", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + concluida + "')].cliente.nome").value("Conta excluída"))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> clientes = JsonPath.read(corpo, "$[?(@.id == '" + concluida + "')].cliente");
        assertThat(clientes).hasSize(1);
        assertThat(clientes.getFirst().get("whatsapp")).isNull();
        assertThat(corpo).doesNotContain("Cliente Teste").doesNotContain("11900000001");
    }
}
