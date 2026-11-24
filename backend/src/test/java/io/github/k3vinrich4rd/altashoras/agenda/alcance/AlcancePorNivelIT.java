package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A34, A35, A41 e A42 da Fatia 11D: o painel por estado, região e município, o filtro de UF e os períodos. */
class AlcancePorNivelIT extends AlcanceBase {

    private static final LocalDate HA_60_DIAS = LocalDate.of(2026, 9, 18);
    private static final LocalDate HA_200_DIAS = LocalDate.of(2026, 5, 1);

    private void spERj(Catalogo c) {
        pessoas(8, c.barbeiro(), c.servico(), CENTRO);
        pessoas(6, c.barbeiro(), c.servico(), CARAPICUIBA_CENTRO);
        pessoas(7, c.barbeiro(), c.servico(), CAMPINAS_CENTRO);
        pessoas(6, c.barbeiro(), c.servico(), COPACABANA);
    }

    private static List<Object> nomes(String corpo, String caminho) {
        return ler(corpo, caminho);
    }

    @Test
    @DisplayName("A34: ADMIN, dados em SP e RJ: ESTADO = linhas por UF com regiões embaixo; REGIAO = por região imediata com municípios embaixo; MUNICIPIO (o padrão) = por município com bairros embaixo; nivel inválido é 400")
    void tresNiveis() throws Exception {
        spERj(catalogo());

        String estado = painel("?nivel=ESTADO");
        assertThat(nomes(estado, "$.grupos[*].tipo")).containsExactly("UF", "UF");
        assertThat(nomes(estado, "$.grupos[*].codigo")).containsExactly("SP", "RJ");
        assertThat(nomes(estado, "$.grupos[*].clientes")).containsExactly(21, 6);
        assertThat(nomes(estado, "$.grupos[0].filhos[*].nome")).containsExactly("São Paulo", "Campinas");
        assertThat(nomes(estado, "$.grupos[0].filhos[*].tipo")).containsOnly("REGIAO");
        assertThat(nomes(estado, "$.grupos[0].filhos[*].clientes")).containsExactly(14, 7);

        String regiao = painel("?nivel=REGIAO");
        assertThat(nomes(regiao, "$.grupos[*].tipo")).containsExactly("REGIAO", "REGIAO", "REGIAO");
        assertThat(nomes(regiao, "$.grupos[*].nome")).containsExactly("São Paulo", "Campinas", "Rio de Janeiro");
        assertThat(nomes(regiao, "$.grupos[0].filhos[*].nome")).containsExactly("Osasco", "Carapicuíba");
        assertThat(nomes(regiao, "$.grupos[0].filhos[*].clientes")).containsExactly(8, 6);

        String municipio = painel("");
        assertThat(this.<String>ler(municipio, "$.nivel")).isEqualTo("MUNICIPIO");
        assertThat(nomes(municipio, "$.grupos[*].nome")).containsExactly("Osasco", "Carapicuíba", "Campinas", "Rio de Janeiro");
        assertThat(nomes(municipio, "$.grupos[0].filhos[*].nome")).containsExactly("Centro");
        assertThat(this.<Integer>ler(municipio, "$.k")).isEqualTo(5);

        alcanceDoAdmin("?nivel=BAIRRO").andExpect(status().isBadRequest());
        alcanceDoAdmin("?periodo=ONTEM").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A35: ADMIN com ?uf=SP: só SP, nos três níveis (e o RJ some)")
    void filtroDeUf() throws Exception {
        spERj(catalogo());
        for (String nivel : new String[] {"ESTADO", "REGIAO", "MUNICIPIO"}) {
            String sp = painel("?nivel=" + nivel + "&uf=SP");
            assertThat(sp).contains("\"uf\":\"SP\"").doesNotContain("Rio de Janeiro").doesNotContain("Copacabana").doesNotContain("\"RJ\"");
            assertThat(nomes(sp, "$.grupos[*].uf")).containsOnly("SP");
        }
        String rj = painel("?nivel=ESTADO&uf=rj");
        assertThat(nomes(rj, "$.grupos[*].codigo")).containsExactly("RJ");
    }

    @Test
    @DisplayName("A41: os períodos padrão (30 dias, 90 dias, 12 meses) e de/ate antigos: cada um conta o seu; de depois de ate e janela acima de 366 dias seguem 400; uma diferença entre janelas menor que k sobe de nível (E11)")
    void periodos() throws Exception {
        Catalogo c = catalogo();
        // Centro: 6 nos últimos 30 dias, 6 há 60 dias e 6 há 200 dias: 6, 12 e 18 (as diferenças são 6, 6 e 12: todas >= k)
        pessoas(6, c.barbeiro(), c.servico(), CENTRO);
        pessoasEm(6, c.barbeiro(), c.servico(), HA_60_DIAS, CENTRO);
        pessoasEm(6, c.barbeiro(), c.servico(), HA_200_DIAS, CENTRO);
        assertThat(this.<Integer>ler(painel(""), "$.grupos[0].clientes")).isEqualTo(6);
        assertThat(this.<Integer>ler(painel("?periodo=ULTIMOS_30_DIAS"), "$.grupos[0].clientes")).isEqualTo(6);
        assertThat(this.<Integer>ler(painel("?periodo=ULTIMOS_90_DIAS"), "$.grupos[0].clientes")).isEqualTo(12);
        assertThat(this.<Integer>ler(painel("?periodo=ULTIMOS_12_MESES"), "$.grupos[0].clientes")).isEqualTo(18);
        String antigo = painel("?de=2026-11-01&ate=2026-11-17");
        assertThat(this.<Integer>ler(antigo, "$.grupos[0].clientes")).isEqualTo(6);
        assertThat(this.<String>ler(antigo, "$.periodo.de")).isEqualTo("2026-11-01");

        alcanceDoAdmin("?de=2026-11-17&ate=2026-11-01").andExpect(status().isBadRequest());
        alcanceDoAdmin("?de=2025-01-01&ate=2026-11-17").andExpect(status().isBadRequest());

        // E11 entre janelas: Carapicuíba tem 6 nos últimos 30 dias e mais 2 há 60 dias: 30 dias = 6 e 90 dias = 8 (diferença 2)
        pessoas(6, c.barbeiro(), c.servico(), CARAPICUIBA_CENTRO);
        pessoasEm(2, c.barbeiro(), c.servico(), HA_60_DIAS, CARAPICUIBA_CENTRO);
        String trinta = painel("?periodo=ULTIMOS_30_DIAS");
        assertThat(nomes(trinta, "$.grupos[?(@.tipo == 'MUNICIPIO')].nome")).doesNotContain("Carapicuíba").contains("Osasco");
        String noventa = painel("?periodo=ULTIMOS_90_DIAS");
        assertThat(nomes(noventa, "$.grupos[?(@.tipo == 'MUNICIPIO')].nome")).contains("Carapicuíba");
    }

    @Test
    @DisplayName("A42: o painel responde com Cache-Control: no-store, e o percentual tem 1 casa decimal e é sobre o total do recorte")
    void semCacheEPercentual() throws Exception {
        Catalogo c = catalogo();
        pessoas(8, c.barbeiro(), c.servico(), CENTRO);
        pessoas(6, c.barbeiro(), c.servico(), CARAPICUIBA_CENTRO);
        alcanceDoAdmin("?nivel=MUNICIPIO").andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.grupos[0].percentual").value(57.1)).andExpect(jsonPath("$.grupos[1].percentual").value(42.9));
        String corpo = painel("?nivel=MUNICIPIO");
        List<Map<String, Object>> grupos = ler(corpo, "$.grupos");
        for (Map<String, Object> grupo : grupos) {
            assertThat(String.valueOf(grupo.get("percentual"))).matches("\\d+\\.\\d");
        }
    }
}
