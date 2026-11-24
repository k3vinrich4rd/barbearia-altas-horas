package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A20 e A21 da Fatia 11B: nenhuma subtração entre um grupo e os filhos dele revela um número menor que k (supressão complementar, E9). */
class SupressaoComplementarIT extends AlcanceBase {

    private static final Onde VILA_A = new Onde("Vila A", OSASCO, "Osasco", "SP");
    private static final Onde VILA_B = new Onde("Vila B", OSASCO, "Osasco", "SP");

    @Test
    @DisplayName("A20: município com 8 pessoas (Centro 5 e dois bairros de 2 e 1, a sobra seria 3 < k): o menor bairro nomeado (Centro, 5) é rebaixado e filhos = [Outros bairros 8]; nenhum número menor que k e nenhuma subtração dá 1 a 4")
    void menorFilhoNomeadoEhRebaixado() throws Exception {
        Catalogo c = catalogo();
        pessoas(5, c.barbeiro(), c.servico(), CENTRO);
        pessoas(2, c.barbeiro(), c.servico(), VILA_A);
        pessoas(1, c.barbeiro(), c.servico(), VILA_B);

        String corpo = painel("?nivel=MUNICIPIO");
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].clientes")).isEqualTo(8);
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].filhos.length()")).isEqualTo(1);
        assertThat(this.<String>ler(corpo, "$.grupos[0].filhos[0].nome")).isEqualTo("Outros bairros");
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].filhos[0].clientes")).isEqualTo(8);
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].clientes") - this.<Integer>ler(corpo, "$.grupos[0].filhos[0].clientes")).isZero();
    }

    @Test
    @DisplayName("A21: em qualquer nível, nenhum valor de 1 a k - 1 aparece como clientes ou percentual; abaixo de k só como abaixoDoLimite = true com clientes e percentual nulos; sem NaN")
    void nuncaUmNumeroMenorQueK() throws Exception {
        Catalogo c = catalogo();
        pessoas(7, c.barbeiro(), c.servico(), CENTRO);
        pessoas(2, c.barbeiro(), c.servico(), VILA_A);
        pessoas(3, c.barbeiro(), c.servico(), CARAPICUIBA_CENTRO);
        pessoas(2, c.barbeiro(), c.servico(), COPACABANA);
        pessoas(2, c.barbeiro(), c.servico(), new Onde(null, CAMPINAS, "Campinas", "SP"));
        atendimento(c.barbeiro(), c.servico(), "CONCLUIDO", null);
        atendimento(c.barbeiro(), c.servico(), "CONCLUIDO", null);

        int conferidos = 0;
        for (String nivel : new String[] {"ESTADO", "REGIAO", "MUNICIPIO"}) {
            String corpo = painel("?nivel=" + nivel);
            assertThat(corpo).doesNotContain("NaN").doesNotContain("Infinity");
            List<Object> linhas = new ArrayList<>(JsonPath.<List<Object>>read(corpo, "$.grupos[*]"));
            linhas.addAll(JsonPath.<List<Object>>read(corpo, "$.grupos[*].filhos[*]"));
            linhas.add(JsonPath.<Object>read(corpo, "$.semRegiao"));
            for (Object linha : linhas) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> mapa = (java.util.Map<String, Object>) linha;
                Object clientes = mapa.get("clientes");
                assertThat(clientes == null || ((Number) clientes).intValue() == 0 || ((Number) clientes).intValue() >= 5)
                        .as(nivel + ": " + mapa).isTrue();
                if (Boolean.TRUE.equals(mapa.get("abaixoDoLimite"))) {
                    assertThat(clientes).as(nivel + ": " + mapa).isNull();
                    assertThat(mapa.get("percentual")).as(nivel + ": " + mapa).isNull();
                }
                if (clientes == null) {
                    assertThat(mapa.get("abaixoDoLimite")).as(nivel + ": " + mapa).isEqualTo(true);
                }
                conferidos++;
            }
        }
        assertThat(conferidos).isGreaterThanOrEqualTo(10);
    }
}
