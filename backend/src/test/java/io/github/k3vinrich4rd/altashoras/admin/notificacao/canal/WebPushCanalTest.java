package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.DestinoDoAviso.Aparelho;

/**
 * A20 a A23 da Fatia 13: o {@code WebPushCanal} contra um servidor HTTP LOCAL falso (o {@code HttpServer} do JDK). Nenhum teste
 * fala com a rede real; o texto do aviso nunca aparece em claro no que o servidor vê.
 */
class WebPushCanalTest {

    private record Visto(String autorizacao, String codificacao, String ttl, String urgencia, byte[] corpo) {
    }

    private static final Instant AGORA = Instant.parse("2026-11-24T12:00:00Z");

    private HttpServer servidor;
    private final List<Visto> vistos = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(201);
    private volatile long demoraEmMs = 0;
    private ChavesDeTeste.Par vapid;
    private ChavesDeTeste.Aparelho aparelho;
    private WebPushCanal canal;

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(0), 0);
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.createContext("/", troca -> {
            vistos.add(new Visto(troca.getRequestHeaders().getFirst("Authorization"),
                    troca.getRequestHeaders().getFirst("Content-Encoding"), troca.getRequestHeaders().getFirst("TTL"),
                    troca.getRequestHeaders().getFirst("Urgency"), troca.getRequestBody().readAllBytes()));
            try {
                Thread.sleep(demoraEmMs);
            } catch (InterruptedException interrompido) {
                Thread.currentThread().interrupt();
            }
            troca.sendResponseHeaders(status.get(), -1);
            troca.close();
        });
        servidor.start();
        vapid = ChavesDeTeste.par();
        aparelho = ChavesDeTeste.aparelho();
        canal = new WebPushCanal(new Vapid(vapid.publica(), vapid.privada(), "https://site.exemplo.test"),
                new ValidadorDeEndpoint(List.of("localhost"), false), Clock.fixed(AGORA, ZoneOffset.UTC), Duration.ofSeconds(5));
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private DestinoDoAviso destino() {
        String endpoint = "http://localhost:" + servidor.getAddress().getPort() + "/push/" + UUID.randomUUID();
        return new DestinoDoAviso(UUID.randomUUID(), new Aparelho(endpoint, aparelho.p256dh(), aparelho.auth()), null);
    }

    private static AvisoParaEnvio aviso() {
        return new AvisoParaEnvio("Altas Horas", "Novo horário: Corte Degradê, qui 27/11 às 10:00", "/painel", UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("A20: servidor local devolve 201: ENVIADO; o servidor viu Authorization vapid assinado, Content-Encoding aes128gcm, TTL, Urgency e um corpo criptografado (o texto não aparece em claro)")
    void envia() throws Exception {
        ResultadoDoEnvio resultado = canal.enviar(aviso(), destino());

        assertThat(resultado).isEqualTo(ResultadoDoEnvio.ENVIADO);
        assertThat(vistos).hasSize(1);
        Visto visto = vistos.getFirst();
        assertThat(visto.autorizacao()).startsWith("vapid t=").contains(", k=" + vapid.publica());
        assertThat(visto.codificacao()).isEqualTo("aes128gcm");
        assertThat(visto.ttl()).isEqualTo("3600");
        assertThat(visto.urgencia()).isEqualTo("high");
        assertThat(new String(visto.corpo(), StandardCharsets.ISO_8859_1)).doesNotContain("Novo hor").doesNotContain("Corte");
        // O aparelho abre o corpo e acha o JSON do aviso (A23).
        assertThat(CriptografiaWebPushTest.abrir(visto.corpo(), aparelho)).contains("Novo horário: Corte Degradê");
        // O JWT do VAPID vale de verdade: assinatura ES256 conferida com a chave pública, audiência e prazo certos.
        String jwt = visto.autorizacao().substring("vapid t=".length(), visto.autorizacao().indexOf(", k="));
        String[] partes = jwt.split("\\.");
        Signature conferencia = Signature.getInstance("SHA256withECDSAinP1363Format");
        conferencia.initVerify(vapid.chaves().getPublic());
        conferencia.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(conferencia.verify(Base64.getUrlDecoder().decode(partes[2]))).isTrue();
        String corpoDoJwt = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        assertThat(corpoDoJwt).contains("\"aud\":\"http://localhost:" + servidor.getAddress().getPort() + "\"")
                .contains("\"exp\":" + AGORA.plusSeconds(12 * 3600).getEpochSecond()).contains("\"sub\":\"https://site.exemplo.test\"");
    }

    @Test
    @DisplayName("A21: o servidor devolve 404 e, em outro cenário, 410: DESTINO_INVALIDO nos dois")
    void destinoMorto() {
        status.set(404);
        assertThat(canal.enviar(aviso(), destino())).isEqualTo(ResultadoDoEnvio.DESTINO_INVALIDO);
        status.set(410);
        assertThat(canal.enviar(aviso(), destino())).isEqualTo(ResultadoDoEnvio.DESTINO_INVALIDO);
    }

    @Test
    @DisplayName("A22: 429, depois 503, depois uma resposta que demora 10 s: TENTAR_DEPOIS nos três, e o terceiro volta em até ~6 s (timeout de 5 s)")
    void falhasPassageiras() {
        status.set(429);
        assertThat(canal.enviar(aviso(), destino())).isEqualTo(ResultadoDoEnvio.TENTAR_DEPOIS);
        status.set(503);
        assertThat(canal.enviar(aviso(), destino())).isEqualTo(ResultadoDoEnvio.TENTAR_DEPOIS);

        status.set(201);
        demoraEmMs = 10_000;
        long inicio = System.nanoTime();
        ResultadoDoEnvio resultado = canal.enviar(aviso(), destino());
        Duration levou = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(resultado).isEqualTo(ResultadoDoEnvio.TENTAR_DEPOIS);
        assertThat(levou).isLessThan(Duration.ofSeconds(7)).isGreaterThanOrEqualTo(Duration.ofSeconds(4));
    }

    @Test
    @DisplayName("A23: o payload tem título, texto no formato 'Novo horário: serviço, dia dd/MM às HH:mm', link /painel e tag, com no máximo 3 KB e sem nome, WhatsApp, e-mail nem valor")
    void payload() {
        byte[] json = WebPushCanal.corpoDoAviso(aviso());
        String texto = new String(json, StandardCharsets.UTF_8);

        assertThat(texto).contains("\"titulo\":\"Altas Horas\"")
                .contains("\"texto\":\"Novo horário: Corte Degradê, qui 27/11 às 10:00\"").contains("\"link\":\"/painel\"")
                .contains("\"tag\":");
        assertThat(texto).doesNotContainIgnoringCase("whatsapp").doesNotContain("@").doesNotContain("R$").doesNotContain("nome");
        assertThat(json.length).isLessThanOrEqualTo(3 * 1024);
        // Texto enorme é cortado para caber nos 3 KB.
        AvisoParaEnvio gigante = new AvisoParaEnvio("Altas Horas", "x".repeat(10_000), "/painel", "t");
        assertThat(WebPushCanal.corpoDoAviso(gigante).length).isLessThanOrEqualTo(3 * 1024);
    }

    @Test
    @DisplayName("A24: endpoint de host fora da lista nunca é chamado, mesmo chegando ao canal (defesa em profundidade)")
    void hostForaDaListaNaoEhChamado() {
        WebPushCanal estrito = new WebPushCanal(new Vapid(vapid.publica(), vapid.privada(), "https://site.exemplo.test"),
                new ValidadorDeEndpoint(List.of("fcm.googleapis.com")), Clock.fixed(AGORA, ZoneOffset.UTC), Duration.ofSeconds(5));

        assertThat(estrito.enviar(aviso(), destino())).isEqualTo(ResultadoDoEnvio.DESTINO_INVALIDO);
        assertThat(vistos).isEmpty();
    }

    @Test
    @DisplayName("A32: sem VAPID configurada o canal devolve NAO_CONFIGURADO e não chama ninguém; o toString nunca traz chave")
    void semVapid() {
        WebPushCanal semChave = new WebPushCanal(null, new ValidadorDeEndpoint(List.of("localhost"), false),
                Clock.fixed(AGORA, ZoneOffset.UTC), Duration.ofSeconds(5));

        assertThat(semChave.enviar(aviso(), destino())).isEqualTo(ResultadoDoEnvio.NAO_CONFIGURADO);
        assertThat(vistos).isEmpty();
        assertThat(new Vapid(vapid.publica(), vapid.privada(), "https://site.exemplo.test").toString()).doesNotContain(vapid.privada())
                .doesNotContain(vapid.publica());
    }
}
