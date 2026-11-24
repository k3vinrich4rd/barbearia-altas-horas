package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.ParametrosDeNotificacao;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.DestinoDoAviso.Aparelho;

/**
 * O canal de Web Push (E8, D172): criptografa o corpo (RFC 8291, {@code aes128gcm}) e assina com VAPID (RFC 8292), tudo com o
 * JDK. Um ÚNICO {@link HttpClient}, sem pool novo, com timeout de 5 s para conectar e 5 s para a resposta (o banco tem só 3
 * conexões: o envio roda fora de transação e nunca demora mais que isso). Sem redirecionamento: o serviço de push não
 * redireciona e seguir {@code Location} abriria a porta do SSRF. {@code 404} e {@code 410} (e os 4xx de inscrição
 * recusada) viram DESTINO_INVALIDO; {@code 429}, {@code 5xx} e timeout, TENTAR_DEPOIS; {@code 201}, ENVIADO.
 * Nada de endpoint, chave, texto do aviso nem corpo de resposta vai para log (R30).
 */
@Component
public class WebPushCanal implements CanalDeNotificacao {

    private static final Logger LOG = LoggerFactory.getLogger(WebPushCanal.class);
    static final Duration TEMPO_LIMITE = Duration.ofSeconds(5);
    /** O limite do protocolo é 4 KB; a spec fixa 3 KB (E10). */
    static final int LIMITE_DO_CORPO = 3 * 1024;
    private static final int TTL_EM_SEGUNDOS = 3600;

    private final @Nullable Vapid vapid;
    private final ValidadorDeEndpoint validador;
    private final Clock clock;
    private final HttpClient http;
    private final Duration tempoLimite;

    @org.springframework.beans.factory.annotation.Autowired
    public WebPushCanal(ParametrosDeNotificacao parametros, Clock clock) {
        this(vapidDe(parametros), new ValidadorDeEndpoint(parametros.hostsPermitidos()), clock, TEMPO_LIMITE);
    }

    public WebPushCanal(@Nullable Vapid vapid, ValidadorDeEndpoint validador, Clock clock, Duration tempoLimite) {
        this.vapid = vapid;
        this.validador = validador;
        this.clock = clock;
        this.tempoLimite = tempoLimite;
        this.http = HttpClient.newBuilder().connectTimeout(tempoLimite).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static @Nullable Vapid vapidDe(ParametrosDeNotificacao parametros) {
        if (!parametros.vapidConfigurada()) {
            return null;
        }
        try {
            return new Vapid(parametros.vapidPublica(), parametros.vapidPrivada(), parametros.assunto());
        } catch (IllegalArgumentException invalida) {
            // A mensagem já não traz valor de chave; o canal fica sem configuração em vez de derrubar a API.
            LOG.error("{}", invalida.getMessage());
            return null;
        }
    }

    @Override
    public Canal canal() {
        return Canal.WEBPUSH;
    }

    @Override
    public ResultadoDoEnvio enviar(AvisoParaEnvio aviso, DestinoDoAviso destino) {
        Aparelho aparelho = destino.aparelho();
        if (aparelho == null) {
            return ResultadoDoEnvio.DESTINO_INVALIDO;
        }
        if (vapid == null) {
            return ResultadoDoEnvio.NAO_CONFIGURADO;
        }
        // Defesa em profundidade: mesmo que uma inscrição antiga ou forjada chegue aqui, host fora da lista nunca é chamado (E9).
        if (!validador.aceita(aparelho.endpoint())) {
            return ResultadoDoEnvio.DESTINO_INVALIDO;
        }
        CriptografiaWebPush.Cifrado cifrado;
        try {
            cifrado = CriptografiaWebPush.cifrar(corpoDoAviso(aviso), decodificar(aparelho.chaveP256dh()),
                    decodificar(aparelho.chaveAuth()));
        } catch (IllegalArgumentException chaveInvalida) {
            return ResultadoDoEnvio.DESTINO_INVALIDO;
        }
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(aparelho.endpoint())).timeout(tempoLimite)
                .header("Authorization", vapid.autorizacao(ValidadorDeEndpoint.audiencia(aparelho.endpoint()), clock.instant()))
                .header("Content-Encoding", "aes128gcm").header("Content-Type", "application/octet-stream")
                .header("TTL", Integer.toString(TTL_EM_SEGUNDOS)).header("Urgency", "high")
                .POST(HttpRequest.BodyPublishers.ofByteArray(cifrado.corpo())).build();
        try {
            int status = http.send(pedido, HttpResponse.BodyHandlers.discarding()).statusCode();
            return resultadoDe(status);
        } catch (HttpTimeoutException demorou) {
            return ResultadoDoEnvio.TENTAR_DEPOIS;
        } catch (IOException falhaDeRede) {
            return ResultadoDoEnvio.TENTAR_DEPOIS;
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            return ResultadoDoEnvio.TENTAR_DEPOIS;
        }
    }

    static ResultadoDoEnvio resultadoDe(int status) {
        if (status >= 200 && status < 300) {
            return ResultadoDoEnvio.ENVIADO;
        }
        if (status == 429 || status >= 500) {
            return ResultadoDoEnvio.TENTAR_DEPOIS;
        }
        // Só 404 e 410 dizem que a inscrição morreu (RFC 8030): apagar. O resto (400, 401, 403, 413...) pode ser o NOSSO erro (par VAPID
        // trocado, corpo grande): a inscrição fica e o aviso tenta até o limite (D178). Sem dado pessoal no log.
        if (status == 404 || status == 410) {
            return ResultadoDoEnvio.DESTINO_INVALIDO;
        }
        LOG.warn("push recusado status={}, confira o par VAPID e o tamanho do aviso", status);
        return ResultadoDoEnvio.RECUSADO;
    }

    /** O JSON que o service worker lê (A23): título, texto, link e tag; nunca dado de cliente. */
    static byte[] corpoDoAviso(AvisoParaEnvio aviso) {
        String texto = aviso.texto();
        byte[] json = json(aviso, texto);
        while (json.length > LIMITE_DO_CORPO && texto.length() > 1) {
            texto = texto.substring(0, texto.length() / 2);
            json = json(aviso, texto);
        }
        return json;
    }

    private static byte[] json(AvisoParaEnvio aviso, String texto) {
        return ("{\"titulo\":" + literal(aviso.titulo()) + ",\"texto\":" + literal(texto) + ",\"link\":" + literal(aviso.link())
                + ",\"tag\":" + literal(aviso.tag()) + "}").getBytes(StandardCharsets.UTF_8);
    }

    private static String literal(String valor) {
        StringBuilder saida = new StringBuilder("\"");
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '"' -> saida.append("\\\"");
                case '\\' -> saida.append("\\\\");
                case '\n' -> saida.append("\\n");
                case '\r' -> saida.append("\\r");
                case '\t' -> saida.append("\\t");
                default -> {
                    if (c < 0x20) {
                        saida.append(String.format("\\u%04x", (int) c));
                    } else {
                        saida.append(c);
                    }
                }
            }
        }
        return saida.append('"').toString();
    }

    /** O navegador manda base64url; aceita também com preenchimento ou no alfabeto comum. */
    static byte[] decodificar(String valor) {
        String url = valor.replace('+', '-').replace('/', '_').replace("=", "");
        return Base64.getUrlDecoder().decode(url);
    }

    @Override
    public String toString() {
        return "WebPushCanal[" + (vapid == null ? "sem VAPID" : "configurado") + "]";
    }
}
