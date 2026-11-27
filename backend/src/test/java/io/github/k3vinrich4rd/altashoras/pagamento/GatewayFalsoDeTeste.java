package io.github.k3vinrich4rd.altashoras.pagamento;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.github.k3vinrich4rd.altashoras.pagamento.gateway.CobrancaNoGateway;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.GatewayDePagamento;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.GatewayIndisponivelException;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.NovaCobranca;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.Provedor;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.StatusNoGateway;

/**
 * O gateway dos testes: em memória, sem rede, com contador de chamadas por método e falhas que o teste liga. Tira o
 * lugar do GatewaySimulado (@Primary). Devolve sempre um QR em base64 e um link de checkout, para provar o A22 e o A23.
 * Quem usa precisa chamar {@link GatewayFalso#reiniciar()} antes de cada teste (o bean vive junto do contexto).
 */
@TestConfiguration(proxyBeanMethods = false)
public class GatewayFalsoDeTeste {

    @Bean
    @Primary
    GatewayFalso gatewayFalso() {
        return new GatewayFalso();
    }

    public static class GatewayFalso implements GatewayDePagamento {

        public final AtomicInteger chamadasDeCriar = new AtomicInteger();
        public final AtomicInteger chamadasDeBuscar = new AtomicInteger();
        public final AtomicInteger chamadasDeConsultar = new AtomicInteger();

        private final Map<String, StatusNoGateway> status = new ConcurrentHashMap<>();
        private final Map<UUID, CobrancaNoGateway> porReferencia = new ConcurrentHashMap<>();
        private final AtomicInteger consultasQueFalham = new AtomicInteger();
        private volatile boolean consultaSempreFalha;
        private volatile boolean indisponivel;
        private volatile boolean perdeRespostaAoCriar;
        /** O último pedido de criação que chegou ao gateway (Fatia 15, A41: a validade enviada). */
        public volatile NovaCobranca ultimoPedido;

        public void reiniciar() {
            chamadasDeCriar.set(0);
            chamadasDeBuscar.set(0);
            chamadasDeConsultar.set(0);
            consultasQueFalham.set(0);
            consultaSempreFalha = false;
            indisponivel = false;
            perdeRespostaAoCriar = false;
            ultimoPedido = null;
        }

        /** Timeout ao criar. Com {@code perdeResposta}, o gateway CRIA a cobrança e só a resposta se perde (A30). */
        public void ficarIndisponivel(boolean perdeResposta) {
            indisponivel = true;
            perdeRespostaAoCriar = perdeResposta;
        }

        public void voltar() {
            indisponivel = false;
            perdeRespostaAoCriar = false;
        }

        /** As próximas {@code vezes} consultas lançam RuntimeException (A37). */
        public void falharProximasConsultas(int vezes) {
            consultasQueFalham.set(vezes);
        }

        public void falharSempreAoConsultar() {
            consultaSempreFalha = true;
        }

        public void marcarPago(String idProvedor) {
            status.put(idProvedor, StatusNoGateway.PAGA);
        }

        @Override
        public Provedor provedor() {
            return Provedor.SIMULADO;
        }

        @Override
        public CobrancaNoGateway criar(NovaCobranca pedido) {
            chamadasDeCriar.incrementAndGet();
            ultimoPedido = pedido;
            if (indisponivel && !perdeRespostaAoCriar) {
                throw new GatewayIndisponivelException();
            }
            String id = "fake_" + UUID.randomUUID();
            CobrancaNoGateway criada = new CobrancaNoGateway(id, "cus_" + UUID.randomUUID(),
                    "00020126FAKE" + pedido.cobrancaId(), "UVJDT0RFLUZBTFNP", "https://checkout.exemplo.test/" + id);
            status.put(id, StatusNoGateway.PENDENTE);
            porReferencia.put(pedido.cobrancaId(), criada);
            if (indisponivel) {
                throw new GatewayIndisponivelException();
            }
            return criada;
        }

        @Override
        public Optional<CobrancaNoGateway> buscarPorReferencia(UUID cobrancaId) {
            chamadasDeBuscar.incrementAndGet();
            return Optional.ofNullable(porReferencia.get(cobrancaId));
        }

        @Override
        public StatusNoGateway consultar(String idProvedor) {
            chamadasDeConsultar.incrementAndGet();
            if (consultaSempreFalha || consultasQueFalham.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new IllegalStateException("falha simulada ao consultar o gateway");
            }
            return status.getOrDefault(idProvedor, StatusNoGateway.PENDENTE);
        }
    }
}
