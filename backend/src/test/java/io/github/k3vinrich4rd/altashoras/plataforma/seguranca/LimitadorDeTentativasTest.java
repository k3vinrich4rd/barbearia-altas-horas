package io.github.k3vinrich4rd.altashoras.plataforma.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;

/** A janela deslizante, sem Spring: o relógio é adiantado na mão. */
class LimitadorDeTentativasTest {

    private static final Duration JANELA = Duration.ofMinutes(15);
    private final RelogioAjustavel relogio = new RelogioAjustavel();
    private final LimitadorEmMemoria limitador = new LimitadorEmMemoria(relogio);

    @Test
    @DisplayName("A28: bloqueia na 5ª tentativa, informa quanto falta e libera quando a janela passa")
    void bloqueiaELibera() {
        for (int i = 0; i < 4; i++) {
            limitador.registrar("chave", JANELA);
            relogio.avancar(Duration.ofMinutes(1));
        }
        assertThat(limitador.segundosBloqueado("chave", 5, JANELA)).isZero();
        limitador.registrar("chave", JANELA);
        // A primeira tentativa foi há 4 minutos: faltam 11 para ela sair da janela.
        assertThat(limitador.segundosBloqueado("chave", 5, JANELA)).isEqualTo(11 * 60);
        relogio.avancar(Duration.ofMinutes(11));
        assertThat(limitador.segundosBloqueado("chave", 5, JANELA)).isZero();
    }

    @Test
    @DisplayName("A29: um acerto zera a contagem: 4 erros, acerto e mais 4 erros não bloqueiam")
    void acertoZera() {
        for (int i = 0; i < 4; i++) {
            limitador.registrar("chave", JANELA);
        }
        limitador.zerar("chave");
        for (int i = 0; i < 4; i++) {
            limitador.registrar("chave", JANELA);
        }
        assertThat(limitador.segundosBloqueado("chave", 5, JANELA)).isZero();
    }

    @Test
    @DisplayName("Chaves diferentes não se misturam")
    void chavesIndependentes() {
        for (int i = 0; i < 5; i++) {
            limitador.registrar("a", JANELA);
        }
        assertThat(limitador.segundosBloqueado("a", 5, JANELA)).isPositive();
        assertThat(limitador.segundosBloqueado("b", 5, JANELA)).isZero();
    }
}
