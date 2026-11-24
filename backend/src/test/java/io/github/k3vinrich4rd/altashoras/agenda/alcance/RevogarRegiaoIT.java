package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** A4 e A5 da Fatia 11A: retirar a autorização apaga a região do perfil e de TODAS as reservas da conta, e a pessoa sai do bairro. */
class RevogarRegiaoIT extends AlcanceBase {

    /** Seis contas em Osasco/Centro (uma delas é a que vai revogar) e três reservas da primeira. */
    private record Cenario(UUID quemRevoga, Catalogo c) {
    }

    private Cenario cenario() {
        Catalogo c = catalogo();
        UUID quemRevoga = clienteComRegiao(CENTRO);
        for (int i = 0; i < 3; i++) {
            atendimentoDaConta(quemRevoga, c.barbeiro(), c.servico(), DIA_DO_ATENDIMENTO, "CONCLUIDO");
        }
        for (int i = 0; i < 5; i++) {
            atendimentoDaConta(clienteComRegiao(CENTRO), c.barbeiro(), c.servico(), DIA_DO_ATENDIMENTO, "CONCLUIDO");
        }
        return new Cenario(quemRevoga, c);
    }

    private void conferirSemRegiao(Cenario cenario) throws Exception {
        assertThat(jdbc.queryForMap("SELECT bairro, cidade, uf, regiao_municipio_ibge FROM usuario WHERE id = ?",
                cenario.quemRevoga()).values()).containsOnlyNulls();
        assertThat(jdbc.queryForList("""
                SELECT regiao_bairro, regiao_cidade, regiao_uf, regiao_municipio_ibge, regiao_consentimento_versao
                FROM reserva WHERE cliente_id = ?""", cenario.quemRevoga())).hasSize(3)
                .allSatisfy(linha -> assertThat(linha.values()).containsOnlyNulls());
        // a reserva em si não mudou
        assertThat(contar("SELECT count(*) FROM reserva WHERE cliente_id = ? AND status = 'CONCLUIDO'", cenario.quemRevoga()))
                .isEqualTo(3);
        // o alcance seguinte não conta a pessoa em bairro nenhum: Osasco cai de 6 para 5 e ela vira "sem região"
        alcanceDoAdmin("?nivel=MUNICIPIO&periodo=ULTIMOS_90_DIAS").andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].nome").value("Osasco")).andExpect(jsonPath("$.grupos[0].clientes").value(5))
                .andExpect(jsonPath("$.semRegiao.abaixoDoLimite").value(true)).andExpect(jsonPath("$.semRegiao.clientes").doesNotExist());
        comoCliente(MockMvcRequestBuilders.get("/me"), cenario.quemRevoga(), null).andExpect(jsonPath("$.regiaoAutorizada").value(false))
                .andExpect(jsonPath("$.endereco").doesNotExist());
    }

    @Test
    @DisplayName("A4: conta com região e 3 reservas, PUT /me/consentimentos/regiao com aceito = false: 200; a localidade do perfil e a região das 3 reservas ficam NULL; o alcance seguinte não conta a pessoa em bairro nenhum; a revogação fica no histórico")
    void revogarPelaTelaDeConsentimentos() throws Exception {
        Cenario cenario = cenario();
        alcanceDoAdmin("?nivel=MUNICIPIO&periodo=ULTIMOS_90_DIAS").andExpect(jsonPath("$.grupos[0].clientes").value(6));

        comoCliente(put("/me/consentimentos/regiao"), cenario.quemRevoga(), "{ \"aceito\": false, \"versaoTexto\": \"1.2\" }")
                .andExpect(status().isOk()).andExpect(jsonPath("$.finalidade").value("REGIAO_ALCANCE"))
                .andExpect(jsonPath("$.aceito").value(false));

        conferirSemRegiao(cenario);
        assertThat(jdbc.queryForList("""
                SELECT aceito FROM consentimento WHERE usuario_id = ? AND finalidade = 'REGIAO_ALCANCE' ORDER BY registrado_em, id
                """, Boolean.class, cenario.quemRevoga())).containsExactly(true, false);
    }

    @Test
    @DisplayName("A5: conta com região, PUT /me sem CEP (ou com autorizaRegiao = false): mesmo efeito do A4")
    void revogarPeloPerfil() throws Exception {
        Cenario cenario = cenario();

        comoCliente(put("/me"), cenario.quemRevoga(), "{ \"autorizaRegiao\": false }").andExpect(status().isOk())
                .andExpect(jsonPath("$.regiaoAutorizada").value(false));

        conferirSemRegiao(cenario);
        // quem aceita de novo precisa informar o CEP outra vez: a região não volta sozinha
        comoCliente(put("/me"), cenario.quemRevoga(), "{ \"cep\": \"06010-000\", \"autorizaRegiao\": true }")
                .andExpect(status().isBadRequest());
    }
}
