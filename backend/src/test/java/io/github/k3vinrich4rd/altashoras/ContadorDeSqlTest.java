package io.github.k3vinrich4rd.altashoras;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A contagem é por thread, para jobs em paralelo não quebrarem os testes de N+1. */
class ContadorDeSqlTest {

    @Test
    @DisplayName("consultas preparadas em outra thread não entram no total desta")
    void consultasDeOutraThreadNaoContam() throws Exception {
        long antes = ContadorDeSql.total();
        Thread outra = new Thread(ContadorDeSql::registrar);
        outra.start();
        outra.join();
        assertThat(ContadorDeSql.total()).isEqualTo(antes);
        ContadorDeSql.registrar();
        assertThat(ContadorDeSql.total()).isEqualTo(antes + 1);
    }
}
