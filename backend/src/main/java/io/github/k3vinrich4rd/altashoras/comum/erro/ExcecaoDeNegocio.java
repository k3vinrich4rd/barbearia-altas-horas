package io.github.k3vinrich4rd.altashoras.comum.erro;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Erro previsto pelo contrato: carrega o {@link CodigoErro} e, se quiser, um detalhe melhor que o padrão.
 * O TratadorGlobalDeErros transforma em application/problem+json. O detalhe nunca leva dado pessoal.
 * Quando o erro é de um campo do corpo (ex.: o {@code usuarioId} não é de um barbeiro), {@link #campo()} diz qual,
 * e a resposta traz o item em {@code errors[]}, como a validação do Bean Validation.
 * Extensões RFC 9457 previstas no contrato (ex.: {@code conflitos[]} do FOLGA_COM_RESERVAS, 1.12.0) entram por
 * {@link #comExtensao}; a resposta com extensão sai com {@code Cache-Control: no-store} e a extensão nunca vai para o log.
 */
public class ExcecaoDeNegocio extends RuntimeException {

    private final transient CodigoErro codigo;
    private final @Nullable String campo;
    private final transient Map<String, Object> extensoes = new LinkedHashMap<>();

    public ExcecaoDeNegocio(CodigoErro codigo) {
        this(codigo, codigo.detalhePadrao());
    }

    public ExcecaoDeNegocio(CodigoErro codigo, String detalhe) {
        this(codigo, detalhe, null);
    }

    private ExcecaoDeNegocio(CodigoErro codigo, String detalhe, @Nullable String campo) {
        super(detalhe);
        this.codigo = codigo;
        this.campo = campo;
    }

    /** 400 VALIDACAO_INVALIDA apontando o campo errado em {@code errors[]}. */
    public static ExcecaoDeNegocio campoInvalido(String campo, String mensagem) {
        return new ExcecaoDeNegocio(CodigoErro.VALIDACAO_INVALIDA, mensagem, campo);
    }

    public CodigoErro codigo() {
        return codigo;
    }

    /** Acrescenta uma extensão ao corpo do problema (o nome vem do contrato). Devolve a própria exceção. */
    public ExcecaoDeNegocio comExtensao(String nome, Object valor) {
        extensoes.put(nome, valor);
        return this;
    }

    public Map<String, Object> extensoes() {
        return Map.copyOf(extensoes);
    }

    /** O campo do corpo a que o erro se refere; nulo quando o erro é do pedido como um todo. */
    public @Nullable String campo() {
        return campo;
    }
}
