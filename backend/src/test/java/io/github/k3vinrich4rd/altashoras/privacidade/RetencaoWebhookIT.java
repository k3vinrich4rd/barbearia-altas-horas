package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A56: só os eventos de webhook JÁ PROCESSADOS e com mais de 1 ano saem; os que aguardam ficam (E21, ADR 0009). */
class RetencaoWebhookIT extends PrivacidadeBase {

    @Autowired
    RetencaoDeDados retencao;

    private UUID evento(Duration idade, boolean processado) {
        OffsetDateTime recebido = OffsetDateTime.ofInstant(relogio.instant().minus(idade), ZoneOffset.UTC);
        return jdbc.queryForObject("""
                INSERT INTO evento_webhook (provedor, id_evento_provedor, tipo, recebido_em, processado_em, resultado)
                VALUES ('SIMULADO', ?, 'PAYMENT_RECEIVED', ?, ?, ?) RETURNING id
                """, UUID.class, "evt_" + UUID.randomUUID(), recebido, processado ? recebido.plusMinutes(1) : null,
                processado ? "PROCESSADO" : null);
    }

    private boolean existe(UUID id) {
        return contar("SELECT count(*) FROM evento_webhook WHERE id = ?", id) == 1;
    }

    @Test
    @DisplayName("A56: evento processado há 366 dias sai; o processado há 10 dias e o que nunca foi processado (há 400 dias) ficam")
    void soOProcessadoAntigoSai() {
        UUID antigoProcessado = evento(Duration.ofDays(366), true);
        UUID recenteProcessado = evento(Duration.ofDays(10), true);
        UUID antigoPendente = evento(Duration.ofDays(400), false);

        retencao.executar();

        assertThat(existe(antigoProcessado)).isFalse();
        assertThat(existe(recenteProcessado)).isTrue();
        assertThat(existe(antigoPendente)).isTrue();
    }
}
