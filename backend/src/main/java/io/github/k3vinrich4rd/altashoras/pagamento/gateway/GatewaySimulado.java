package io.github.k3vinrich4rd.altashoras.pagamento.gateway;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * O gateway do beta (E28, D59): guarda tudo em memória, gera um Pix de demonstração e um link de checkout que leva
 * à confirmação simulada. Nenhuma conta externa, nenhum custo, nenhum dinheiro real. É o padrão em todo o beta.
 * A memória some se a API reiniciar: id desconhecido é PENDENTE, e {@link #marcarPago} funciona mesmo assim.
 */
@Component
@ConditionalOnProperty(name = "app.pagamento.modo", havingValue = "simulado", matchIfMissing = true)
public class GatewaySimulado implements GatewayDePagamento {

    private final Map<String, StatusNoGateway> cobrancas = new ConcurrentHashMap<>();
    private final Map<UUID, CobrancaNoGateway> porReferencia = new ConcurrentHashMap<>();
    private final String urlCheckout;

    GatewaySimulado(@Value("${app.pagamento.url-checkout-simulado:http://localhost:3000/pagamento-simulado}") String urlCheckout) {
        this.urlCheckout = urlCheckout;
    }

    @Override
    public Provedor provedor() {
        return Provedor.SIMULADO;
    }

    @Override
    public CobrancaNoGateway criar(NovaCobranca pedido) {
        String id = "sim_" + UUID.randomUUID();
        // Sem imagem de QR: o front gera a partir do texto do copia e cola (D68).
        CobrancaNoGateway criada = new CobrancaNoGateway(id, "sim_cli_" + UUID.randomUUID(),
                "SIMULADO-" + pedido.cobrancaId(), null, urlCheckout + "/" + pedido.cobrancaId());
        cobrancas.put(id, StatusNoGateway.PENDENTE);
        porReferencia.put(pedido.cobrancaId(), criada);
        return criada;
    }

    @Override
    public Optional<CobrancaNoGateway> buscarPorReferencia(UUID cobrancaId) {
        return Optional.ofNullable(porReferencia.get(cobrancaId));
    }

    @Override
    public StatusNoGateway consultar(String idProvedor) {
        return cobrancas.getOrDefault(idProvedor, StatusNoGateway.PENDENTE);
    }

    /** O "cliente pagou" da simulação (E28). */
    public void marcarPago(String idProvedor) {
        cobrancas.put(idProvedor, StatusNoGateway.PAGA);
    }
}
