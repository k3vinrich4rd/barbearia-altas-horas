package io.github.k3vinrich4rd.altashoras.privacidade.titular;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FormatadorDeCanalDeContatoTest {

    @ParameterizedTest(name = "\"{0}\" vira \"{1}\"")
    @DisplayName("celular de 11 dígitos e fixo de 10 saem no padrão brasileiro; e-mail e o resto ficam iguais")
    @CsvSource(delimiter = ';', value = {
            "11900000001;(11) 90000-0001",
            "11 90000-0001;(11) 90000-0001",
            "(11) 90000-0001;(11) 90000-0001",
            "1133334444;(11) 3333-4444",
            "(11) 3333-4444;(11) 3333-4444",
            "contato@exemplo.test;contato@exemplo.test",
            "jaime@escola.test;jaime@escola.test",
            "119000000;119000000"
    })
    void formata(String entrada, String esperado) {
        assertThat(FormatadorDeCanalDeContato.formatar(entrada)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("texto livre, vazio e nulo não são alterados")
    void textosLivresFicamIguais() {
        assertThat(FormatadorDeCanalDeContato.formatar(
                "o canal de contato que a barbearia divulgar no estabelecimento (ainda não configurado nesta instalação)"))
                        .startsWith("o canal de contato");
        assertThat(FormatadorDeCanalDeContato.formatar("")).isEqualTo("");
        assertThat(FormatadorDeCanalDeContato.formatar("   ")).isEqualTo("   ");
        assertThat(FormatadorDeCanalDeContato.formatar(null)).isNull();
    }
}
