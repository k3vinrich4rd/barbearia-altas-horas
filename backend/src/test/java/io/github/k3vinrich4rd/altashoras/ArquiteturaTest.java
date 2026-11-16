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
}
