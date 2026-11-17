package io.github.k3vinrich4rd.altashoras;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/** A10: os 8 módulos existem e nenhum depende de outro por fora da API pública (ADR 0001, ADR 0002). */
class ModulithTest {

    private final ApplicationModules modulos = ApplicationModules.of(AltasHorasApplication.class);

    @Test
    @DisplayName("A10: os 8 módulos do desenho existem")
    void oitoModulos() {
        assertThat(modulos.stream().map(m -> m.getIdentifier().toString()))
                .containsExactlyInAnyOrder("comum", "plataforma", "conta", "cep", "agenda", "pagamento",
                        "privacidade", "admin");
    }

    @Test
    @DisplayName("A10: ApplicationModules.verify() sem ciclo nem acesso a pacote interno")
    void fronteirasRespeitadas() {
        modulos.verify();
    }
}
