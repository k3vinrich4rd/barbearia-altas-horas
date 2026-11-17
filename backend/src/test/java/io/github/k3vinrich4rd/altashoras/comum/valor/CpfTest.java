package io.github.k3vinrich4rd.altashoras.comum.valor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

class CpfTest {

    @Test
    @DisplayName("CPF: tira a máscara, valida os dígitos verificadores e mascara no toString()")
    void validaEMascara() {
        Cpf cpf = Cpf.de("529.982.247-25");

        assertThat(cpf.digitos()).isEqualTo("52998224725");
        assertThat(cpf.mascarado()).isEqualTo("***.***.***-25");
        assertThat(cpf).hasToString("***.***.***-25");
        assertThat(Cpf.de("11144477735").digitos()).isEqualTo("11144477735");
    }

    @Test
    @DisplayName("CPF: dígito errado, tamanho errado, vazio e 11 dígitos iguais são CPF_INVALIDO")
    void rejeitaInvalidos() {
        for (String invalido : new String[] {"529.982.247-24", "529.982.247-2", "5299822472555", "", "   ", "111.111.111-11",
                "000.000.000-00", "abc"}) {
            assertThatThrownBy(() -> Cpf.de(invalido)).isInstanceOfSatisfying(ExcecaoDeNegocio.class,
                    erro -> assertThat(erro.codigo()).isEqualTo(CodigoErro.CPF_INVALIDO));
        }
        assertThatThrownBy(() -> Cpf.de(null)).isInstanceOf(ExcecaoDeNegocio.class);
    }
}
