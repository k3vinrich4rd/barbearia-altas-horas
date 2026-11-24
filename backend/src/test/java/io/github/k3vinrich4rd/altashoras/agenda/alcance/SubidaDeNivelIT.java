package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A14 a A19 da Fatia 11B: o lugar pequeno sobe de nível (bairro, município, região imediata, UF, "Outros estados") em vez de sumir. */
class SubidaDeNivelIT extends AlcanceBase {

    private static final Onde VILA_A = new Onde("Vila A", OSASCO, "Osasco", "SP");
    private static final Onde VILA_B = new Onde("Vila B", OSASCO, "Osasco", "SP");
    private static final Onde VILA_C = new Onde("Vila C", OSASCO, "Osasco", "SP");

    private static String regiaoDeSaoPaulo() {
        return "Outros municípios da região imediata de São Paulo";
    }

    private List<Map<String, Object>> grupos(String corpo, String filtro) {
        return ler(corpo, "$.grupos[?(" + filtro + ")]");
    }

    @Test
    @DisplayName("A14: Centro com 7, Vila A com 3 e Vila B com 2 pessoas em Osasco: nivel = MUNICIPIO mostra Osasco 12, Centro 7 e \"Outros bairros\" 5 (as duas vilas somam k e ficam no município)")
    void bairrosPequenosSomamNoOutrosBairros() throws Exception {
        Catalogo c = catalogo();
        pessoas(7, c.barbeiro(), c.servico(), CENTRO);
        pessoas(3, c.barbeiro(), c.servico(), VILA_A);
        pessoas(2, c.barbeiro(), c.servico(), VILA_B);

        String corpo = painel("?nivel=MUNICIPIO");
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].clientes")).isEqualTo(12);
        assertThat(this.<String>ler(corpo, "$.grupos[0].nome")).isEqualTo("Osasco");
        assertThat(this.<String>ler(corpo, "$.grupos[0].tipo")).isEqualTo("MUNICIPIO");
        assertThat(this.<List<Object>>ler(corpo, "$.grupos[0].filhos[?(@.nome == 'Centro')].clientes")).containsExactly(7);
        assertThat(this.<List<Object>>ler(corpo, "$.grupos[0].filhos[?(@.nome == 'Outros bairros')].clientes")).containsExactly(5);
        assertThat(this.<List<Object>>ler(corpo, "$.grupos[0].filhos[?(@.nome == 'Vila A')]")).isEmpty();
        assertThat(this.<Boolean>ler(corpo, "$.totalVisivel")).isTrue();
        assertThat(this.<Integer>ler(corpo, "$.clientes")).isEqualTo(12);
    }

    @Test
    @DisplayName("A15: Osasco com 5 pessoas (todas em bairros pequenos, de 2, 2 e 1): Osasco 5 com filhos = [Outros bairros 5] e nenhum bairro nomeado")
    void municipioComSoBairrosPequenos() throws Exception {
        Catalogo c = catalogo();
        pessoas(2, c.barbeiro(), c.servico(), VILA_A);
        pessoas(2, c.barbeiro(), c.servico(), VILA_B);
        pessoas(1, c.barbeiro(), c.servico(), VILA_C);

        String corpo = painel("?nivel=MUNICIPIO");
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].clientes")).isEqualTo(5);
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].filhos.length()")).isEqualTo(1);
        assertThat(this.<String>ler(corpo, "$.grupos[0].filhos[0].nome")).isEqualTo("Outros bairros");
        assertThat(this.<String>ler(corpo, "$.grupos[0].filhos[0].tipo")).isEqualTo("OUTROS");
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].filhos[0].clientes")).isEqualTo(5);
    }

    @Test
    @DisplayName("A16: Osasco com 3 e Carapicuíba com 3 (a mesma região imediata): nivel = MUNICIPIO sobe os dois para \"Outros municípios da região imediata de São Paulo\", com 6 e agrupadoEm = REGIAO")
    void doisMunicipiosPequenosSobemParaARegiao() throws Exception {
        Catalogo c = catalogo();
        pessoas(3, c.barbeiro(), c.servico(), CENTRO);
        pessoas(3, c.barbeiro(), c.servico(), CARAPICUIBA_CENTRO);

        String corpo = painel("?nivel=MUNICIPIO");
        assertThat(grupos(corpo, "@.tipo == 'MUNICIPIO'")).isEmpty();
        List<Map<String, Object>> outros = grupos(corpo, "@.tipo == 'OUTROS'");
        assertThat(outros).hasSize(1);
        assertThat(outros.getFirst()).containsEntry("nome", regiaoDeSaoPaulo()).containsEntry("clientes", 6)
                .containsEntry("agrupadoEm", "REGIAO").containsEntry("abaixoDoLimite", false).containsEntry("uf", "SP")
                .containsEntry("codigo", "350001");
    }

    @Test
    @DisplayName("A17: um município pequeno de São Paulo (3) e Campinas (3), em regiões diferentes de SP: sobem juntas para \"Outras regiões de SP\" com 6 e agrupadoEm = UF; com 2 e 2 ainda sobem até \"Outros estados\"")
    void regioesPequenasSobemParaAUf() throws Exception {
        Catalogo c = catalogo();
        pessoas(3, c.barbeiro(), c.servico(), SE);
        pessoas(3, c.barbeiro(), c.servico(), CAMPINAS_CENTRO);

        String corpo = painel("?nivel=MUNICIPIO");
        assertThat(grupos(corpo, "@.tipo == 'MUNICIPIO'")).isEmpty();
        List<Map<String, Object>> outros = grupos(corpo, "@.tipo == 'OUTROS'");
        assertThat(outros).hasSize(1);
        assertThat(outros.getFirst()).containsEntry("nome", "Outras regiões de SP").containsEntry("clientes", 6)
                .containsEntry("agrupadoEm", "UF").containsEntry("codigo", "SP");

        jdbc.update("DELETE FROM reserva");
        pessoas(2, c.barbeiro(), c.servico(), SE);
        pessoas(2, c.barbeiro(), c.servico(), CAMPINAS_CENTRO);
        String menor = painel("?nivel=MUNICIPIO");
        List<Map<String, Object>> estados = grupos(menor, "@.nome == 'Outros estados'");
        assertThat(estados).hasSize(1);
        assertThat(estados.getFirst()).containsEntry("agrupadoEm", "PAIS").containsEntry("abaixoDoLimite", true)
                .containsEntry("clientes", null);
        assertThat(grupos(menor, "@.nome == 'Outras regiões de SP'")).isEmpty();
    }

    @Test
    @DisplayName("A18: 2 pessoas no RJ e as demais em SP: nivel = ESTADO mostra SP e o RJ vira \"Outros estados\" com menos de k (abaixoDoLimite = true, clientes = null)")
    void estadoPequenoViraOutrosEstados() throws Exception {
        Catalogo c = catalogo();
        pessoas(8, c.barbeiro(), c.servico(), CENTRO);
        pessoas(2, c.barbeiro(), c.servico(), COPACABANA);

        String corpo = painel("?nivel=ESTADO");
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].clientes")).isEqualTo(8);
        assertThat(this.<String>ler(corpo, "$.grupos[0].tipo")).isEqualTo("UF");
        assertThat(this.<String>ler(corpo, "$.grupos[0].codigo")).isEqualTo("SP");
        List<Map<String, Object>> estados = grupos(corpo, "@.nome == 'Outros estados'");
        assertThat(estados).hasSize(1);
        assertThat(estados.getFirst()).containsEntry("abaixoDoLimite", true).containsEntry("clientes", null)
                .containsEntry("percentual", null);
        assertThat(grupos(corpo, "@.codigo == 'RJ'")).isEmpty();
    }

    @Test
    @DisplayName("A19: pessoas sem município (cidade fora da tabela ou sem código) entram direto na UF: 6 pessoas de SP sem município aparecem em \"Outras regiões de SP\"; sem nenhum município, o nível Região e o Município não inventam um")
    void semMunicipioEntraNaUf() throws Exception {
        Catalogo c = catalogo();
        Onde semMunicipio = new Onde("Bairro Qualquer", null, "Cidade Pequena", "SP");
        pessoas(6, c.barbeiro(), c.servico(), semMunicipio);

        String estado = painel("?nivel=ESTADO");
        assertThat(this.<Integer>ler(estado, "$.grupos[0].clientes")).isEqualTo(6);
        assertThat(this.<String>ler(estado, "$.grupos[0].filhos[0].nome")).isEqualTo("Outras regiões de SP");
        for (String nivel : new String[] {"REGIAO", "MUNICIPIO"}) {
            String corpo = painel("?nivel=" + nivel);
            assertThat(grupos(corpo, "@.tipo == 'OUTROS'")).hasSize(1);
            assertThat(this.<String>ler(corpo, "$.grupos[0].nome")).isEqualTo("Outras regiões de SP");
            assertThat(this.<String>ler(corpo, "$.grupos[0].agrupadoEm")).isEqualTo("UF");
            assertThat(grupos(corpo, "@.tipo == 'MUNICIPIO' || @.tipo == 'REGIAO'")).isEmpty();
        }
    }
}
