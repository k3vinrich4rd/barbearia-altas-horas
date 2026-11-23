package io.github.k3vinrich4rd.altashoras.conta.convite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A19 da Fatia 10B: a validade do convite fica entre 1 hora e 7 dias, ou a aplicação não sobe. */
class ParametrosDoConviteTest {

    @Test
    @DisplayName("A19: a validade padrão é 48 h, 30 min fora do intervalo derruba a subida e 1 h, 30 horas e 7 dias valem")
    void validadeConfigurada() {
        assertThat(new ParametrosDoConvite(Duration.ofHours(48), 5).validade()).isEqualTo(Duration.ofHours(48));
        assertThat(new ParametrosDoConvite(Duration.ofHours(1), 5).validade()).isEqualTo(Duration.ofHours(1));
        assertThat(new ParametrosDoConvite(Duration.ofDays(7), 5).validade()).isEqualTo(Duration.ofDays(7));
        assertThatThrownBy(() -> new ParametrosDoConvite(Duration.ofMinutes(30), 5)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("entre 1 hora e 7 dias");
        assertThatThrownBy(() -> new ParametrosDoConvite(Duration.ofDays(8), 5)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ParametrosDoConvite(Duration.ofHours(48), 0)).isInstanceOf(IllegalStateException.class);
    }
}
