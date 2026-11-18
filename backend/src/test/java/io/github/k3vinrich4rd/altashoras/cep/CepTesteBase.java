package io.github.k3vinrich4rd.altashoras.cep;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.ip;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.ipNovo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;
import io.github.k3vinrich4rd.altashoras.cep.CepDeTeste.TickerFalso;

/**
 * Começo e fim iguais para os testes do CEP: expectativas, cache, relógio do cache e relógio do limite zerados antes de
 * cada teste, e conferência de que toda chamada esperada aos provedores aconteceu.
 */
public abstract class CepTesteBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ProvedoresFalsos provedores;

    @Autowired
    protected TickerFalso ticker;

    @Autowired
    protected CacheManager caches;

    @Autowired
    protected RelogioAjustavel relogio;

    @BeforeEach
    public void zerarProvedoresECaches() {
        provedores.reiniciar();
        ticker.reiniciar();
        caches.getCacheNames().forEach(nome -> caches.getCache(nome).clear());
        relogio.reiniciar();
    }

    @AfterEach
    public void conferirProvedores() {
        provedores.verificar();
        relogio.reiniciar();
    }

    /** Consulta de um IP novo a cada chamada: o limite de um teste não atrapalha o outro. */
    protected ResultActions consultar(String cep) throws Exception {
        return consultarDo(cep, ipNovo());
    }

    protected ResultActions consultarDo(String cep, String deOnde) throws Exception {
        return mvc.perform(get(URI.create("/cep/" + cep)).with(ip(deOnde)));
    }
}
