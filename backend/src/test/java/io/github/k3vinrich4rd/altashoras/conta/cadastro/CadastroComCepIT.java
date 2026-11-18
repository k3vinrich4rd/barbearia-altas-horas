package io.github.k3vinrich4rd.altashoras.conta.cadastro;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.SENHA;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.emailNovo;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.json;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_INEXISTENTE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_ERRO;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import io.github.k3vinrich4rd.altashoras.cep.CepTeste;
import io.github.k3vinrich4rd.altashoras.cep.CepTesteBase;

/** A15, A17, A18 e A19: o CEP no cadastro (E6, R31). O cadastro usa a mesma porta e o mesmo cache da rota pública. */
@CepTeste
class CadastroComCepIT extends CepTesteBase {

    private ResultActions cadastrar(String email, String cep) throws Exception {
        String campoCep = cep == null ? "" : ", \"cep\": \"" + cep + "\"";
        return mvc.perform(json(post("/auth/cadastro"), """
                { "nome": "Cliente Teste", "email": "%s", "whatsapp": "(11) 90000-0001", "senha": "%s"%s }
                """.formatted(email, SENHA, campoCep)));
    }

    private Map<String, Object> endereco(String email) {
        return jdbc.queryForMap("SELECT cep, bairro, cidade, uf FROM usuario WHERE email = ?", email);
    }

    @Test
    @DisplayName("A15: cadastro com cep = 01001000 e ViaCEP respondendo: 201, o banco guarda cep, bairro Sé, cidade São Paulo e uf SP, e o perfil traz completo=true")
    void cadastroComCepResolvido() throws Exception {
        provedores.viaCep(CEP_SE, json(VIACEP_SE));
        String email = emailNovo();

        cadastrar(email, CEP_SE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.endereco.cep").value(CEP_SE))
                .andExpect(jsonPath("$.endereco.bairro").value("Sé"))
                .andExpect(jsonPath("$.endereco.cidade").value("São Paulo"))
                .andExpect(jsonPath("$.endereco.uf").value("SP"))
                .andExpect(jsonPath("$.endereco.completo").value(true));

        assertThat(endereco(email)).containsEntry("cep", CEP_SE).containsEntry("bairro", "Sé")
                .containsEntry("cidade", "São Paulo").containsEntry("uf", "SP");
    }

    @Test
    @DisplayName("A17: cadastro com cep = 99999999 (inexistente): 201, só o cep no banco e completo=false, igual ao A16")
    void cadastroComCepInexistente() throws Exception {
        provedores.viaCep(CEP_INEXISTENTE, json(VIACEP_ERRO));
        String email = emailNovo();

        cadastrar(email, CEP_INEXISTENTE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.endereco.cep").value(CEP_INEXISTENTE))
                .andExpect(jsonPath("$.endereco.completo").value(false));

        Map<String, Object> linha = endereco(email);
        assertThat(linha).containsEntry("cep", CEP_INEXISTENTE);
        assertThat(linha.get("bairro")).isNull();
        assertThat(linha.get("cidade")).isNull();
        assertThat(linha.get("uf")).isNull();
    }

    @Test
    @DisplayName("A18: cadastro sem cep: 201 e zero chamadas aos provedores")
    void cadastroSemCep() throws Exception {
        // Nenhuma expectativa: qualquer chamada externa lançaria AssertionError e o cadastro não daria 201.
        String email = emailNovo();

        cadastrar(email, null).andExpect(status().isCreated()).andExpect(jsonPath("$.endereco").doesNotExist());

        Map<String, Object> linha = endereco(email);
        assertThat(linha.get("cep")).isNull();
        assertThat(linha.get("bairro")).isNull();
        provedores.verificar();
    }

    @Test
    @DisplayName("A19: dois cadastros com o mesmo CEP: uma única chamada ao ViaCEP (o segundo usa o cache) e as duas contas com o endereço completo")
    void doisCadastrosUsamOMesmoCache() throws Exception {
        provedores.viaCep(CEP_SE, json(VIACEP_SE)); // uma só: a segunda chamada falharia na hora
        String primeiro = emailNovo();
        String segundo = emailNovo();

        cadastrar(primeiro, CEP_SE).andExpect(status().isCreated()).andExpect(jsonPath("$.endereco.completo").value(true));
        cadastrar(segundo, "01001-000").andExpect(status().isCreated()).andExpect(jsonPath("$.endereco.completo").value(true));

        assertThat(endereco(primeiro)).containsEntry("bairro", "Sé").containsEntry("cidade", "São Paulo");
        assertThat(endereco(segundo)).containsEntry("bairro", "Sé").containsEntry("cidade", "São Paulo");
        provedores.verificar();
    }
}
