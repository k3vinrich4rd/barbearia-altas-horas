package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.ip;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** A71 a A73: a vitrine pública (GET /alcance): nome e quantidade, sem limite mínimo (D149), 12 meses, cache de 1 hora e limite por IP (E35, D-9). */
class AlcancePublicoIT extends AlcanceBase {

    private ResultActions vitrine(String deOnde) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/alcance").with(ip(deOnde)));
    }

    @Test
    @DisplayName("A71: no cenário do A67, GET /alcance sem token devolve Osasco (6) com Centro (6) e São Paulo (2) com Sé (2), do maior para o menor, totalCidades 2, totalBairros 2, sem Outras cidades, sem percentual e sem semRegiao")
    void nomeEQuantidadePorCidadeEBairro() throws Exception {
        cenarioDoA67();

        String corpo = vitrine("203.0.113.71").andExpect(status().isOk())
                .andExpect(jsonPath("$.atualizadoEm").value("2026-11-17T08:00:00-03:00"))
                .andExpect(jsonPath("$.totalCidades").value(2))
                .andExpect(jsonPath("$.totalBairros").value(2))
                .andExpect(jsonPath("$.regioes.length()").value(2))
                .andExpect(jsonPath("$.regioes[0].cidade").value("Osasco"))
                .andExpect(jsonPath("$.regioes[0].uf").value("SP"))
                .andExpect(jsonPath("$.regioes[0].atendimentos").value(6))
                .andExpect(jsonPath("$.regioes[0].bairros.length()").value(1))
                .andExpect(jsonPath("$.regioes[0].bairros[0].bairro").value("Centro"))
                .andExpect(jsonPath("$.regioes[0].bairros[0].atendimentos").value(6))
                .andExpect(jsonPath("$.regioes[1].cidade").value("São Paulo"))
                .andExpect(jsonPath("$.regioes[1].atendimentos").value(2))
                .andExpect(jsonPath("$.regioes[1].bairros[0].bairro").value("Sé"))
                .andExpect(jsonPath("$.regioes[1].bairros[0].atendimentos").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("percentual").doesNotContain("semRegiao").doesNotContain("Outras cidades")
                .doesNotContain("Outros bairros").doesNotContain("abaixoDoLimite").doesNotContain("Visitante");
    }

    @Test
    @DisplayName("A72: atendimentos de 13 meses atrás num bairro não aparecem na vitrine (só os últimos 12 meses); os de 11 meses atrás aparecem com a quantidade, mesmo com poucos")
    void soOsUltimosDozeMeses() throws Exception {
        Catalogo c = catalogo();
        atendimentosEm(5, c.barbeiro(), c.servico(), LocalDate.of(2025, 10, 14), "Vila Antiga", "Barueri", "SP"); // 13 meses
        atendimentosEm(2, c.barbeiro(), c.servico(), LocalDate.of(2025, 12, 16), "Jardim Novo", "Carapicuíba", "SP"); // 11 meses

        vitrine("203.0.113.72").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCidades").value(1))
                .andExpect(jsonPath("$.regioes[0].cidade").value("Carapicuíba"))
                .andExpect(jsonPath("$.regioes[0].atendimentos").value(2))
                .andExpect(jsonPath("$.regioes[0].bairros[0].bairro").value("Jardim Novo"))
                .andExpect(jsonPath("$.regioes[0].bairros[0].atendimentos").value(2))
                .andExpect(jsonPath("$.regioes[?(@.cidade == 'Barueri')]").isEmpty());
    }

    @Test
    @DisplayName("A73: duas chamadas com um atendimento novo no meio: a segunda vem do cache (1 hora, Ticker de teste) e só a de depois de 1 hora enxerga o dado novo")
    void cacheDeUmaHora() throws Exception {
        Catalogo c = catalogo();
        atendimentos(5, c.barbeiro(), c.servico(), "Centro", "Osasco", "SP");

        vitrine("203.0.113.73").andExpect(jsonPath("$.totalCidades").value(1));
        atendimentos(5, c.barbeiro(), c.servico(), "Jardim Novo", "Carapicuíba", "SP"); // chega no meio

        vitrine("203.0.113.73").andExpect(jsonPath("$.totalCidades").value(1)); // do cache

        ticker.avancar(Duration.ofMinutes(59).plusSeconds(59));
        vitrine("203.0.113.73").andExpect(jsonPath("$.totalCidades").value(1)); // ainda do cache

        ticker.avancar(Duration.ofSeconds(1)); // fecha 1 hora
        vitrine("203.0.113.73").andExpect(jsonPath("$.totalCidades").value(2));
    }

    @Test
    @DisplayName("A73: 31 chamadas no mesmo minuto do mesmo IP: a 31ª é 429 com Retry-After; outro IP segue com 200 e um minuto depois o IP volta")
    void limiteDe30PorMinutoPorIp() throws Exception {
        String ip = "203.0.113.74";
        for (int i = 0; i < 30; i++) {
            vitrine(ip).andExpect(status().isOk());
        }

        vitrine(ip).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("LIMITE_EXCEDIDO"));
        vitrine("203.0.113.75").andExpect(status().isOk());

        relogio.avancar(Duration.ofMinutes(1));
        vitrine(ip).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A19: a vitrine mostra todas as cidades e bairros com a quantidade, ordenados do maior para o menor, inclusive o bairro de 1 atendimento, e sem nada de pessoa (nenhum nome de cliente no JSON)")
    void vitrineSemLimiteMinimoOrdenadaPorQuantidade() throws Exception {
        Catalogo c = catalogo();
        atendimentos(1, c.barbeiro(), c.servico(), "Vila Yara", "Osasco", "SP");
        atendimentos(4, c.barbeiro(), c.servico(), "Centro", "Osasco", "SP");
        atendimentos(1, c.barbeiro(), c.servico(), "Centro", "Barueri", "SP");

        String corpo = vitrine("203.0.113.79").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCidades").value(2))
                .andExpect(jsonPath("$.totalBairros").value(3))
                .andExpect(jsonPath("$.regioes[0].cidade").value("Osasco"))
                .andExpect(jsonPath("$.regioes[0].atendimentos").value(5))
                .andExpect(jsonPath("$.regioes[0].bairros[0].bairro").value("Centro"))
                .andExpect(jsonPath("$.regioes[0].bairros[0].atendimentos").value(4))
                .andExpect(jsonPath("$.regioes[0].bairros[1].bairro").value("Vila Yara"))
                .andExpect(jsonPath("$.regioes[0].bairros[1].atendimentos").value(1))
                .andExpect(jsonPath("$.regioes[1].cidade").value("Barueri"))
                .andExpect(jsonPath("$.regioes[1].atendimentos").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("Visitante").doesNotContain("@").doesNotContain("whatsapp");
    }
}
