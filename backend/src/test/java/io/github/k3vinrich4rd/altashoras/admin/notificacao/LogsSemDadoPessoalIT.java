package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.ChavesDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.ResultadoDoEnvio;

/** A49 da Fatia 13 (R30): inscrever, avisar, falhar, tentar de novo e testar não deixam endpoint, chave, nome, WhatsApp nem e-mail em log. */
class LogsSemDadoPessoalIT extends NotificacaoBase {

    @Test
    @DisplayName("A49: nenhuma linha de log, em todo o fluxo, contém endpoint, p256dh, auth, chave VAPID, nome, WhatsApp ou e-mail")
    void nadaPessoalNoLog() throws Exception {
        Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level antes = raiz.getLevel();
        ListAppender<ILoggingEvent> captura = new ListAppender<>();
        captura.start();
        raiz.setLevel(Level.DEBUG);
        raiz.addAppender(captura);
        try {
            Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
            ChavesDeTeste.Aparelho aparelho = ChavesDeTeste.aparelho();
            String endpoint = "https://fcm.googleapis.com/fcm/send/ENDPOINT-SECRETO-DO-APARELHO";
            mvc.perform(MockMvcRequestBuilders.post("/admin/notificacoes/inscricao")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + barbeiro.conta().token()).contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"endpoint\": \"" + endpoint + "\", \"chaves\": { \"p256dh\": \"" + aparelho.p256dh()
                            + "\", \"auth\": \"" + aparelho.auth() + "\" } }")).andExpect(status().isNoContent());
            UUID cliente = cliente("Maria Souza Teste", "11988880001");
            UUID reserva = comAceite(reserva(barbeiro.id(), servicoDeTeste("Corte Clássico"), as(10, 0), "AGUARDANDO_PAGAMENTO",
                    relogio.instant().plusSeconds(1200), cliente, null));
            falsoWebPush.responder(d -> ResultadoDoEnvio.TENTAR_DEPOIS);
            reservas.confirmarPagamento(reserva);
            esperarEntregaFalhar();
            falsoWebPush.responder(d -> ResultadoDoEnvio.DESTINO_INVALIDO);
            post("/admin/notificacoes/teste", barbeiro.conta()).andExpect(status().isNoContent());
            // endpoint recusado também não aparece
            mvc.perform(MockMvcRequestBuilders.post("/admin/notificacoes/inscricao")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + barbeiro.conta().token()).contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"endpoint\": \"https://host-nao-aceito.exemplo.test/NAO-LOGAR\", \"chaves\": { \"p256dh\": \"x\", \"auth\": \"y\" } }"))
                    .andExpect(status().isBadRequest());

            List<String> linhas = captura.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(linhas).isNotEmpty();
            String tudo = String.join("\n", linhas);
            assertThat(tudo).doesNotContain("ENDPOINT-SECRETO").doesNotContain("NAO-LOGAR").doesNotContain(aparelho.p256dh())
                    .doesNotContain(aparelho.auth()).doesNotContain("Maria Souza").doesNotContain("11988880001")
                    .doesNotContain("@exemplo.test").doesNotContain("fcm.googleapis.com/fcm/send");
        } finally {
            raiz.detachAppender(captura);
            raiz.setLevel(antes);
        }
    }
}
