package io.github.k3vinrich4rd.altashoras.comum;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PrecoFormatadoTest {

    @ParameterizedTest(name = "A2: {0} centavos vira \"{1}\"")
    @DisplayName("A2: preço em centavos formatado em reais, com espaço normal e ponto de milhar")
    @CsvSource(delimiter = ';', value = {
            "4500;R$ 45,00", "123456;R$ 1.234,56", "0;R$ 0,00", "5;R$ 0,05", "2000;R$ 20,00", "100000000;R$ 1.000.000,00"})
    void formata(long centavos, String esperado) {
        assertThat(PrecoFormatado.de(centavos)).isEqualTo(esperado).doesNotContain("\u00a0");
    }
}
