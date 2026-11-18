package io.github.k3vinrich4rd.altashoras.cep.consulta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/**
 * A22: no perfil de teste os dois provedores têm timeout de conexão de 1 s e de leitura de 2 s, e as URLs base apontam
 * para a própria máquina (porta fechada): nenhum teste sai para a internet (D59). Usa o mesmo contexto dos outros testes
 * da API, sem os provedores falsos, que é onde a configuração de produção aparece de verdade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class CepConfigTest {

    @Autowired
    CepProps props;

    @Test
    @DisplayName("A22: timeout de conexão de 1 s e de leitura de 2 s, e as URLs base dos dois provedores apontam para localhost")
    void timeoutsEUrlsDoPerfilDeTeste() {
        assertThat(props.conexaoTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(props.leituraTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(props.viacepUrl()).startsWith("http://localhost");
        assertThat(props.brasilapiUrl()).startsWith("http://localhost");
    }

    @Test
    @DisplayName("A22: o cliente montado pela configuração desiste de um servidor que não responde em cerca de 2 s")
    void leituraRealmenteExpiraEm2Segundos() throws Exception {
        CountDownLatch liberar = new CountDownLatch(1);
        HttpServer mudo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mudo.createContext("/", troca -> {
            try {
                liberar.await(10, TimeUnit.SECONDS); // o servidor aceita e nunca responde
            } catch (InterruptedException interrompido) {
                Thread.currentThread().interrupt();
            }
            troca.close();
        });
        mudo.start();
        try {
            RestClient cliente = RestClient.builder().requestFactory(CepConfig.fabricaComTimeouts(props))
                    .baseUrl("http://127.0.0.1:" + mudo.getAddress().getPort()).build();

            long inicio = System.nanoTime();
            assertThatThrownBy(() -> cliente.get().uri("/ws/{cep}/json/", "01001000").retrieve().body(String.class))
                    .isInstanceOf(ResourceAccessException.class);
            Duration gasto = Duration.ofNanos(System.nanoTime() - inicio);

            assertThat(gasto).isBetween(Duration.ofMillis(1800), Duration.ofMillis(3500));
        } finally {
            liberar.countDown();
            mudo.stop(0);
        }
    }
}
