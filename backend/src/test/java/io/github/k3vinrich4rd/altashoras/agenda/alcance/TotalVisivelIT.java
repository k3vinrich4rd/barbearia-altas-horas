package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A22 da Fatia 11B: o total só aparece quando o que ficou escondido no topo é 0 ou pelo menos k (E10). */
class TotalVisivelIT extends AlcanceBase {

    @Test
    @DisplayName("A22: sem região com 2 → totalVisivel = false e total e percentuais nulos; sem região com 0 → totalVisivel = true; \"Outros estados\" com 3 → totalVisivel = false")
    void totalSoApareceQuandoNadaFicaEscondido() throws Exception {
        Catalogo c = catalogo();
        pessoas(6, c.barbeiro(), c.servico(), CENTRO);

        // sem região = 0
        String zero = painel("?nivel=MUNICIPIO");
        assertThat(this.<Boolean>ler(zero, "$.totalVisivel")).isTrue();
        assertThat(this.<Integer>ler(zero, "$.clientes")).isEqualTo(6);
        assertThat(this.<Double>ler(zero, "$.grupos[0].percentual")).isEqualTo(100.0);
        assertThat(this.<Integer>ler(zero, "$.semRegiao.clientes")).isZero();

        // sem região = 2 (menos de k): o total e os percentuais somem
        atendimento(c.barbeiro(), c.servico(), "CONCLUIDO", null);
        atendimento(c.barbeiro(), c.servico(), "CONCLUIDO", null);
        String dois = painel("?nivel=MUNICIPIO");
        assertThat(this.<Boolean>ler(dois, "$.totalVisivel")).isFalse();
        assertThat(this.<Object>ler(dois, "$.clientes")).isNull();
        assertThat(this.<Object>ler(dois, "$.grupos[0].percentual")).isNull();
        assertThat(this.<Integer>ler(dois, "$.grupos[0].clientes")).isEqualTo(6); // o número do município continua
        assertThat(this.<Boolean>ler(dois, "$.semRegiao.abaixoDoLimite")).isTrue();

        // sem região = 5 (k): aparece, e o total volta
        for (int i = 0; i < 3; i++) {
            atendimento(c.barbeiro(), c.servico(), "CONCLUIDO", null);
        }
        String cinco = painel("?nivel=MUNICIPIO");
        assertThat(this.<Boolean>ler(cinco, "$.totalVisivel")).isTrue();
        assertThat(this.<Integer>ler(cinco, "$.semRegiao.clientes")).isEqualTo(5);
        assertThat(this.<Integer>ler(cinco, "$.clientes")).isEqualTo(11);

        // "Outros estados" com 3 (RJ): o total some
        jdbc.update("DELETE FROM reserva");
        pessoas(6, c.barbeiro(), c.servico(), CENTRO);
        pessoas(3, c.barbeiro(), c.servico(), COPACABANA);
        String outros = painel("?nivel=ESTADO");
        assertThat(this.<Boolean>ler(outros, "$.totalVisivel")).isFalse();
        assertThat(this.<Object>ler(outros, "$.clientes")).isNull();
        assertThat(this.<Object>ler(outros, "$.grupos[0].percentual")).isNull();
    }
}
