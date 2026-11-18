package io.github.k3vinrich4rd.altashoras.cep;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/**
 * Configuração única dos testes do CEP: todos dividem o mesmo contexto (e o mesmo container do PostgreSQL). O teto do
 * cache fica em 3 entradas só aqui (A12); o padrão de produção, 10 000, é conferido no A12 pelo binding de CepProps.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "app.cep.cache-max=3")
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, CepDeTeste.class})
public @interface CepTeste {
}
