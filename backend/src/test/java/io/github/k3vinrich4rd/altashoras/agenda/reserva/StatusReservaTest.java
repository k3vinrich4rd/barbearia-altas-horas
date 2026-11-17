package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/** A máquina de estados em unidade, sem Spring nem banco: as 36 combinações de origem e destino. */
class StatusReservaTest {

    /** A tabela de transições do modelo de domínio (seção 2), escrita de novo aqui de propósito. */
    private static final Map<StatusReserva, Set<StatusReserva>> PERMITIDAS = Map.of(
            StatusReserva.AGUARDANDO_PAGAMENTO,
            EnumSet.of(StatusReserva.CONFIRMADO, StatusReserva.EXPIRADO, StatusReserva.CANCELADO),
            StatusReserva.EXPIRADO, EnumSet.of(StatusReserva.CONFIRMADO),
            StatusReserva.CONFIRMADO,
            EnumSet.of(StatusReserva.CANCELADO, StatusReserva.CONCLUIDO, StatusReserva.NAO_COMPARECEU),
            StatusReserva.CONCLUIDO, EnumSet.noneOf(StatusReserva.class),
            StatusReserva.NAO_COMPARECEU, EnumSet.noneOf(StatusReserva.class),
            StatusReserva.CANCELADO, EnumSet.noneOf(StatusReserva.class));

    @Test
    @DisplayName("A10: só as transições do modelo passam; as outras 28 são recusadas")
    void transicoes() {
        int permitidas = 0;
        for (StatusReserva de : StatusReserva.values()) {
            for (StatusReserva para : StatusReserva.values()) {
                boolean esperado = PERMITIDAS.get(de).contains(para);
                assertThat(de.podeIrPara(para)).as("%s -> %s", de, para).isEqualTo(esperado);
                permitidas += esperado ? 1 : 0;
            }
        }
        assertThat(permitidas).isEqualTo(7);
    }

    @Test
    @DisplayName("A10: a entidade recusa transição inválida com 422 TRANSICAO_INVALIDA e não muda o status")
    void entidadeRecusa() {
        Instant agora = Instant.parse("2026-11-17T11:00:00Z");
        Horario horario = new Horario(UUID.randomUUID(), UUID.randomUUID(), agora.plusSeconds(7200),
                agora.plusSeconds(9000), agora.plusSeconds(9000));
        Reserva reserva = Reserva.nova(horario, UUID.randomUUID(), 5500, agora.plusSeconds(900), agora);

        reserva.mudarPara(StatusReserva.EXPIRADO, agora);
        assertThatThrownBy(() -> reserva.mudarPara(StatusReserva.CANCELADO, agora))
                .isInstanceOf(ExcecaoDeNegocio.class)
                .extracting(erro -> ((ExcecaoDeNegocio) erro).codigo())
                .isEqualTo(CodigoErro.TRANSICAO_INVALIDA);
        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.EXPIRADO);

        reserva.mudarPara(StatusReserva.CONFIRMADO, agora);
        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.CONFIRMADO);
    }
}
