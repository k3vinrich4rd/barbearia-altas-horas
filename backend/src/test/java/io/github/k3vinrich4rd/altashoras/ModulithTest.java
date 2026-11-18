package io.github.k3vinrich4rd.altashoras;

import java.util.List;

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

    @Test
    @DisplayName("A40: o admin só depende de agenda, pagamento e conta (pelas APIs públicas); ninguém depende do admin; a agenda não conhece o pagamento")
    void adminSoPelasApisPublicas() {
        modulos.verify();
        assertThat(dependenciasDe("admin")).isSubsetOf("agenda", "pagamento", "conta", "comum").contains("agenda", "pagamento");
        for (String modulo : List.of("comum", "plataforma", "conta", "cep", "agenda", "pagamento", "privacidade")) {
            assertThat(dependenciasDe(modulo)).as("dependências de " + modulo).doesNotContain("admin");
        }
        assertThat(dependenciasDe("agenda")).doesNotContain("pagamento", "admin");
    }

    @Test
    @DisplayName("A71: o admin depende de agenda, pagamento e conta (e de mais nenhum módulo de negócio); os cadastros chegam à agenda só por PainelDaAgenda e à conta só por Contas")
    void cadastrosDoAdminSoPelasApisPublicas() {
        modulos.verify();
        assertThat(dependenciasDe("admin")).contains("agenda", "pagamento", "conta")
                .isSubsetOf("agenda", "pagamento", "conta", "comum");
        // Nos módulos agenda e conta só o pacote raiz é API pública (ADR 0002): o Modulith recusa qualquer outra classe.
        var tiposDaAgendaUsadosPeloAdmin = modulos.getModuleByName("admin").orElseThrow().getDirectDependencies(modulos)
                .stream().filter(d -> d.getTargetModule().getIdentifier().toString().equals("agenda"))
                .map(d -> d.getTargetType().getFullName()).distinct().toList();
        assertThat(tiposDaAgendaUsadosPeloAdmin).isNotEmpty()
                .allSatisfy(tipo -> assertThat(tipo).startsWith("io.github.k3vinrich4rd.altashoras.agenda.")
                        .doesNotContain(".catalogo.").doesNotContain(".disponibilidade.").doesNotContain(".reserva."));
        var tiposDaContaUsadosPeloAdmin = modulos.getModuleByName("admin").orElseThrow().getDirectDependencies(modulos)
                .stream().filter(d -> d.getTargetModule().getIdentifier().toString().equals("conta"))
                .map(d -> d.getTargetType().getFullName()).distinct().toList();
        assertThat(tiposDaContaUsadosPeloAdmin).containsExactly("io.github.k3vinrich4rd.altashoras.conta.Contas");
    }

    @Test
    @DisplayName("A21: a conta depende do cep, e nada do cep depende da conta (nem do admin, da agenda ou do pagamento)")
    void contaUsaOCepECepNaoUsaAConta() {
        modulos.verify();
        assertThat(dependenciasDe("conta")).contains("cep");
        assertThat(dependenciasDe("cep")).isSubsetOf("comum", "plataforma")
                .doesNotContain("conta", "agenda", "pagamento", "privacidade", "admin");
        var tiposDoCepUsadosPelaConta = modulos.getModuleByName("conta").orElseThrow().getDirectDependencies(modulos)
                .stream().filter(d -> d.getTargetModule().getIdentifier().toString().equals("cep"))
                .map(d -> d.getTargetType().getFullName()).distinct().toList();
        assertThat(tiposDoCepUsadosPelaConta).isNotEmpty().allSatisfy(tipo -> assertThat(tipo)
                .startsWith("io.github.k3vinrich4rd.altashoras.cep.").doesNotContain(".consulta."));
    }

    private List<String> dependenciasDe(String modulo) {
        return modulos.getModuleByName(modulo).orElseThrow().getDirectDependencies(modulos).uniqueModules()
                .map(m -> m.getIdentifier().toString()).toList();
    }
}
