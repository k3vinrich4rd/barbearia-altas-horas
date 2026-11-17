package io.github.k3vinrich4rd.altashoras.pagamento.gateway;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/** Rede fora, timeout ou resposta de erro do gateway: vira 503 GATEWAY_INDISPONIVEL (E27). Sem corpo da resposta. */
public class GatewayIndisponivelException extends ExcecaoDeNegocio {

    public GatewayIndisponivelException() {
        super(CodigoErro.GATEWAY_INDISPONIVEL);
    }
}
