package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_OSASCO;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;

/**
 * A1, A2 e A3 da Fatia 11A: a região só existe com a caixinha marcada, e o CEP nunca é guardado. Cobre os três caminhos: o
 * cadastro, o {@code PUT /me} e a reserva do visitante. Nenhuma chamada sai para a internet (provedores falsos).
 */
@ExtendWith(OutputCaptureExtension.class)
class RegiaoConsentidaIT extends AlcanceBase {

    private Map<String, Object> localidade(String email) {
        return jdbc.queryForMap("SELECT bairro, cidade, uf, regiao_municipio_ibge FROM usuario WHERE email = ?", email);
    }

    @Test
    @DisplayName("A1: cadastro, PUT /me e reserva de visitante com cep e SEM autorizaRegiao: 201 ou 200 como antes, nenhuma região gravada, zero chamadas à ViaCEP e à BrasilAPI")
    void semCaixinhaOCepEIgnorado() throws Exception {
        // Nenhuma expectativa nos provedores falsos: qualquer chamada falharia o teste (provedores.verificar() no fim).
        String email = ContaDeTeste.emailNovo();
        String corpo = cadastrar(email, ", \"cep\": \"06010-000\"").andExpect(status().isCreated())
                .andExpect(jsonPath("$.regiaoAutorizada").value(false)).andExpect(jsonPath("$.endereco").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID cliente = UUID.fromString(JsonPath.read(corpo, "$.id"));

        comoCliente(put("/me"), cliente, "{ \"cep\": \"06010-000\" }").andExpect(status().isOk())
                .andExpect(jsonPath("$.regiaoAutorizada").value(false));
        comoCliente(put("/me"), cliente, "{ \"cep\": \"06010-000\", \"autorizaRegiao\": false }").andExpect(status().isOk());

        Catalogo c = catalogo();
        reservarComoVisitante(c, "10:00", "06010-000").andExpect(status().isCreated())
                .andExpect(jsonPath("$.regiaoAutorizada").value(false));

        assertThat(localidade(email).values()).containsOnlyNulls();
        assertThat(jdbc.queryForMap("""
                SELECT regiao_bairro, regiao_cidade, regiao_uf, regiao_municipio_ibge, regiao_consentimento_versao
                FROM reserva WHERE barbeiro_id = ?""", c.barbeiro()).values()).containsOnlyNulls();
        assertThat(contar("SELECT count(*) FROM consentimento WHERE usuario_id = ? AND finalidade = 'REGIAO_ALCANCE' AND aceito",
                cliente)).isZero();
        provedores.verificar();
    }

    @Test
    @DisplayName("A2: autorizaRegiao = true, versão vigente e CEP de Osasco/Centro no cadastro, no PUT /me e na reserva de visitante: grava bairro, município, UF, código IBGE 3534401 e a versão; a conta ganha linha REGIAO_ALCANCE aceita; nada de CEP no log")
    void comCaixinhaGravaARegiaoSemOCep(CapturedOutput saida) throws Exception {
        provedores.viaCep(CEP_OSASCO, json(VIACEP_OSASCO)); // uma só: as outras duas leituras saem do cache do CEP

        String email = ContaDeTeste.emailNovo();
        String corpo = cadastrar(email, REGIAO_OSASCO).andExpect(status().isCreated())
                .andExpect(jsonPath("$.regiaoAutorizada").value(true)).andExpect(jsonPath("$.endereco.bairro").value("Centro"))
                .andExpect(jsonPath("$.endereco.cidade").value("Osasco")).andExpect(jsonPath("$.endereco.completo").value(true))
                .andExpect(jsonPath("$.endereco.cep").doesNotExist()).andReturn().getResponse().getContentAsString();
        UUID cliente = UUID.fromString(JsonPath.read(corpo, "$.id"));
        assertThat(localidade(email)).containsEntry("bairro", "Centro").containsEntry("cidade", "Osasco")
                .containsEntry("uf", "SP").containsEntry("regiao_municipio_ibge", "3534401");
        assertThat(jdbc.queryForMap("""
                SELECT versao_texto, aceito FROM consentimento WHERE usuario_id = ? AND finalidade = 'REGIAO_ALCANCE'
                """, cliente)).containsEntry("versao_texto", "1.2").containsEntry("aceito", true);

        // PUT /me: outra conta que só agora autoriza
        String outro = ContaDeTeste.emailNovo();
        UUID segundo = UUID.fromString(JsonPath.read(cadastrar(outro, "").andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.id"));
        comoCliente(put("/me"), segundo, "{ \"cep\": \"06010000\", \"autorizaRegiao\": true, \"versaoTextoRegiao\": \"1.2\" }")
                .andExpect(status().isOk()).andExpect(jsonPath("$.regiaoAutorizada").value(true))
                .andExpect(jsonPath("$.endereco.bairro").value("Centro"));
        assertThat(localidade(outro)).containsEntry("regiao_municipio_ibge", "3534401").containsEntry("uf", "SP");

        // Visitante: a reserva guarda a região, o município e a versão do texto aceito
        Catalogo c = catalogo();
        reservarAutorizando(c, "10:00", "06010-000").andExpect(status().isCreated())
                .andExpect(jsonPath("$.regiaoAutorizada").value(true)).andExpect(jsonPath("$.cep").doesNotExist());
        assertThat(jdbc.queryForMap("""
                SELECT regiao_bairro, regiao_cidade, regiao_uf, regiao_municipio_ibge, regiao_consentimento_versao
                FROM reserva WHERE barbeiro_id = ?""", c.barbeiro())).containsEntry("regiao_bairro", "Centro")
                .containsEntry("regiao_cidade", "Osasco").containsEntry("regiao_uf", "SP")
                .containsEntry("regiao_municipio_ibge", "3534401").containsEntry("regiao_consentimento_versao", "1.2");

        assertThat(saida.getAll()).doesNotContain("06010000").doesNotContain("06010-000").doesNotContain(email)
                .doesNotContain("Centro").doesNotContain("3534401");
        provedores.verificar();
    }

    @Test
    @DisplayName("A3: versaoTextoRegiao velha, ou faltando com autorizaRegiao = true: 400 VALIDACAO_INVALIDA em versaoTextoRegiao, e nada é gravado (nem conta, nem reserva, nem consentimento)")
    void versaoDoTextoPrecisaSerAVigente() throws Exception {
        String email = ContaDeTeste.emailNovo();
        for (String campos : new String[] {
                ", \"cep\": \"06010-000\", \"autorizaRegiao\": true, \"versaoTextoRegiao\": \"1.1\"",
                ", \"cep\": \"06010-000\", \"autorizaRegiao\": true"}) {
            cadastrar(email, campos).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                    .andExpect(jsonPath("$.errors[0].field").value("versaoTextoRegiao"));
        }
        assertThat(contar("SELECT count(*) FROM usuario WHERE email = ?", email)).isZero();

        UUID cliente = UUID.fromString(JsonPath.read(cadastrar(ContaDeTeste.emailNovo(), "").andReturn().getResponse()
                .getContentAsString(), "$.id"));
        comoCliente(put("/me"), cliente, "{ \"cep\": \"06010-000\", \"autorizaRegiao\": true, \"versaoTextoRegiao\": \"1.0\" }")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("versaoTextoRegiao"));
        assertThat(contar("SELECT count(*) FROM consentimento WHERE usuario_id = ? AND finalidade = 'REGIAO_ALCANCE'", cliente))
                .isZero();

        Catalogo c = catalogo();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/agendamentos/guest")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(corpoGuest(c, "10:00", "06010-000", true).replace("\"versaoTextoRegiao\": \"1.2\"",
                        "\"versaoTextoRegiao\": \"0.9\""))
                .with(io.github.k3vinrich4rd.altashoras.ContaDeTeste.ip(ContaDeTeste.ipNovo())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("versaoTextoRegiao"));
        assertThat(contar("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", c.barbeiro())).isZero();
        provedores.verificar();
    }

