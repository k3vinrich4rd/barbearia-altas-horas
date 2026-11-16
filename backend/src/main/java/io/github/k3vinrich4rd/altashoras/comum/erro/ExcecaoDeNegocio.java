package io.github.k3vinrich4rd.altashoras.comum.erro;

/**
 * Erro previsto pelo contrato: carrega o {@link CodigoErro} e, se quiser, um detalhe melhor que o padrão.
 * O TratadorGlobalDeErros transforma em application/problem+json. O detalhe nunca leva dado pessoal.
 */
public class ExcecaoDeNegocio extends RuntimeException {

    private final transient CodigoErro codigo;

    public ExcecaoDeNegocio(CodigoErro codigo) {
        this(codigo, codigo.detalhePadrao());
    }

    public ExcecaoDeNegocio(CodigoErro codigo, String detalhe) {
        super(detalhe);
        this.codigo = codigo;
    }

    public CodigoErro codigo() {
        return codigo;
    }
}
