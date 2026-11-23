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
    @DisplayName("A40: o admin só depende de agenda, pagamento, conta e privacidade (pelas APIs públicas); ninguém depende do admin; a agenda não conhece o pagamento")
    void adminSoPelasApisPublicas() {
        modulos.verify();
        assertThat(dependenciasDe("admin")).isSubsetOf("agenda", "pagamento", "conta", "privacidade", "comum").contains("agenda", "pagamento");
        for (String modulo : List.of("comum", "plataforma", "conta", "cep", "agenda", "pagamento", "privacidade")) {
            assertThat(dependenciasDe(modulo)).as("dependências de " + modulo).doesNotContain("admin");
        }
        assertThat(dependenciasDe("agenda")).doesNotContain("pagamento", "admin");
    }

    @Test
    @DisplayName("A71: o admin depende de agenda, pagamento e conta (e, desde a 6B, da porta de retenção da privacidade; de mais nenhum módulo de negócio); os cadastros chegam à agenda só por PainelDaAgenda e à conta só por Contas")
    void cadastrosDoAdminSoPelasApisPublicas() {
        modulos.verify();
        assertThat(dependenciasDe("admin")).contains("agenda", "pagamento", "conta")
                .isSubsetOf("agenda", "pagamento", "conta", "privacidade", "comum");
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
        // Contas (a porta), o convite que ela devolve (10B) e os dois eventos que o painel escuta para auditar: nada de usuario, sessao ou convite.
        assertThat(tiposDaContaUsadosPeloAdmin).containsExactlyInAnyOrder("io.github.k3vinrich4rd.altashoras.conta.Contas",
                "io.github.k3vinrich4rd.altashoras.conta.ConviteEmitido", "io.github.k3vinrich4rd.altashoras.conta.ConviteAceito",
                "io.github.k3vinrich4rd.altashoras.conta.ConviteRevogadoPorTentativas");
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

    @Test
    @DisplayName("A60: a privacidade só depende do comum; conta, agenda, admin e pagamento dependem dela (portas e evento), e ela não conhece nenhum deles")
    void privacidadeNaoDependeDeNinguem() {
        modulos.verify();
        assertThat(dependenciasDe("privacidade")).isSubsetOf("comum")
                .doesNotContain("conta", "agenda", "pagamento", "admin", "cep", "plataforma");
        for (String modulo : List.of("conta", "agenda", "admin", "pagamento")) {
            assertThat(dependenciasDe(modulo)).as("dependências de " + modulo).contains("privacidade");
        }
        // As portas e o evento são o que cruza a fronteira: nenhuma outra classe da privacidade é usada por fora.
        for (String modulo : List.of("conta", "agenda", "admin", "pagamento")) {
            var tipos = modulos.getModuleByName(modulo).orElseThrow().getDirectDependencies(modulos).stream()
                    .filter(d -> d.getTargetModule().getIdentifier().toString().equals("privacidade"))
                    .map(d -> d.getTargetType().getFullName()).distinct().toList();
            assertThat(tipos).as("tipos da privacidade usados por " + modulo).isNotEmpty().allSatisfy(tipo -> assertThat(tipo)
                    .startsWith("io.github.k3vinrich4rd.altashoras.privacidade.").doesNotContain(".titular."));
        }
    }

    @Test
    @DisplayName("6C: a agenda usa a conta só por Contas e o cep só pelas portas CepApi, Endereco e CepIndisponivel; nem a conta nem o cep conhecem a agenda; o admin chega ao alcance só por AlcanceDaAgenda")
    void alcanceUsaContaECepPelasPortas() {
        modulos.verify();
        assertThat(dependenciasDe("agenda")).contains("conta", "cep");
        assertThat(dependenciasDe("conta")).doesNotContain("agenda");
        assertThat(dependenciasDe("cep")).doesNotContain("agenda");
        var tiposDaContaUsadosPelaAgenda = modulos.getModuleByName("agenda").orElseThrow().getDirectDependencies(modulos)
                .stream().filter(d -> d.getTargetModule().getIdentifier().toString().equals("conta"))
                .map(d -> d.getTargetType().getFullName()).distinct().toList();
        // Contas (a porta) e os eventos AdminInicialCriado e ConviteAceito (que a agenda escuta): nada de usuario, sessao ou cadastro.
        assertThat(tiposDaContaUsadosPelaAgenda).contains("io.github.k3vinrich4rd.altashoras.conta.Contas")
                .allSatisfy(tipo -> assertThat(tipo).matches("io\\.github\\.k3vinrich4rd\\.altashoras\\.conta\\.(Contas.*|AdminInicialCriado|ConviteAceito)"));
        var tiposDoCepUsadosPelaAgenda = modulos.getModuleByName("agenda").orElseThrow().getDirectDependencies(modulos)
                .stream().filter(d -> d.getTargetModule().getIdentifier().toString().equals("cep"))
                .map(d -> d.getTargetType().getFullName()).distinct().toList();
        assertThat(tiposDoCepUsadosPelaAgenda).isNotEmpty().allSatisfy(tipo -> assertThat(tipo)
                .startsWith("io.github.k3vinrich4rd.altashoras.cep.").doesNotContain(".consulta."));
        var alcanceUsadoPeloAdmin = modulos.getModuleByName("admin").orElseThrow().getDirectDependencies(modulos)
                .stream().map(d -> d.getTargetType().getFullName()).filter(tipo -> tipo.contains("Alcance")).distinct().toList();
        assertThat(alcanceUsadoPeloAdmin).isNotEmpty().allSatisfy(tipo -> assertThat(tipo)
                .startsWith("io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda").doesNotContain(".alcance."));
    }

    private List<String> dependenciasDe(String modulo) {
        return modulos.getModuleByName(modulo).orElseThrow().getDirectDependencies(modulos).uniqueModules()
                .map(m -> m.getIdentifier().toString()).toList();
    }
}
