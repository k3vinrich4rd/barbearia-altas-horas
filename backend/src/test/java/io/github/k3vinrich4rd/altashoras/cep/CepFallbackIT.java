package io.github.k3vinrich4rd.altashoras.cep;

import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.BRASILAPI_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_INEXISTENTE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.erro503;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.json;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.naoEJson;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.naoEncontrado;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.timeout;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A5 e A6: o que é "falha" e passa para a BrasilAPI, e o que é "o CEP não existe" e não passa (E3, E4). */
@CepTeste
class CepFallbackIT extends CepTesteBase {

    private void esperaBrasilApiEResponde() throws Exception {
        provedores.brasilApi(CEP_SE, json(BRASILAPI_SE));
        consultar(CEP_SE).andExpect(status().isOk()).andExpect(jsonPath("$.fonte").value("BRASILAPI"))
                .andExpect(jsonPath("$.logradouro").value("Praça da Sé")).andExpect(jsonPath("$.bairro").value("Sé"))
                .andExpect(jsonPath("$.cidade").value("São Paulo")).andExpect(jsonPath("$.uf").value("SP"));
    }

    @Test
    @DisplayName("A5: ViaCEP em timeout: a BrasilAPI responde e vem com fonte BRASILAPI e os campos mapeados")
    void viaCepEmTimeout() throws Exception {
        provedores.viaCep(CEP_SE, timeout());
        esperaBrasilApiEResponde();
    }

    @Test
    @DisplayName("A5: ViaCEP com 503: a BrasilAPI responde e vem com fonte BRASILAPI e os campos mapeados")
    void viaCepComErro503() throws Exception {
        provedores.viaCep(CEP_SE, erro503());
        esperaBrasilApiEResponde();
    }

    @Test
    @DisplayName("A5: ViaCEP com corpo que não é JSON: a BrasilAPI responde e vem com fonte BRASILAPI e os campos mapeados")
    void viaCepComCorpoQueNaoEJson() throws Exception {
        provedores.viaCep(CEP_SE, naoEJson());
        esperaBrasilApiEResponde();
    }

    @Test
    @DisplayName("A5: ViaCEP com 429 (limite do provedor): também passa para a BrasilAPI")
    void viaCepComLimiteDoProvedor() throws Exception {
        provedores.viaCep(CEP_SE, org.springframework.test.web.client.response.MockRestResponseCreators
                .withTooManyRequests());
        esperaBrasilApiEResponde();
    }

    @Test
    @DisplayName("A6: ViaCEP fora e BrasilAPI responde 404 para 99999999: 404 CEP_NAO_ENCONTRADO")
    void brasilApiDizQueNaoExiste() throws Exception {
        provedores.viaCep(CEP_INEXISTENTE, erro503());
        provedores.brasilApi(CEP_INEXISTENTE, naoEncontrado());

        consultar(CEP_INEXISTENTE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CEP_NAO_ENCONTRADO"));
    }
}
