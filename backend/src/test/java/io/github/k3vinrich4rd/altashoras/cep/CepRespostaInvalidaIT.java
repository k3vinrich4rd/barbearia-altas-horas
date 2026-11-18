package io.github.k3vinrich4rd.altashoras.cep;

import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.BRASILAPI_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.json;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A13: resposta 200 do ViaCEP que não serve (CEP trocado, uf inválida, corpo vazio) vale como falha (E3, E4). */
@CepTeste
class CepRespostaInvalidaIT extends CepTesteBase {

    private void viaCepInvalidoEBrasilApiResponde(String corpoDoViaCep) throws Exception {
        provedores.viaCep(CEP_SE, json(corpoDoViaCep));
        provedores.brasilApi(CEP_SE, json(BRASILAPI_SE));
        consultar(CEP_SE).andExpect(status().isOk()).andExpect(jsonPath("$.fonte").value("BRASILAPI"))
                .andExpect(jsonPath("$.bairro").value("Sé"));
    }

    @Test
    @DisplayName("A13: ViaCEP responde 200 com o cep de outro CEP: vale como falha e cai na BrasilAPI")
    void cepDiferenteDoPedido() throws Exception {
        viaCepInvalidoEBrasilApiResponde("""
                {"cep":"20040-020","logradouro":"Rua da Assembleia","bairro":"Centro","localidade":"Rio de Janeiro","uf":"RJ"}
                """);
    }

    @Test
    @DisplayName("A13: ViaCEP responde 200 com uf = \"São Paulo\" (não são 2 letras): vale como falha e cai na BrasilAPI")
    void ufInvalida() throws Exception {
        viaCepInvalidoEBrasilApiResponde("""
                {"cep":"01001-000","logradouro":"Praça da Sé","bairro":"Sé","localidade":"São Paulo","uf":"São Paulo"}
                """);
    }

    @Test
    @DisplayName("A13: ViaCEP responde 200 com corpo {}: vale como falha e cai na BrasilAPI")
    void corpoVazio() throws Exception {
        viaCepInvalidoEBrasilApiResponde("{}");
    }

    @Test
    @DisplayName("A13: a BrasilAPI também é conferida: resposta dela com cep trocado, com o ViaCEP fora, dá 503")
    void brasilApiComCepTrocado() throws Exception {
        provedores.viaCep(CEP_SE, ProvedoresFalsos.erro503());
        provedores.brasilApi(CEP_SE, json("""
                {"cep":"20040020","state":"RJ","city":"Rio de Janeiro","neighborhood":"Centro","street":"Rua da Assembleia"}
                """));
        consultar(CEP_SE).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CEP_INDISPONIVEL"));
    }
}
