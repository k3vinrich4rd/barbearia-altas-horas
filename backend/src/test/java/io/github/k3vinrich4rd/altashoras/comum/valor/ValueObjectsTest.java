package io.github.k3vinrich4rd.altashoras.comum.valor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValueObjectsTest {

    @Test
    @DisplayName("A38: e-mail, telefone e CEP normalizam o valor e mascaram no toString()")
    void normalizamEMascaram() {
        Email email = Email.de("  Cliente.A@Exemplo.Test ");
        assertThat(email.valor()).isEqualTo("cliente.a@exemplo.test");
        assertThat(email).hasToString("c***@exemplo.test");

        Telefone telefone = Telefone.de("(11) 90000-0001");
        assertThat(telefone.digitos()).isEqualTo("11900000001");
        assertThat(telefone.valido()).isTrue();
        assertThat(telefone).hasToString("*******0001");
        assertThat(Telefone.de("12345").valido()).isFalse();

        Cep cep = Cep.de("01001-000");
        assertThat(cep.digitos()).isEqualTo("01001000");
        assertThat(cep.valido()).isTrue();
        assertThat(cep).hasToString("*****000");
        assertThat(Cep.de("0100100").valido()).isFalse();
    }
}
