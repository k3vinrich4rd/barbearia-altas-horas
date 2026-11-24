package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_OSASCO;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.erro503;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.json;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.viaCepSemIbge;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;

/** A26 da Fatia 11B: o município do IBGE vem do provedor, do fallback ou da tabela embutida, e nunca derruba o cadastro nem a reserva (R31). */
class CepIbgeIT extends AlcanceBase {

    private Map<String, Object> regiaoDaConta(String email) {
        return jdbc.queryForMap("SELECT bairro, cidade, uf, regiao_municipio_ibge FROM usuario WHERE email = ?", email);
    }

    private String cadastrarAutorizando(String cep) throws Exception {
        String email = ContaDeTeste.emailNovo();
        cadastrar(email, ", \"cep\": \"" + cep + "\", \"autorizaRegiao\": true, \"versaoTextoRegiao\": \"1.2\"")
                .andExpect(status().isCreated());
        return email;
    }

    @Test
    @DisplayName("A26: ViaCEP com ibge: o município sai do código do provedor (3534401 = Osasco), sem consultar a tabela por nome")
    void viaCepComCodigo() throws Exception {
        provedores.viaCep("06010000", json(VIACEP_OSASCO));
        assertThat(regiaoDaConta(cadastrarAutorizando("06010000"))).containsEntry("regiao_municipio_ibge", "3534401")
                .containsEntry("bairro", "Centro");
    }

    @Test
    @DisplayName("A26: só a BrasilAPI v2 responde, com ibge.city: o município sai do código dela")
    void soABrasilApiV2() throws Exception {
        provedores.viaCep("06020000", erro503());
        provedores.brasilApi("06020000", json("""
                {"cep":"06020000","state":"SP","city":"Osasco","neighborhood":"Vila Yara","street":"Rua Fictícia",
                 "service":"viacep","ibge":{"city":"3534401","state":"35"}}"""));
        assertThat(regiaoDaConta(cadastrarAutorizando("06020000"))).containsEntry("regiao_municipio_ibge", "3534401")
                .containsEntry("bairro", "Vila Yara").containsEntry("uf", "SP");
    }

    @Test
    @DisplayName("A26: provedor sem código mas com município único na tabela: resolvido pelo nome (sem acento) e UF")
    void resolvidoPeloNomeEUf() throws Exception {
        provedores.viaCep("06030000", json(viaCepSemIbge("06030000", "Centro", "Carapicuiba", "SP")));
        assertThat(regiaoDaConta(cadastrarAutorizando("06030000"))).containsEntry("regiao_municipio_ibge", "3510609")
                .containsEntry("cidade", "Carapicuiba");
    }

    @Test
    @DisplayName("A26: cidade que a tabela não acha: sem município, só a UF (o cadastro dá 201 e a conta fica com cidade e UF, sem código)")
    void semMunicipioSoAUf() throws Exception {
        provedores.viaCep("06040000", json(viaCepSemIbge("06040000", "Centro", "Cidade Que Nao Existe", "SP")));
        Map<String, Object> regiao = regiaoDaConta(cadastrarAutorizando("06040000"));
        assertThat(regiao.get("regiao_municipio_ibge")).isNull();
        assertThat(regiao).containsEntry("uf", "SP").containsEntry("cidade", "Cidade Que Nao Existe");
    }

    @Test
    @DisplayName("A26: provedores fora: o cadastro e a reserva do visitante dão 201 e ficam sem região (R31), e nenhuma exceção sobe")
    void provedoresForaNuncaDerrubam() throws Exception {
        provedores.osDoisFora("06050000");
        assertThat(regiaoDaConta(cadastrarAutorizando("06050000")).values()).containsOnlyNulls();
        provedores.recomecar();
        provedores.osDoisFora("06050000");
        Catalogo c = catalogo();
        reservarAutorizando(c, "10:00", "06050-000").andExpect(status().isCreated());
        assertThat(jdbc.queryForMap("SELECT regiao_cidade, regiao_municipio_ibge, regiao_consentimento_versao FROM reserva WHERE barbeiro_id = ?",
                c.barbeiro()).values()).containsOnlyNulls();
    }
}
