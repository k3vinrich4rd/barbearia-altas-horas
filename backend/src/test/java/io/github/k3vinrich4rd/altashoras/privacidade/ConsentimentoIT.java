package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** A23 a A31: o titular vê o estado atual do consentimento, aceita e revoga; cada mudança é uma linha nova (R28, E11, E12). */
class ConsentimentoIT extends PrivacidadeBase {

    private static final String ROTA = "/me/consentimentos/marketing";

    @Test
    @DisplayName("A23: cliente cadastrado sem aceitaMarketing: GET /me/consentimentos devolve 1 item, MARKETING_CEP, aceito=false, versaoTexto=1.0 e a data do cadastro")
    void semAceiteNoCadastro() throws Exception {
        relogio.reiniciar(); // 17/11 08:00: o dia do cadastro
        String email = cadastrar(false);
        irPara(TERCA, 9, 30);
        Cliente cliente = entrar(email);

        get("/me/consentimentos", cliente.acesso()).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].finalidade").value("MARKETING_CEP"))
                .andExpect(jsonPath("$[0].aceito").value(false))
                .andExpect(jsonPath("$[0].versaoTexto").value("1.0"))
                .andExpect(jsonPath("$[0].registradoEm").value("2026-11-17T08:00:00-03:00"));
    }

    @Test
    @DisplayName("A24: cliente cadastrado com aceitaMarketing=true: GET /me/consentimentos devolve aceito=true")
    void comAceiteNoCadastro() throws Exception {
        Cliente cliente = novoCliente(true);

        get("/me/consentimentos", cliente.acesso()).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].aceito").value(true));
    }

    @Test
    @DisplayName("A25: aceitar pelo PUT dá 200 com aceito=true e registradoEm de 01/12 09:30; o banco passa a ter 2 linhas (a antiga fica)")
    void aceitar() throws Exception {
        relogio.reiniciar();
        String email = cadastrar(false);
        irPara(TERCA, 9, 30);
        Cliente cliente = entrar(email);

        put(ROTA, cliente.acesso(), consentimento(true, "1.0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.finalidade").value("MARKETING_CEP"))
                .andExpect(jsonPath("$.aceito").value(true))
                .andExpect(jsonPath("$.versaoTexto").value("1.0"))
                .andExpect(jsonPath("$.registradoEm").value("2026-12-01T09:30:00-03:00"));

        assertThat(consentimentosNoBanco(cliente.id())).hasSize(2);
        assertThat(consentimentosNoBanco(cliente.id()).get(0)).containsEntry("aceito", false);
        assertThat(instante(consentimentosNoBanco(cliente.id()).get(1).get("registrado_em"))).isEqualTo(relogio.instant());
    }

    @Test
    @DisplayName("A26: aceitar e depois revogar: 3 linhas no banco, e GET /me/consentimentos devolve 1 item, o mais recente (aceito=false)")
    void revogar() throws Exception {
        relogio.reiniciar();
        String email = cadastrar(false);
        irPara(TERCA, 9, 30);
        Cliente cliente = entrar(email);
        put(ROTA, cliente.acesso(), consentimento(true, "1.0")).andExpect(status().isOk());
        relogio.avancar(Duration.ofMinutes(1)); // a mudança seguinte tem de ser depois: "a mais recente" é pela data

        put(ROTA, cliente.acesso(), consentimento(false, "1.0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.aceito").value(false));

        assertThat(linhasDeConsentimento(cliente.id())).isEqualTo(3);
        get("/me/consentimentos", cliente.acesso()).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].aceito").value(false))
                .andExpect(jsonPath("$[0].registradoEm").value("2026-12-01T09:31:00-03:00"));
    }

    @Test
    @DisplayName("A27: PUT com o estado que já vale na mesma versão devolve 200 com a linha existente e não grava nenhuma linha nova")
    void estadoIgualNaoGravaOutraLinha() throws Exception {
        relogio.reiniciar();
        String email = cadastrar(true); // aceito=true na versão 1.0, em 17/11
        irPara(TERCA, 9, 30);
        Cliente cliente = entrar(email);

        put(ROTA, cliente.acesso(), consentimento(true, "1.0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.aceito").value(true))
                .andExpect(jsonPath("$.registradoEm").value("2026-11-17T08:00:00-03:00"));
        put(ROTA, cliente.acesso(), consentimento(true, "1.0")).andExpect(status().isOk());

        assertThat(linhasDeConsentimento(cliente.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("A28: versaoTexto 0.9, sem aceito, com campo extra e com versaoTexto de 21 caracteres dão 400 (com errors[].field, CAMPO_DESCONHECIDO no extra) e nada é gravado")
    void corposInvalidos() throws Exception {
        Cliente cliente = novoCliente(false);
        long antes = linhasDeConsentimento(cliente.id());

        put(ROTA, cliente.acesso(), consentimento(true, "0.9")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors[0].field").value("versaoTexto"));
        put(ROTA, cliente.acesso(), "{ \"versaoTexto\": \"1.0\" }").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors[0].field").value("aceito"));
        put(ROTA, cliente.acesso(), "{ \"aceito\": true, \"versaoTexto\": \"1.0\", \"finalidade\": \"MARKETING_CEP\" }")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));
        put(ROTA, cliente.acesso(), consentimento(true, "1".repeat(21))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors[0].field").value("versaoTexto"));

        assertThat(linhasDeConsentimento(cliente.id())).isEqualTo(antes);
    }

    @Test
    @DisplayName("A29: os consentimentos de B não mudam quando A aceita e revoga (nenhuma rota recebe id de usuário); sem token dá 401")
    void umNaoMexeNoOutro() throws Exception {
        Cliente a = novoCliente(false);
        Cliente b = novoCliente(true);
        var antesDeB = consentimentosNoBanco(b.id());

        put(ROTA, a.acesso(), consentimento(true, "1.0")).andExpect(status().isOk());
        relogio.avancar(Duration.ofMinutes(1));
        put(ROTA, a.acesso(), consentimento(false, "1.0")).andExpect(status().isOk());

        assertThat(consentimentosNoBanco(b.id())).isEqualTo(antesDeB);
        get("/me/consentimentos", b.acesso()).andExpect(status().isOk()).andExpect(jsonPath("$[0].aceito").value(true));
        mvc.perform(MockMvcRequestBuilders.get("/me/consentimentos")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.put(ROTA).contentType(MediaType.APPLICATION_JSON).content(consentimento(true, "1.0")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A30: revogar o consentimento não apaga CEP, bairro nem cidade: GET /me continua com o endereço")
    void revogarNaoApagaOEndereco() throws Exception {
        Cliente cliente = novoCliente(true);
        jdbc.update("UPDATE usuario SET cep = '01001000', bairro = 'Sé', cidade = 'São Paulo', uf = 'SP' WHERE id = ?",
                cliente.id());

        put(ROTA, cliente.acesso(), consentimento(false, "1.0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.aceito").value(false));

        get("/me", cliente.acesso()).andExpect(status().isOk())
                .andExpect(jsonPath("$.endereco.cep").value("01001000"))
                .andExpect(jsonPath("$.endereco.bairro").value("Sé"))
                .andExpect(jsonPath("$.endereco.cidade").value("São Paulo"));
        assertThat(jdbc.queryForMap("SELECT cep, bairro, cidade FROM usuario WHERE id = ?", cliente.id()))
                .containsEntry("bairro", "Sé").containsEntry("cidade", "São Paulo");
    }

    @Test
    @DisplayName("A31: o ADMIN sem nenhum consentimento no banco recebe 200 com lista vazia")
    void adminSemConsentimento() throws Exception {
        Conta admin = conta("ADMIN");
        assertThat(linhasDeConsentimento(admin.id())).isZero();

        get("/me/consentimentos", admin).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
