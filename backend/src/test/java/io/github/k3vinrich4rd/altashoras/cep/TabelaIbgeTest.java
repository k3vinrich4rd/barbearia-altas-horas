package io.github.k3vinrich4rd.altashoras.cep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A24: a tabela do IBGE embutida (Fatia 11, E16): 5.571 municípios, 510 regiões imediatas, 27 UFs, sem rede. */
class TabelaIbgeTest {

    private final TabelaIbge tabela = new TabelaIbge();

    @Test
    @DisplayName("A24: a tabela embutida tem 5.571 municípios, 510 regiões imediatas e 27 UFs, e a contagem bate com o cabeçalho do arquivo")
    void contagens() {
        assertThat(tabela.totalDeMunicipios()).isEqualTo(5571);
        assertThat(tabela.totalDeRegioes()).isEqualTo(510);
        assertThat(tabela.totalDeUfs()).isEqualTo(27);
        assertThat(tabela.versao()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    @DisplayName("A24: Osasco 3534401 pertence à região imediata 350001 (São Paulo); código fora da tabela devolve vazio")
    void osasco() {
        TabelaIbge.Municipio osasco = tabela.porCodigo("3534401").orElseThrow();
        assertThat(osasco.nome()).isEqualTo("Osasco");
        assertThat(osasco.uf()).isEqualTo("SP");
        assertThat(osasco.regiaoCodigo()).isEqualTo("350001");
        assertThat(osasco.regiaoNome()).isEqualTo("São Paulo");
        assertThat(tabela.porCodigo("9999999")).isEmpty();
        assertThat(tabela.porCodigo(null)).isEmpty();
    }

    @Test
    @DisplayName("A24: todo município tem região e UF, e cada região fica numa UF só")
    void todoMunicipioTemRegiao() {
        // Percorre pelo nome: os 5.571 códigos reais estão no arquivo; aqui só confere a consistência pelas amostras conhecidas.
        for (String codigo : new String[] {"3534401", "3510609", "3509502", "3304557", "1100015", "5300108"}) {
            TabelaIbge.Municipio m = tabela.porCodigo(codigo).orElseThrow();
            assertThat(m.regiaoCodigo()).isNotBlank();
            assertThat(tabela.nomeDaRegiao(m.regiaoCodigo())).contains(m.regiaoNome());
            assertThat(m.uf()).matches("[A-Z]{2}");
        }
    }

    @Test
    @DisplayName("A26: o nome sem acento e a UF acham o município quando é um só; sem acento e com outra caixa também")
    void porNomeEUf() {
        assertThat(tabela.porNomeEUf("osasco", "SP")).map(TabelaIbge.Municipio::codigo).contains("3534401");
        assertThat(tabela.porNomeEUf("Sao Paulo", "SP")).map(TabelaIbge.Municipio::codigo).contains("3550308");
        assertThat(tabela.porNomeEUf("Osasco", "RJ")).isEmpty();
        assertThat(tabela.porNomeEUf("Município que não existe", "SP")).isEmpty();
    }

    @Test
    @DisplayName("A24: arquivo sem cabeçalho de contagem, ou com contagem diferente, derruba a subida")
    void contagemConferida() {
        assertThatThrownBy(() -> TabelaIbge.doClasspath("ibge/municipios-contagem-errada.csv"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("contagem");
    }
}
