package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;

/** A36 a A39 da Fatia 11D: o k vale em cada recorte; o barbeiro convidado vê só os clientes dele (ADR 0013, E15). */
class AlcancePorBarbeiroIT extends AlcanceBase {

    private record Equipe(Conta contaA, UUID barbeiroA, Conta contaB, UUID barbeiroB, UUID servico) {
    }

    private Equipe equipe() {
        Conta contaA = conta("BARBEIRO");
        Conta contaB = conta("BARBEIRO");
        return new Equipe(contaA, barbeiroDaConta(contaA.id()), contaB, barbeiroDaConta(contaB.id()),
                io.github.k3vinrich4rd.altashoras.DadosDeTeste.servico(jdbc, 30));
    }

    private String doBarbeiro(Conta conta, String consulta) throws Exception {
        return alcanceDoBarbeiro(conta, consulta).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("A36: ADMIN, barbeiros A (10 clientes em Osasco) e B (3 em Osasco e 3 em Carapicuíba), com ?barbeiroId=B: o k vale no recorte de B: Osasco com 3 sobe para a região (agrupadoEm = REGIAO), nunca aparece o 3")
    void kVaiNoRecorteDoBarbeiro() throws Exception {
        Equipe e = equipe();
        pessoas(10, e.barbeiroA(), e.servico(), CENTRO);
        pessoas(3, e.barbeiroB(), e.servico(), CENTRO);
        pessoas(3, e.barbeiroB(), e.servico(), CARAPICUIBA_CENTRO);

        // (em "todos", Osasco 13 e Carapicuíba 3: a sobra de 3 faria o Osasco ser rebaixado, E9; aqui interessa o recorte de B)
        String deB = painel("?nivel=MUNICIPIO&barbeiroId=" + e.barbeiroB());
        assertThat(this.<List<Object>>ler(deB, "$.grupos[?(@.tipo == 'MUNICIPIO')]")).isEmpty();
        assertThat(this.<String>ler(deB, "$.grupos[0].nome")).isEqualTo("Outros municípios da região imediata de São Paulo");
        assertThat(this.<Integer>ler(deB, "$.grupos[0].clientes")).isEqualTo(6);
        assertThat(this.<String>ler(deB, "$.grupos[0].agrupadoEm")).isEqualTo("REGIAO");
        assertThat(deB).doesNotContain("\"clientes\":3");

        alcanceDoAdmin("?barbeiroId=" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A37: BARBEIRO A pede GET /admin/alcance e ?barbeiroId=<B>: só pessoas que reservaram com A; o filtro é ignorado e nada do corpo denuncia isso; B nunca aparece")
    void barbeiroVeSoOsClientesDele() throws Exception {
        Equipe e = equipe();
        pessoas(10, e.barbeiroA(), e.servico(), CENTRO);
        pessoas(8, e.barbeiroB(), e.servico(), COPACABANA);

        String proprio = doBarbeiro(e.contaA(), "?nivel=ESTADO");
        String comFiltroDoOutro = doBarbeiro(e.contaA(), "?nivel=ESTADO&barbeiroId=" + e.barbeiroB());
        String comOProprio = doBarbeiro(e.contaA(), "?nivel=ESTADO&barbeiroId=" + e.barbeiroA());

        assertThat(proprio).isEqualTo(comFiltroDoOutro).isEqualTo(comOProprio);
        assertThat(this.<List<Object>>ler(proprio, "$.grupos[*].codigo")).containsExactly("SP");
        assertThat(this.<Integer>ler(proprio, "$.clientes")).isEqualTo(10);
        assertThat(proprio).doesNotContain("RJ").doesNotContain("Copacabana").doesNotContain(e.barbeiroB().toString());
    }

    @Test
    @DisplayName("A38: BARBEIRO A com 6 clientes em 6 bairros diferentes: nenhum bairro nomeado; sobe para o município (6)")
    void poucosClientesNuncaMostramBairroPequeno() throws Exception {
        Equipe e = equipe();
        for (String bairro : new String[] {"Centro", "Vila A", "Vila B", "Vila C", "Vila D", "Vila E"}) {
            pessoas(1, e.barbeiroA(), e.servico(), new Onde(bairro, OSASCO, "Osasco", "SP"));
        }
        String corpo = doBarbeiro(e.contaA(), "?nivel=MUNICIPIO");
        assertThat(this.<String>ler(corpo, "$.grupos[0].nome")).isEqualTo("Osasco");
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].clientes")).isEqualTo(6);
        assertThat(this.<List<Object>>ler(corpo, "$.grupos[0].filhos[*].nome")).containsExactly("Outros bairros");
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].filhos[0].clientes")).isEqualTo(6);
    }

    @Test
    @DisplayName("A39: barbeiro A inativo e conta de barbeiro sem vínculo: 404; CLIENTE: 403; sem token: 401")
    void quemPodeVer() throws Exception {
        Equipe e = equipe();
        jdbc.update("UPDATE barbeiro SET ativo = false WHERE id = ?", e.barbeiroA());
        alcanceDoBarbeiro(e.contaA(), "").andExpect(status().isNotFound());

        Conta semVinculo = conta("BARBEIRO");
        alcanceDoBarbeiro(semVinculo, "").andExpect(status().isNotFound());

        UUID cliente = cliente("Cliente Fictício", "11977770002");
        mvc.perform(MockMvcRequestBuilders.get("/admin/alcance").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeCliente(cliente))
                .with(ContaDeTeste.ip(ContaDeTeste.ipNovo()))).andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.get("/admin/alcance").with(ContaDeTeste.ip(ContaDeTeste.ipNovo())))
                .andExpect(status().isUnauthorized());
    }
}
