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

    @Test
    @DisplayName("A44: agenda e pagamento só se falam pela API pública e por eventos; a agenda não depende do pagamento")
    void agendaEPagamentoSoPelaApiPublica() {
        modulos.verify();
        assertThat(modulos.getModuleByName("agenda").orElseThrow().getDirectDependencies(modulos).uniqueModules()
                .map(m -> m.getIdentifier().toString())).doesNotContain("pagamento");
        assertThat(modulos.getModuleByName("pagamento").orElseThrow().getDirectDependencies(modulos).uniqueModules()
                .map(m -> m.getIdentifier().toString())).contains("agenda");
    }

    @Test
    @DisplayName("A39: conta, plataforma, privacidade e agenda só se falam pela API pública e por eventos")
    void contaNaoAtravessaFronteiras() {
        modulos.verify();
        assertThat(modulos.getModuleByName("conta").orElseThrow().getDirectDependencies(modulos).uniqueModules()
                .map(m -> m.getIdentifier().toString())).doesNotContain("agenda", "pagamento", "admin");
    }
}
