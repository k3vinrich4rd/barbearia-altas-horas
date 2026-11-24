package io.github.k3vinrich4rd.altashoras.cep.consulta;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

import io.github.k3vinrich4rd.altashoras.cep.Endereco;
import io.github.k3vinrich4rd.altashoras.comum.valor.Cep;

/**
 * BrasilAPI v2, o fallback (ADR 0008, Fatia 11): {@code GET /api/cep/v2/{cep}}; {@code 404} quer dizer que o CEP não existe. A v2
 * traz o código IBGE em {@code ibge.city}; aceita também o campo {@code ibge} como texto ou número, sem derrubar a leitura.
 */
@Component
class BrasilApiCliente extends ProvedorDeCep {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resposta(@Nullable String cep, @Nullable String street, @Nullable String neighborhood,
            @Nullable String city, @Nullable String state, @Nullable JsonNode ibge) {

        @Nullable String codigoIbge() {
            if (ibge == null || ibge.isNull()) {
                return null;
            }
            JsonNode cidade = ibge.isObject() ? ibge.get("city") : ibge;
            return cidade == null || cidade.isNull() ? null : cidade.asString();
        }
    }

    BrasilApiCliente(RestClient.Builder construtor, CepProps props) {
        super("BRASILAPI", construtor.clone().baseUrl(props.brasilapiUrl()).build());
    }

    @Override
    String caminho() {
        return "/api/cep/v2/{cep}";
    }

    @Override
    Endereco.Fonte fonte() {
        return Endereco.Fonte.BRASILAPI;
    }

    @Override
    Optional<Endereco> converter(Cep cep, ConvertibleClientHttpResponse resposta) throws IOException {
        Resposta corpo = resposta.bodyTo(Resposta.class);
        if (corpo == null || corpo.cep() == null || !Cep.de(corpo.cep()).digitos().equals(cep.digitos())
                || texto(corpo.city()) == null || !ufValida(corpo.state())) {
            throw falha("CORPO_INVALIDO");
        }
        return Optional.of(new Endereco(cep.digitos(), texto(corpo.street()), texto(corpo.neighborhood()),
                texto(corpo.city()), corpo.state(), texto(corpo.codigoIbge()), fonte()));
    }
}
