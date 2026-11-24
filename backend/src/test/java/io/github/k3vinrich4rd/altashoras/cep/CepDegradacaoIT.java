package io.github.k3vinrich4rd.altashoras.cep;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.emailNovo;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.json;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_SE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;

/** A7 e A16: os dois provedores fora. A consulta avisa (503); o cadastro não liga (R31). */
@CepTeste
class CepDegradacaoIT extends CepTesteBase {

    @Test
    @DisplayName("A7: ViaCEP e BrasilAPI fora: 503 CEP_INDISPONIVEL em RFC 9457 com traceId, duas tentativas e nenhuma terceira")
    void osDoisForaDao503() throws Exception {
        // Uma expectativa para cada provedor, uma vez só: uma terceira chamada (retry) falharia na hora.
        provedores.osDoisFora(CEP_SE);

        consultar(CEP_SE)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/problem+json")))
                .andExpect(jsonPath("$.code").value("CEP_INDISPONIVEL"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("#cep_indisponivel")));
        provedores.verificar(); // as duas tentativas aconteceram
    }

    @Test
    @DisplayName("A16: cadastro com a caixinha de região e os dois provedores fora: 201, nada de endereço no banco (bairro, cidade e uf nulos) e menos de 3,5 s")
    void cadastroSobreviveAosProvedoresForaDoAr() throws Exception {
        provedores.osDoisFora(CEP_SE);
        String email = emailNovo();

        long inicio = System.nanoTime();
        mvcCadastro(email)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endereco").doesNotExist())
                .andExpect(jsonPath("$.regiaoAutorizada").value(true));
        Duration gasto = Duration.ofNanos(System.nanoTime() - inicio);

        Map<String, Object> linha = jdbc.queryForMap("SELECT bairro, cidade, uf FROM usuario WHERE email = ?", email);
        assertThat(linha.get("bairro")).isNull();
        assertThat(linha.get("cidade")).isNull();
        assertThat(linha.get("uf")).isNull();
        assertThat(gasto).isLessThan(Duration.ofMillis(3500));
        provedores.verificar(); // as duas tentativas aconteceram dentro do cadastro
    }

    private org.springframework.test.web.servlet.ResultActions mvcCadastro(String email) throws Exception {
        return mvc.perform(json(post("/auth/cadastro"), """
                { "nome": "Cliente Teste", "email": "%s", "whatsapp": "(11) 90000-0001", "senha": "%s", "cep": "01001-000",
                  "autorizaRegiao": true, "versaoTextoRegiao": "1.2" }
                """.formatted(email, ContaDeTeste.SENHA)));
    }
}
