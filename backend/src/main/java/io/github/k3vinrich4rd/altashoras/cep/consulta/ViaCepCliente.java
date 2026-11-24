package io.github.k3vinrich4rd.altashoras.cep.consulta;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

import io.github.k3vinrich4rd.altashoras.cep.Endereco;
import io.github.k3vinrich4rd.altashoras.comum.valor.Cep;

/**
 * ViaCEP, o primeiro da fila (ADR 0008): {@code GET /ws/{cep}/json/}. CEP que não existe volta como {@code 200} com
 * {@code {"erro": true}} (ou {@code 404}): isso é resposta, não falha, e a BrasilAPI nem é consultada (E3).
 */
@Component
class ViaCepCliente extends ProvedorDeCep {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resposta(@Nullable String cep, @Nullable String logradouro, @Nullable String bairro,
            @Nullable String localidade, @Nullable String uf, @Nullable String ibge, @Nullable Object erro) {
    }

    ViaCepCliente(RestClient.Builder construtor, CepProps props) {
        super("VIACEP", construtor.clone().baseUrl(props.viacepUrl()).build());
    }

    @Override
    String caminho() {
        return "/ws/{cep}/json/";
    }

    @Override
    Endereco.Fonte fonte() {
        return Endereco.Fonte.VIACEP;
    }

    @Override
    Optional<Endereco> converter(Cep cep, ConvertibleClientHttpResponse resposta) throws IOException {
        Resposta corpo = resposta.bodyTo(Resposta.class);
        if (corpo == null) {
            throw falha("CORPO_INVALIDO");
        }
        if (Boolean.TRUE.equals(corpo.erro()) || "true".equalsIgnoreCase(String.valueOf(corpo.erro()))) {
            return Optional.empty();
        }
        if (corpo.cep() == null || !Cep.de(corpo.cep()).digitos().equals(cep.digitos())
                || texto(corpo.localidade()) == null || !ufValida(corpo.uf())) {
            throw falha("CORPO_INVALIDO");
        }
        return Optional.of(new Endereco(cep.digitos(), texto(corpo.logradouro()), texto(corpo.bairro()),
                texto(corpo.localidade()), corpo.uf(), texto(corpo.ibge()), fonte()));
    }
}
