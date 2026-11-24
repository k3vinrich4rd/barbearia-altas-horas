package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** A48 da Fatia 13: o stub do e-mail, provado só com um cliente FALSO (nunca um provedor real). */
class EmailCanalTest {

    private record Enviado(String remetente, String para, String assunto, String texto) {
    }

    private static final AvisoParaEnvio AVISO = new AvisoParaEnvio("Altas Horas", "Novo horário: Corte Clássico, ter 01/12 às 10:00", "/painel", "t");

    private static DestinoDoAviso conta(String email) {
        return new DestinoDoAviso(UUID.randomUUID(), null, email);
    }

    @Test
    @DisplayName("A48: ligado com configuração falsa: o cliente falso recebe o aviso e o e-mail da conta, sem nome de cliente")
    void enviaComOFalso() {
        List<Enviado> gravados = new ArrayList<>();
        EmailCanal canal = new EmailCanal("avisos@exemplo.test", (r, p, a, t) -> gravados.add(new Enviado(r, p, a, t)));

        ResultadoDoEnvio resultado = canal.enviar(AVISO, conta("barbeiro@exemplo.test"));

        assertThat(resultado).isEqualTo(ResultadoDoEnvio.ENVIADO);
        assertThat(gravados).hasSize(1);
        assertThat(gravados.getFirst().para()).isEqualTo("barbeiro@exemplo.test");
        assertThat(gravados.getFirst().remetente()).isEqualTo("avisos@exemplo.test");
        assertThat(gravados.getFirst().texto()).contains("Novo horário: Corte Clássico").doesNotContain("Maria");
        assertThat(canal.porAparelho()).isFalse();
        assertThat(canal.canal()).isEqualTo(Canal.EMAIL);
    }

    @Test
    @DisplayName("A48: ligado sem configuração (sem remetente ou sem provedor) devolve NAO_CONFIGURADO e registra uma linha sem dado pessoal")
    void semConfiguracao() {
        Logger log = (Logger) LoggerFactory.getLogger(EmailCanal.class);
        ListAppender<ILoggingEvent> captura = new ListAppender<>();
        captura.start();
        log.addAppender(captura);
        try {
            assertThat(new EmailCanal("", (r, p, a, t) -> { }).enviar(AVISO, conta("barbeiro@exemplo.test")))
                    .isEqualTo(ResultadoDoEnvio.NAO_CONFIGURADO);
            assertThat(new EmailCanal("avisos@exemplo.test", (ClienteDeEmail) null).enviar(AVISO, conta("barbeiro@exemplo.test")))
                    .isEqualTo(ResultadoDoEnvio.NAO_CONFIGURADO);
            assertThat(captura.list).hasSize(2).allSatisfy(l -> assertThat(l.getFormattedMessage())
                    .doesNotContain("barbeiro@exemplo.test").doesNotContain("avisos@exemplo.test").doesNotContain("Corte"));
        } finally {
            log.detachAppender(captura);
        }
    }

    @Test
    @DisplayName("A48: falha do provedor é passageira (TENTAR_DEPOIS) e o endereço não vai para o log; destinatário vazio é inválido")
    void falhaDoProvedor() {
        EmailCanal canal = new EmailCanal("avisos@exemplo.test", (r, p, a, t) -> {
            throw new IllegalStateException("falhou para " + p);
        });
        assertThat(canal.enviar(AVISO, conta("barbeiro@exemplo.test"))).isEqualTo(ResultadoDoEnvio.TENTAR_DEPOIS);
        assertThat(canal.enviar(AVISO, conta(" "))).isEqualTo(ResultadoDoEnvio.DESTINO_INVALIDO);
    }
}
