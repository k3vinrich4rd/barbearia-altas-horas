package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.AvisoParaEnvio;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.Canal;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.DestinoDoAviso.Aparelho;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.EmailCanal;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.ResultadoDoEnvio;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.WebPushCanal;

/**
 * A15 a A17 da Fatia 13: qual canal o Despachante chama. A15 sobe a aplicação inteira com a configuração padrão
 * ({@code canais=webpush}); A16 e A17 montam o Despachante com as mesmas implementações falsas e cada configuração (a lista de
 * canais é só texto, então não precisa de uma aplicação nova por combinação).
 */
class DespachanteDeCanaisIT extends NotificacaoBase {

    @Autowired
    Despachante despachante;

    @Autowired
    List<io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.CanalDeNotificacao> implementacoes;

    private static ParametrosDeNotificacao com(String canais) {
        return new ParametrosDeNotificacao(canais, "", "", "https://site.exemplo.test", "fcm.googleapis.com", false, 30, 90, 5,
                Duration.ofHours(1));
    }

    private static final AvisoParaEnvio AVISO = new AvisoParaEnvio("Altas Horas", "Novo horário: Corte, ter 01/12 às 10:00", "/painel", "t");
    private static final List<Aparelho> UM_APARELHO = List.of(new Aparelho("https://fcm.googleapis.com/fcm/send/a", "p", "a"));

    @Test
    @DisplayName("A15: canais=webpush (padrão): só o canal de Web Push é chamado; o EmailCanal existe como bean e não é chamado")
    void soOWebPushPorPadrao() {
        assertThat(despachante.canaisLigados()).containsExactly(Canal.WEBPUSH);
        assertThat(implementacoes).anyMatch(c -> c instanceof WebPushCanal).anyMatch(c -> c instanceof EmailCanal);

        despachante.entregar(AVISO, UUID.randomUUID(), UM_APARELHO, () -> "barbeiro@exemplo.test");

        assertThat(falsoWebPush.envios()).hasSize(1);
        assertThat(falsoEmail.envios()).isEmpty();
    }

    @Test
    @DisplayName("A16: canais=webpush,email com os dois falsos: os dois recebem o mesmo AvisoParaEnvio (sem dado de cliente) e o resultado de um não impede o outro")
    void osDoisCanais() {
        falsoWebPush.responder(d -> ResultadoDoEnvio.TENTAR_DEPOIS);
        Despachante ambos = new Despachante(List.of(falsoWebPush, falsoEmail), com("webpush,email"));

        Despachante.Relatorio relatorio = ambos.entregar(AVISO, UUID.randomUUID(), UM_APARELHO, () -> "barbeiro@exemplo.test");

        assertThat(falsoWebPush.envios()).hasSize(1);
        assertThat(falsoEmail.envios()).hasSize(1);
        assertThat(falsoWebPush.envios().getFirst().aviso()).isSameAs(AVISO).isEqualTo(falsoEmail.envios().getFirst().aviso());
        assertThat(falsoEmail.envios().getFirst().destino().email()).isEqualTo("barbeiro@exemplo.test");
        assertThat(relatorio.pendente()).as("o Web Push falhou, mas o e-mail entregou: o resultado de um não trava o outro").isTrue();
    }

    @Test
    @DisplayName("A17: canais vazio: só o painel; nenhum canal é chamado e o Despachante sobe sem erro")
    void semCanais() {
        Despachante nenhum = new Despachante(List.of(falsoWebPush, falsoEmail), com(""));

        Despachante.Relatorio relatorio = nenhum.entregar(AVISO, UUID.randomUUID(), UM_APARELHO, () -> "barbeiro@exemplo.test");

        assertThat(nenhum.canaisLigados()).isEmpty();
        assertThat(falsoWebPush.envios()).isEmpty();
        assertThat(falsoEmail.envios()).isEmpty();
        assertThat(relatorio.pendente()).isFalse();
        assertThat(relatorio.enviados()).isEmpty();
    }
}
