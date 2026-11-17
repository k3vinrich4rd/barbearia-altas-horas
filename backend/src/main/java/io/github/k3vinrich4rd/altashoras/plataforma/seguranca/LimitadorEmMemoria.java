package io.github.k3vinrich4rd.altashoras.plataforma.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.plataforma.LimitadorDeTentativas;

/**
 * Janela deslizante em memória: guarda o instante de cada tentativa e descarta as que saíram da janela.
 * Limite conhecido: reiniciar a API zera os contadores (aceito no beta, uma instância só).
 */
@Component
class LimitadorEmMemoria implements LimitadorDeTentativas {

    private final Map<String, Deque<Instant>> tentativas = new ConcurrentHashMap<>();
    private final Clock clock;

    LimitadorEmMemoria(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long segundosBloqueado(String chave, int limite, Duration janela) {
        Instant agora = clock.instant();
        Deque<Instant> fila = tentativas.get(chave);
        if (fila == null) {
            return 0;
        }
        synchronized (fila) {
            descartarAntigas(fila, agora.minus(janela));
            if (fila.size() < limite) {
                return 0;
            }
            // Libera quando a tentativa mais antiga sair da janela.
            long segundos = Duration.between(agora, fila.peekFirst().plus(janela)).toSeconds();
            return Math.max(1, segundos);
        }
    }

    @Override
    public void registrar(String chave, Duration janela) {
        Instant agora = clock.instant();
        Deque<Instant> fila = tentativas.computeIfAbsent(chave, c -> new ArrayDeque<>());
        synchronized (fila) {
            descartarAntigas(fila, agora.minus(janela));
            fila.addLast(agora);
        }
    }

    @Override
    public void zerar(String chave) {
        tentativas.remove(chave);
    }

    private static void descartarAntigas(Deque<Instant> fila, Instant limite) {
        while (!fila.isEmpty() && !fila.peekFirst().isAfter(limite)) {
            fila.removeFirst();
        }
    }
}
