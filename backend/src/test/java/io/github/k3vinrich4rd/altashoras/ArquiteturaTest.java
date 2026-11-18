package io.github.k3vinrich4rd.altashoras;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A10: regras de arquitetura que o compilador não pega. */
class ArquiteturaTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.k3vinrich4rd.altashoras");

    @Test
    @DisplayName("A10: ninguém lê o relógio do sistema direto; o tempo vem do Clock injetado (ADR 0004)")
    void tempoSoPeloClock() {
        noClasses().that().resideOutsideOfPackage("..plataforma.tempo..")
                .should().callMethod(Instant.class, "now")
                .orShould().callMethod(LocalDate.class, "now")
                .orShould().callMethod(LocalDateTime.class, "now")
                .orShould().callMethod(OffsetDateTime.class, "now")
                .orShould().callMethod(ZonedDateTime.class, "now")
                .orShould().callMethod(System.class, "currentTimeMillis")
                .because("o relógio injetado deixa testar a expiração de 15 minutos sem esperar")
                .check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0011: o núcleo da regra (Calculadora*) não conhece Spring, JPA nem JDBC")
    void nucleoDaRegraSemFramework() {
        noClasses().that().haveSimpleNameStartingWith("Calculadora")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..",
                        "java.sql..")
                .because("regra pura se testa em milissegundos e sobrevive a troca de framework")
                .check(CLASSES);
    }

    @Test
    @DisplayName("A44: a porta do gateway e a máquina de estados da reserva não conhecem Spring, JPA nem Hibernate")
    void portaEEstadosSemFramework() {
        noClasses().that().haveSimpleName("GatewayDePagamento").or().haveSimpleName("StatusReserva")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..")
                .because("a regra de estados e a porta do gateway são código puro (ADR 0011): trocar o provedor ou o "
                        + "framework não as toca")
                .check(CLASSES);
    }

    @Test
    @DisplayName("A10: dinheiro nunca em double ou float (ADR 0004)")
    void dinheiroSemPontoFlutuante() {
        noFields().that().haveNameMatching("(?i).*(preco|valor|centavos).*")
                .should().haveRawType(double.class)
                .orShould().haveRawType(float.class)
                .orShould().haveRawType(Double.class)
                .orShould().haveRawType(Float.class)
                .because("dinheiro é long em centavos")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("A71: os cadastros do admin chamam a agenda só por PainelDaAgenda e a conta só por Contas; não usam repositório, entidade nem SQL alheios")
    void cadastrosDoAdminSoPelaApiPublica() {
        noClasses().that().resideInAPackage("..admin.cadastro..")
                .should().dependOnClassesThat().resideInAnyPackage("..agenda.catalogo..", "..agenda.disponibilidade..",
                        "..agenda.reserva..", "..conta.usuario..", "..conta.sessao..", "..conta.cadastro..",
                        "..conta.admin..", "..pagamento..")
                .because("o catálogo, a jornada, a folga e a configuração são da agenda, e a conta é da conta (ADR 0002)")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..admin.cadastro..")
                .should().dependOnClassesThat(com.tngtech.archunit.base.DescribedPredicate.describe(
                        "classes da agenda que não são o PainelDaAgenda",
                        classe -> classe.getPackageName().equals("io.github.k3vinrich4rd.altashoras.agenda")
                                && !classe.getName().startsWith("io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda")))
                .because("PainelDaAgenda é a única porta do painel para os cadastros da agenda")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..admin.cadastro..")
                .should().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManager")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient")
                .check(CLASSES);
    }

    @Test
    @DisplayName("A21: o módulo cep só expõe CepApi, Endereco e CepIndisponivel; os clientes dos provedores, o controller e o cache não são acessíveis de fora")
    void cepSoExpoeAPortaEOsTiposDela() {
        var publicas = CLASSES.stream().filter(c -> c.getPackageName().equals("io.github.k3vinrich4rd.altashoras.cep"))
                .filter(c -> !c.getSimpleName().equals("package-info"))
                .map(c -> c.getSimpleName()).toList();
        org.assertj.core.api.Assertions.assertThat(publicas)
                .containsExactlyInAnyOrder("CepApi", "Endereco", "Fonte", "CepIndisponivel");
        noClasses().that().resideOutsideOfPackage("..cep.consulta..")
                .should().dependOnClassesThat().resideInAPackage("..cep.consulta..")
                .because("os clientes ViaCEP e BrasilAPI, o cache e o controller são detalhes do módulo cep (ADR 0002)")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..cep..")
                .should().dependOnClassesThat().resideInAnyPackage("..conta..", "..agenda..", "..pagamento..",
                        "..privacidade..", "..admin..")
                .because("o cep não conhece quem o usa")
                .check(CLASSES);
    }

    @Test
    @DisplayName("A40: ninguém depende do módulo admin, e o admin só usa JdbcClient, JdbcTemplate ou EntityManager na própria auditoria")
    void adminIsoladoDasTabelasAlheias() {
        noClasses().that().resideOutsideOfPackage("..admin..")
                .should().dependOnClassesThat().resideInAPackage("..admin..")
                .because("o painel chama os donos; nenhum dono chama o painel (ADR 0002)")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..admin..").and().resideOutsideOfPackage("..admin.auditoria..")
                .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManager")
                .because("o admin só enxerga SQL de auditoria_admin; as outras tabelas são dos donos (ADR 0002)")
                .check(CLASSES);
    }
}
