package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.Canal;

/** A18 e A19 da Fatia 13: a configuração errada derruba a partida com mensagem clara e sem imprimir chave. */
class DespachanteDeCanaisTest {

    private static ParametrosDeNotificacao com(String canais, String publica, String privada, boolean exigir) {
        return new ParametrosDeNotificacao(canais, publica, privada, "https://site.exemplo.test", "fcm.googleapis.com", exigir, 30,
                90, 5, Duration.ofHours(1));
    }

    @Test
    @DisplayName("A18: canais=sms (nome desconhecido): a aplicação falha na partida com mensagem que cita a propriedade e a lista dos nomes válidos")
    void nomeDesconhecido() {
        assertThatThrownBy(() -> new Despachante(List.of(), com("webpush,sms", "", "", false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("app.notificacao.canais")
                .hasMessageContaining("APP_NOTIFICACAO_CANAIS").hasMessageContaining("'sms'").hasMessageContaining("webpush")
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("A19: canais=webpush com exigir-chaves e sem vapid-privada: falha na partida com mensagem clara que não imprime nenhum valor de chave")
    void semChavePrivada() {
        String publica = "BPUBLICA-FALSA-QUE-NAO-PODE-APARECER";
        assertThatThrownBy(() -> new Despachante(List.of(), com("webpush", publica, "", true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_NOTIFICACAO_VAPID_PRIVADA")
                .hasMessageNotContaining(publica);
        // Sem exigir (o padrão enquanto o Kevin não cadastra o par), sobe: o canal só devolve NAO_CONFIGURADO.
        assertThat(new Despachante(List.of(), com("webpush", "", "", false)).canaisLigados()).isEmpty();
        // Webpush desligado: a falta de chave não importa.
        assertThat(new Despachante(List.of(), com("", "", "", true)).canaisLigados()).isEmpty();
        assertThat(com("webpush", publica, "SEGREDO-FALSO", true).toString()).doesNotContain(publica).doesNotContain("SEGREDO-FALSO");
        assertThat(Canal.values()).hasSize(2);
    }
}