    @Test
    @DisplayName("A2: a reserva de uma conta copia a região e a versão do texto aceito; conta com localidade mas SEM consentimento de região (ou que revogou) reserva sem região")
    void reservaDeContaCopiaSoComConsentimento() throws Exception {
        Catalogo c = catalogo();
        UUID comConsentimento = clienteComRegiao(CENTRO);
        reservarComoCliente(c, "10:00", comConsentimento).andExpect(status().isCreated());
        assertThat(jdbc.queryForMap("""
                SELECT regiao_bairro, regiao_municipio_ibge, regiao_consentimento_versao FROM reserva WHERE cliente_id = ?""",
                comConsentimento)).containsEntry("regiao_bairro", "Centro").containsEntry("regiao_municipio_ibge", "3534401")
                .containsEntry("regiao_consentimento_versao", "1.2");

        UUID semConsentimento = clienteComRegiao(CENTRO);
        jdbc.update("DELETE FROM consentimento WHERE usuario_id = ?", semConsentimento); // a localidade ficou, a prova não
        reservarComoCliente(c, "11:00", semConsentimento).andExpect(status().isCreated());
        assertThat(jdbc.queryForMap("""
                SELECT regiao_bairro, regiao_cidade, regiao_municipio_ibge, regiao_consentimento_versao FROM reserva WHERE cliente_id = ?""",
                semConsentimento).values()).containsOnlyNulls();
    }
}
