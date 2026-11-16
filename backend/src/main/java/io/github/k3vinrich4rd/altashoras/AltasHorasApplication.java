package io.github.k3vinrich4rd.altashoras;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API da Barbearia Altas Horas: monólito modular (ADR 0001). Cada subpacote direto é um módulo do Spring Modulith,
 * com fronteiras verificadas pelo ModulithTest e pelas regras do ArquiteturaTest.
 */
@SpringBootApplication
public class AltasHorasApplication {

    public static void main(String[] args) {
        SpringApplication.run(AltasHorasApplication.class, args);
    }
}
