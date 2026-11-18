package io.github.k3vinrich4rd.altashoras.cep;

import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_INEXISTENTE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_ERRO;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.json;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A1 a A4: a consulta pública de CEP (E1, E2). Nenhuma chamada sai para a internet: os provedores são falsos. */
@CepTeste
class CepIT extends CepTesteBase {

    @Test
    @DisplayName("A1: ViaCEP devolve a Praça da Sé: 200 com logradouro, bairro, cidade, uf e fonte VIACEP, numa única chamada com os 8 dígitos")
    void viaCepResponde() throws Exception {
        provedores.viaCep(CEP_SE, json(VIACEP_SE));

        consultar(CEP_SE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cep").value("01001000"))
                .andExpect(jsonPath("$.logradouro").value("Praça da Sé"))
                .andExpect(jsonPath("$.bairro").value("Sé"))
                .andExpect(jsonPath("$.cidade").value("São Paulo"))
                .andExpect(jsonPath("$.uf").value("SP"))
                .andExpect(jsonPath("$.fonte").value("VIACEP"));
        // A expectativa é a URL exata do ViaCEP com 8 dígitos: duas chamadas ou outra URL falhariam aqui.
        provedores.verificar();
    }

    @Test
    @DisplayName("A2: com hífen (01001-000) a resposta é a mesma e a requisição ao provedor leva só dígitos")
    void comHifen() throws Exception {
        provedores.viaCep(CEP_SE, json(VIACEP_SE));

        consultar("01001-000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cep").value("01001000"))
                .andExpect(jsonPath("$.bairro").value("Sé"))
                .andExpect(jsonPath("$.fonte").value("VIACEP"));
        provedores.verificar();
    }

    @Test
    @DisplayName("A4: ViaCEP responde {\"erro\": true} para 99999999: 404 CEP_NAO_ENCONTRADO e a BrasilAPI não é consultada")
    void cepInexistenteNaoVaiParaOFallback() throws Exception {
        provedores.viaCep(CEP_INEXISTENTE, json(VIACEP_ERRO));

        consultar(CEP_INEXISTENTE).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CEP_NAO_ENCONTRADO"));
        provedores.verificar(); // só a expectativa do ViaCEP existe: uma chamada à BrasilAPI teria falhado na hora
    }

    @Test
    @DisplayName("A3: 123, 0100100, 010010000, abcdefgh e 01001%2F000 dão 400 CEP_INVALIDO e zero chamadas externas")
    void formatosInvalidos() throws Exception {
        // Sem nenhuma expectativa: qualquer chamada ao provedor lançaria AssertionError e o teste falharia.
        for (String ruim : new String[] {"123", "0100100", "010010000", "abcdefgh", "01001%2F000", "0100100a", "01001--000"}) {
            consultar(ruim)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CEP_INVALIDO"));
        }
        provedores.verificar();
    }
}
