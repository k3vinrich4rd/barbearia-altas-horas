package io.github.k3vinrich4rd.altashoras.cep.consulta;

/**
 * O provedor não respondeu de forma utilizável: passa para o próximo (E3). Só o {@code tipo} sobe (vai para o log):
 * a causa original traz a URL, e a URL traz o CEP.
 */
class FalhaDoProvedor extends RuntimeException {

    private final String provedor;
    private final String tipo;

    FalhaDoProvedor(String provedor, String tipo) {
        super("falha do provedor " + provedor + ": " + tipo, null, false, false);
        this.provedor = provedor;
        this.tipo = tipo;
    }

    String provedor() {
        return provedor;
    }

    String tipo() {
        return tipo;
    }
}
