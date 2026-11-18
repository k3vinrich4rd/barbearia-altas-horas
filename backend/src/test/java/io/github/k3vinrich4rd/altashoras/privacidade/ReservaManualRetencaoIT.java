package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/**
 * A25 e A26 da Fatia 6D: o nome e a observação da reserva manual somem na mesma retenção do visitante, 90 dias depois do
 * FIM da reserva (D126). O relógio do teste está em 01/12/2026 09:30; 90 dias antes é 02/09/2026 09:30.
 */
class ReservaManualRetencaoIT extends PrivacidadeBase {

    private static final LocalDate DIA_DOS_90_DIAS = LocalDate.of(2026, 9, 2);

    @Autowired
    RetencaoDeDados retencao;

    private UUID servico;
    private UUID barbeiro;

    private UUID manual(OffsetDateTime inicio, String nome, String observacao) {
        if (servico == null) {
            servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
            barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO reserva (id, barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos,
                                     nome_cliente, observacao)
                VALUES (?, ?, ?, 'MANUAL', ?, ?, ?, 'CONCLUIDO', 4500, ?, ?)
                """, id, barbeiro, servico, inicio, inicio.plusMinutes(30), inicio.plusMinutes(30), nome, observacao);
        return id;
    }

    private Map<String, Object> linha(UUID id) {
        return jdbc.queryForMap("SELECT nome_cliente, observacao, status, preco_centavos, version, atualizado_em FROM reserva WHERE id = ?", id);
    }

    @Test
    @DisplayName("A25: reserva manual com fim há exatamente 90 dias tem nome e observação apagados pelo job; a reserva, o status e o preço continuam, e rodar de novo não muda nada")
    void anonimizaNoDiaExato() {
        UUID reserva = manual(DadosDeTeste.em(DIA_DOS_90_DIAS, 9, 0), "Fulano de Tal", "veio pelo Insta");
        Map<String, Object> antes = linha(reserva);

        assertThat(retencao.executarEm(relogio.instant()).get("reserva_manual")).isEqualTo(1);

        Map<String, Object> depois = linha(reserva);
        assertThat(depois.get("nome_cliente")).isNull();
        assertThat(depois.get("observacao")).isNull();
        assertThat(depois).containsEntry("status", "CONCLUIDO").containsEntry("preco_centavos", 4500L)
                .containsEntry("version", antes.get("version")).containsEntry("atualizado_em", antes.get("atualizado_em"));

        relogio.avancar(Duration.ofHours(2));
        assertThat(retencao.executarEm(relogio.instant()).get("reserva_manual")).isZero();
    }

    @Test
    @DisplayName("A26: reserva manual com fim um minuto antes dos 90 dias mantém nome e observação, e a reserva do visitante da mesma idade continua sendo tratada pela retenção própria dele")
    void umMinutoAntes() {
        UUID reserva = manual(DadosDeTeste.em(DIA_DOS_90_DIAS, 9, 1), "Fulano de Tal", "veio pelo Insta");

        retencao.executarEm(relogio.instant());

        assertThat(linha(reserva)).containsEntry("nome_cliente", "Fulano de Tal").containsEntry("observacao", "veio pelo Insta");
        assertThat(retencao.executarEm(relogio.instant()).get("reserva_guest")).isNotNull();
    }
}
