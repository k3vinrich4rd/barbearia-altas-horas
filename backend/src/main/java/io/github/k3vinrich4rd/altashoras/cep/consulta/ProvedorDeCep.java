package io.github.k3vinrich4rd.altashoras.cep.consulta;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

import io.github.k3vinrich4rd.altashoras.cep.Endereco;
import io.github.k3vinrich4rd.altashoras.comum.valor.Cep;

/**
 * Um serviço de CEP de terceiros. Devolve vazio quando o CEP não existe e lança {@link FalhaDoProvedor} quando não
 * consegue responder (E3): timeout, rede, 5xx, 429, outro 4xx, corpo que não é JSON ou resposta que não bate com o que
 * foi pedido. Nada aqui registra URL, corpo nem mensagem de exceção: todos carregam o CEP (R30, E8).
 */
abstract class ProvedorDeCep {

    private final String nome;
    private final RestClient http;

    ProvedorDeCep(String nome, RestClient http) {
        this.nome = nome;
        this.http = http;
    }

    final String nome() {
        return nome;
    }

    /** Caminho com {@code {cep}} no lugar do CEP; o valor entra só como 8 dígitos (E2). */
    abstract String caminho();

    abstract Endereco.Fonte fonte();

    /** Lê o corpo de uma resposta 2xx. Vazio = o provedor disse que o CEP não existe. */
    abstract Optional<Endereco> converter(Cep cep, ConvertibleClientHttpResponse resposta) throws IOException;

    final Optional<Endereco> consultar(Cep cep) {
        try {
            return http.get().uri(caminho(), cep.digitos()).exchange((pedido, resposta) -> {
                int status = resposta.getStatusCode().value();
                if (status == 404) {
                    return Optional.<Endereco>empty();
                }
                if (status == 429) {
                    throw falha("HTTP_429");
                }
                if (status >= 500) {
                    throw falha("HTTP_5XX");
                }
                if (status < 200 || status >= 300) {
                    throw falha("HTTP_4XX");
                }
                return converter(cep, resposta);
            });
        } catch (FalhaDoProvedor falha) {
            throw falha;
        } catch (ResourceAccessException rede) {
            throw falha(tipoDaFalhaDeRede(rede));
        } catch (RuntimeException ilegivel) {
            throw falha("CORPO_INVALIDO");
        }
    }

    final FalhaDoProvedor falha(String tipo) {
        return new FalhaDoProvedor(nome, tipo);
    }

    /** Classifica pela causa, sem guardar a mensagem (ela traz a URL com o CEP). */
    private static String tipoDaFalhaDeRede(ResourceAccessException falha) {
        for (@Nullable Throwable causa = falha; causa != null; causa = causa.getCause()) {
            if (causa instanceof SocketTimeoutException || causa instanceof HttpTimeoutException) {
                return "TIMEOUT";
            }
        }
        return "REDE";
    }

    /** Texto vazio ou só espaço vira nulo; corta em 120 caracteres, o tamanho das colunas de usuario (E4). */
    static @Nullable String texto(@Nullable String bruto) {
        if (bruto == null) {
            return null;
        }
        String limpo = bruto.strip();
        if (limpo.isEmpty()) {
            return null;
        }
        return limpo.length() > 120 ? limpo.substring(0, 120) : limpo;
    }

    /** UF são 2 letras maiúsculas; outra coisa é resposta inválida, porque a coluna tem CHECK (E4). */
    static boolean ufValida(@Nullable String uf) {
        return uf != null && uf.matches("[A-Z]{2}");
    }
}
