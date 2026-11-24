package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.Canal;

/**
 * Os dois canais falsos dos testes. Passam na frente dos reais na ordem do Spring (o Despachante usa o primeiro de cada
 * canal): o Web Push e o e-mail de verdade nunca são chamados nos testes de integração.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CanaisFalsos {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    CanalFalso falsoWebPush() {
        return new CanalFalso(Canal.WEBPUSH, true);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    CanalFalso falsoEmail() {
        return new CanalFalso(Canal.EMAIL, false);
    }
}
