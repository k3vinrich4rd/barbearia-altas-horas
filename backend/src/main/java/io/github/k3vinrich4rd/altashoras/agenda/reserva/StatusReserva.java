package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.util.EnumSet;
import java.util.Set;

/**
 * Máquina de estados da reserva (docs/dominio/modelo-de-dominio.md, seção 2), sem framework (E5, ADR 0011).
 * O cliente não cancela nem remarca (R18): quem pode pedir cada transição é regra do caso de uso, não daqui.
 */
public enum StatusReserva {

    AGUARDANDO_PAGAMENTO,
    CONFIRMADO,
    CONCLUIDO,
    NAO_COMPARECEU,
    CANCELADO,
    EXPIRADO;

    public boolean podeIrPara(StatusReserva destino) {
        return destinos().contains(destino);
    }

    private Set<StatusReserva> destinos() {
        return switch (this) {
            case AGUARDANDO_PAGAMENTO -> EnumSet.of(CONFIRMADO, EXPIRADO, CANCELADO);
            // Pagamento tardio com o horário ainda livre (R14); se estiver ocupado, a constraint recusa.
            case EXPIRADO -> EnumSet.of(CONFIRMADO);
            case CONFIRMADO -> EnumSet.of(CANCELADO, CONCLUIDO, NAO_COMPARECEU);
            case CONCLUIDO, NAO_COMPARECEU, CANCELADO -> EnumSet.noneOf(StatusReserva.class);
        };
    }
}
