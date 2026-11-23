package io.github.k3vinrich4rd.altashoras.conta.convite;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@code app.conta.convite.validade} (padrão 48 h, H25) e {@code app.conta.convite.tentativas-max} (padrão 5, E8). Uma
 * validade fora de 1 h a 7 dias derruba a subida: melhor falhar cedo do que emitir um convite eterno ou já vencido.
 */
@Component
public class ParametrosDoConvite {

    static final Duration VALIDADE_MINIMA = Duration.ofHours(1);
    static final Duration VALIDADE_MAXIMA = Duration.ofDays(7);

    private final Duration validade;
    private final int tentativasMax;

    ParametrosDoConvite(@Value("${app.conta.convite.validade:48h}") Duration validade,
            @Value("${app.conta.convite.tentativas-max:5}") int tentativasMax) {
        this.validade = validarValidade(validade);
        if (tentativasMax < 1) {
            throw new IllegalStateException("app.conta.convite.tentativas-max deve ser pelo menos 1.");
        }
        this.tentativasMax = tentativasMax;
    }

    static Duration validarValidade(Duration validade) {
        if (validade.compareTo(VALIDADE_MINIMA) < 0 || validade.compareTo(VALIDADE_MAXIMA) > 0) {
            throw new IllegalStateException("app.conta.convite.validade deve ficar entre 1 hora e 7 dias, mas é " + validade + ".");
        }
        return validade;
    }

    public Duration validade() {
        return validade;
    }

    public int tentativasMax() {
        return tentativasMax;
    }
}
