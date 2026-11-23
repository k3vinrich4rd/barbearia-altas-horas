package io.github.k3vinrich4rd.altashoras.conta.convite;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.SENHA;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * A31 da Fatia 10B: emitir, aceitar, revogar, errar e desligar não deixam código, e-mail, nome nem senha no log, na auditoria
 * nem nos corpos de erro (R30, E13).
 */
@ExtendWith(OutputCaptureExtension.class)
class SemDadoPessoalNoLogConviteIT extends ConviteBase {

    @Test
    @DisplayName("A31: o fluxo inteiro do convite (emitir, aceitar, errar, revogar, desligar) não deixa código, e-mail, senha nem token no log, na auditoria nem nos erros")
    void nadaNoLog(CapturedOutput saida) throws Exception {
        Conta dono = dono();
        UUID barbeiro = barbeiroSemConta();
        String corpoDoConvite = emitir(dono, barbeiro).andReturn().getResponse().getContentAsString();
        String codigo = JsonPath.read(corpoDoConvite, "$.codigo");
        String email = "zeferino.ficticio@exemplo.test";
        String senhaRuim = "SenhaCurta";
        String erro1 = aceitar(codigo, email, senhaRuim.substring(0, 5)).andReturn().getResponse().getContentAsString();
        String erro2 = aceitar(CodigoDeConvite.gerar(), email, SENHA).andReturn().getResponse().getContentAsString();
        aceitar(codigo, email, SENHA);
        String erro3 = aceitar(codigo, email, SENHA).andReturn().getResponse().getContentAsString();
        UUID outro = barbeiroSemConta();
        String codigo2 = gerarCodigo(dono, outro);
        revogar(dono, outro);
        String erro4 = aceitar(codigo2, "outro.ficticio@exemplo.test", SENHA).andReturn().getResponse().getContentAsString();
        desligar(dono, barbeiro);

        String auditoria = jdbc.queryForList("SELECT detalhe::text FROM auditoria_admin", String.class).toString();
        for (String proibido : new String[] {codigo, codigo2, email, "outro.ficticio", SENHA, senhaRuim, dono.token()}) {
            assertThat(saida.getAll()).doesNotContain(proibido);
            assertThat(auditoria).doesNotContain(proibido);
        }
        for (String erro : new String[] {erro1, erro2, erro3, erro4}) {
            assertThat(erro).doesNotContain(codigo).doesNotContain(email).doesNotContain(SENHA);
        }
        assertThat(saida.getAll()).doesNotContain("eyJ");
    }
}
