package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeLimite;
import io.github.k3vinrich4rd.altashoras.plataforma.LimitadorDeTentativas;

/**
 * {@code GET /alcance}, pública (E35, D-9): o nome e a quantidade de atendimentos de cada cidade e de cada bairro (sem limite
 * mínimo, D149), dos últimos 12 meses. O cálculo fica 1 hora em cache (uma única entrada) e o tempo do cache é o do {@link Ticker}, que o
 * teste troca por um falso: sem {@code Thread.sleep}. Quem passa do limite por IP (30 por minuto, o mesmo mecanismo do CEP)
 * recebe 429 com {@code Retry-After}, antes de olhar o cache. O resultado não tem dado pessoal: pode ser cacheado também
 * pelo navegador por pouco tempo.
 */
@RestController
class AlcancePublicoController {

    private static final String CHAVE = "vitrine";

    private final AlcanceDaAgenda alcance;
    private final LimitadorDeTentativas limitador;
    private final Cache<String, AlcanceDaAgenda.Publico> cache;
    private final int limite;
    private final Duration janela;

    AlcancePublicoController(AlcanceDaAgenda alcance, LimitadorDeTentativas limitador, Ticker ticker,
            @Value("${app.alcance.cache-ttl:1h}") Duration ttl, @Value("${app.limites.alcance-max:30}") int limite,
            @Value("${app.limites.alcance-janela:1m}") Duration janela) {
        this.alcance = alcance;
        this.limitador = limitador;
        this.cache = Caffeine.newBuilder().ticker(ticker).expireAfterWrite(ttl).maximumSize(1).build();
        this.limite = limite;
        this.janela = janela;
    }

    @GetMapping("/alcance")
    ResponseEntity<AlcanceDaAgenda.Publico> vitrine(HttpServletRequest request) {
        String chave = "alcance|" + request.getRemoteAddr();
        long bloqueado = limitador.segundosBloqueado(chave, limite, janela);
        if (bloqueado > 0) {
            throw new ExcecaoDeLimite(bloqueado);
        }
        limitador.registrar(chave, janela);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(cache.get(CHAVE, k -> alcance.publico()));
    }

    /** Para os testes começarem sem o resultado de outro teste na memória. */
    void esquecer() {
        cache.invalidateAll();
        cache.cleanUp();
    }
}
