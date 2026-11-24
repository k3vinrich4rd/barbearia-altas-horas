package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;

/** A28 a A33 da Fatia 11C: a vitrine pública mostra só nomes e faixas de porcentagem, nunca número de pessoas (ADR 0014, item 4). */
class VitrineEmFaixasIT extends AlcanceBase {

    private static final Onde VILA_A = new Onde("Vila A", OSASCO, "Osasco", "SP");
    private static final Onde VILA_B = new Onde("Vila B", OSASCO, "Osasco", "SP");

    private String vitrineDe(String ip) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/alcance").with(ContaDeTeste.ip(ip))).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
    }

    private String vitrine() throws Exception {
        return vitrineDe(ContaDeTeste.ipNovo());
    }

    @Test
    @DisplayName("A28: GET /alcance sem token: nenhum número de pessoas no JSON (nem atendimentos, clientes, total ou percentual); só nomes e faixaPercentual em múltiplos de 5 ou \"<5\"; o \"Outros bairros\" vem sem faixa")
    void soNomesEFaixas() throws Exception {
        Catalogo c = catalogo();
        pessoas(30, c.barbeiro(), c.servico(), CENTRO);
        pessoas(9, c.barbeiro(), c.servico(), ALTINO);
        pessoas(3, c.barbeiro(), c.servico(), VILA_A);
        pessoas(3, c.barbeiro(), c.servico(), VILA_B);

        String corpo = vitrine();
        assertThat(corpo).doesNotContain("atendimentos").doesNotContain("clientes").doesNotContain("\"total\"")
                .doesNotContain("percentual\":").doesNotContain("NaN");
        assertThat(this.<Map<String, Object>>ler(corpo, "$")).containsOnlyKeys("atualizadoEm", "totalCidades", "totalBairros", "regioes");
        assertThat(this.<Integer>ler(corpo, "$.regioes.length()")).isEqualTo(1);
        assertThat(this.<String>ler(corpo, "$.regioes[0].cidade")).isEqualTo("Osasco");
        assertThat(this.<Integer>ler(corpo, "$.totalCidades")).isEqualTo(1);
        assertThat(this.<Integer>ler(corpo, "$.totalBairros")).isEqualTo(2); // Centro e Presidente Altino: os que passam no k
        List<Map<String, Object>> bairros = ler(corpo, "$.regioes[0].bairrosComFaixa");
        assertThat(bairros).extracting(b -> b.get("bairro")).containsExactly("Centro", "Presidente Altino", "Outros bairros");
        assertThat(bairros).extracting(b -> b.get("faixaPercentual")).containsExactly("65", "20", null); // 30/45 = 66,6% e 9/45 = 20%, arredondados para baixo
        assertThat(this.<List<Object>>ler(corpo, "$.regioes[0].bairros")).containsExactly("Centro", "Presidente Altino");
    }

    @Test
    @DisplayName("A29: município com 19 pessoas: só os nomes dos bairros que passam no k, sem faixa; com 20: as faixas aparecem")
    void faixasSoComVinteOuMais() throws Exception {
        Catalogo c = catalogo();
        pessoas(12, c.barbeiro(), c.servico(), CENTRO);
        pessoas(7, c.barbeiro(), c.servico(), ALTINO);
        String dezenove = vitrine();
        assertThat(this.<Boolean>ler(dezenove, "$.regioes[0].comFaixas")).isFalse();
        assertThat(this.<List<Map<String, Object>>>ler(dezenove, "$.regioes[0].bairrosComFaixa")).hasSize(2)
                .allSatisfy(b -> assertThat(b.get("faixaPercentual")).isNull());
        assertThat(this.<List<Object>>ler(dezenove, "$.regioes[0].bairros")).containsExactly("Centro", "Presidente Altino");

        atendimento(c.barbeiro(), c.servico(), "CONCLUIDO", CENTRO); // 20 pessoas: Centro 13 (65%) e Altino 7 (35%)
        vitrine.esquecer();
        String vinte = vitrine();
        assertThat(this.<Boolean>ler(vinte, "$.regioes[0].comFaixas")).isTrue();
        assertThat(this.<List<Object>>ler(vinte, "$.regioes[0].bairrosComFaixa[*].faixaPercentual")).containsExactly("65", "35");
    }

    @Test
    @DisplayName("A30: a faixa arredonda PARA BAIXO em múltiplos de 5: 14,9% é \"10\", 15,0% é \"15\" e 4,9% é \"<5\"")
    void faixaArredondaParaBaixo() throws Exception {
        assertThat(NiveisDeAlcance.faixa(149, 1000)).isEqualTo("10");
        assertThat(NiveisDeAlcance.faixa(150, 1000)).isEqualTo("15");
        assertThat(NiveisDeAlcance.faixa(49, 1000)).isEqualTo("<5");
        assertThat(NiveisDeAlcance.faixa(5, 100)).isEqualTo("5");
        assertThat(NiveisDeAlcance.faixa(100, 100)).isEqualTo("100");

        // e pela API: 6 de 40 (15%) é "15"; 5 de 40 (12,5%) é "10"
        Catalogo c = catalogo();
        pessoas(29, c.barbeiro(), c.servico(), CENTRO);
        pessoas(6, c.barbeiro(), c.servico(), ALTINO);
        pessoas(5, c.barbeiro(), c.servico(), VILA_A);
        String corpo = vitrine();
        assertThat(this.<List<Object>>ler(corpo, "$.regioes[0].bairrosComFaixa[?(@.bairro == 'Presidente Altino')].faixaPercentual"))
                .containsExactly("15");
        assertThat(this.<List<Object>>ler(corpo, "$.regioes[0].bairrosComFaixa[?(@.bairro == 'Vila A')].faixaPercentual"))
                .containsExactly("10");
    }

    @Test
    @DisplayName("A31: município com 4 pessoas não aparece; um \"Outros bairros\" com menos de k também não")
    void abaixoDeKNaoAparece() throws Exception {
        Catalogo c = catalogo();
        pessoas(4, c.barbeiro(), c.servico(), CARAPICUIBA_CENTRO);
        pessoas(5, c.barbeiro(), c.servico(), CENTRO);
        pessoas(2, c.barbeiro(), c.servico(), VILA_A);
        pessoas(1, c.barbeiro(), c.servico(), VILA_B);

        String corpo = vitrine();
        assertThat(this.<List<Object>>ler(corpo, "$.regioes[*].cidade")).containsExactly("Osasco");
        assertThat(this.<List<Object>>ler(corpo, "$.regioes[0].bairrosComFaixa[*].bairro")).containsExactly("Centro");
        assertThat(this.<Boolean>ler(corpo, "$.regioes[0].comFaixas")).isFalse(); // 8 pessoas
    }

    @Test
    @DisplayName("A32: 5 atendimentos de uma pessoa só não liberam bairro nem faixa na vitrine")
    void umaPessoaSoNaoLibera() throws Exception {
        Catalogo c = catalogo();
        Contato dela = Contato.novo();
        for (int i = 0; i < 5; i++) {
            atendimentoDe(dela, c.barbeiro(), c.servico(), DIA_DO_ATENDIMENTO, "CONCLUIDO", CENTRO);
        }
        String corpo = vitrine();
        assertThat(this.<List<Object>>ler(corpo, "$.regioes")).isEmpty();
        assertThat(this.<Integer>ler(corpo, "$.totalBairros")).isZero();
    }

    @Test
    @DisplayName("A33: duas chamadas com um dado novo no meio: a segunda vem do cache de 1 h; a 31ª consulta no minuto é 429 com Retry-After (D111 intacto)")
    void cacheELimite() throws Exception {
        Catalogo c = catalogo();
        pessoas(5, c.barbeiro(), c.servico(), CENTRO);
        String primeira = vitrine();
        assertThat(this.<List<Object>>ler(primeira, "$.regioes[*].cidade")).containsExactly("Osasco");

        pessoas(5, c.barbeiro(), c.servico(), CARAPICUIBA_CENTRO); // dado novo no meio
        assertThat(this.<List<Object>>ler(vitrine(), "$.regioes[*].cidade")).containsExactly("Osasco"); // ainda o cache

        ticker.avancar(Duration.ofHours(1).plusSeconds(1));
        assertThat(this.<List<Object>>ler(vitrine(), "$.regioes[*].cidade")).containsExactlyInAnyOrder("Osasco", "Carapicuíba");

        String ip = "203.0.113.33";
        for (int i = 0; i < 30; i++) {
            vitrineDe(ip);
        }
        mvc.perform(MockMvcRequestBuilders.get("/alcance").with(ContaDeTeste.ip(ip))).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
