package io.github.k3vinrich4rd.altashoras.comum.erro;

/** Limite de tentativas estourado (R26): vira 429 com o cabeçalho Retry-After em segundos. */
public class ExcecaoDeLimite extends ExcecaoDeNegocio {

    private final long tentarDeNovoEmSegundos;

    public ExcecaoDeLimite(long tentarDeNovoEmSegundos) {
        super(CodigoErro.LIMITE_EXCEDIDO);
        this.tentarDeNovoEmSegundos = tentarDeNovoEmSegundos;
    }

    public long tentarDeNovoEmSegundos() {
        return tentarDeNovoEmSegundos;
    }
}
