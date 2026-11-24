package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

/** Os canais que o sistema conhece (E7). O nome é o que se escreve em {@code app.notificacao.canais}. */
public enum Canal {
    WEBPUSH("webpush"), EMAIL("email");

    private final String nome;

    Canal(String nome) {
        this.nome = nome;
    }

    public String nome() {
        return nome;
    }
}
