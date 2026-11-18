package io.github.k3vinrich4rd.altashoras.cep;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** A20: o CEP localiza a casa de alguém, então nunca aparece no log (R30, E8), nem na falha de provedor. */
@CepTeste
@ExtendWith(OutputCaptureExtension.class)
class SemDadoPessoalNoLogCepIT extends CepTesteBase {

    @Test
    @DisplayName("A20: consultas com sucesso, 404, falha de provedor e cadastros com CEP não deixam nenhum CEP (com ou sem hífen) no log")
    void nenhumCepNoLog(CapturedOutput saida) throws Exception {
        // Sucesso (e depois o mesmo CEP do cache)
        provedores.viaCep(CEP_SE, json(VIACEP_SE));
        consultar(CEP_SE).andExpect(status().isOk());
        consultar("01001-000").andExpect(status().isOk());
        provedores.recomecar();
        // 404
        provedores.viaCep(CEP_INEXISTENTE, json(VIACEP_ERRO));
        consultar(CEP_INEXISTENTE).andExpect(status().isNotFound());
        consultar("99999-999").andExpect(status().isNotFound());
        provedores.recomecar();
        // Falha de provedor: timeout no ViaCEP e BrasilAPI com 503, que é o que mais provoca log de exceção
        provedores.osDoisFora("02002000");
        consultar("02002000").andExpect(status().isServiceUnavailable());
        provedores.recomecar();
        provedores.viaCep("03003000", ProvedoresFalsos.quedaDeRede());
        provedores.brasilApi("03003000", ProvedoresFalsos.naoEJson());
        consultar("03003000").andExpect(status().isServiceUnavailable());
        provedores.recomecar();
        // Formato inválido
        consultar("01001%2F000").andExpect(status().isBadRequest());
        // Cadastros: resolvido (cache), inexistente (cache) e provedores fora
        mvc.perform(json(post("/auth/cadastro"), cadastro(CEP_SE))).andExpect(status().isCreated());
        mvc.perform(json(post("/auth/cadastro"), cadastro("99999-999"))).andExpect(status().isCreated());
        provedores.recomecar();
        provedores.osDoisFora("04004000");
        mvc.perform(json(post("/auth/cadastro"), cadastro("04004000"))).andExpect(status().isCreated());

        assertThat(saida.getAll())
                .doesNotContain("01001000").doesNotContain("01001-000").doesNotContain("01001%2F000")
                .doesNotContain("99999999").doesNotContain("99999-999")
                .doesNotContain("02002000").doesNotContain("02002-000")
                .doesNotContain("03003000").doesNotContain("03003-000")
                .doesNotContain("04004000").doesNotContain("04004-000")
                .contains("cep resolvido: sim"); // o log existe e diz só "sim/não" e a fonte
    }

    private static String cadastro(String cep) {
        return """
                { "nome": "Cliente Teste", "email": "%s", "whatsapp": "(11) 90000-0001", "senha": "%s", "cep": "%s" }
                """.formatted(emailNovo(), SENHA, cep);
    }
}
