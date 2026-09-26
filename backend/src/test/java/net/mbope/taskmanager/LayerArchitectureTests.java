package net.mbope.taskmanager;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LayerArchitectureTests {
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("net.mbope.taskmanager");

    @Test
    void domainDoesNotDependOnFrameworksOrOuterLayers() {
        noClasses().that().resideInAPackage("..internal.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "org.hibernate..", "jakarta..",
                        "..internal.application..", "..internal.infrastructure..", "..internal.presentation..")
                .check(CLASSES);
    }

    @Test
    void applicationDoesNotDependOnInfrastructureOrPresentation() {
        noClasses().that().resideInAPackage("..internal.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..internal.infrastructure..", "..internal.presentation..", "org.springframework.data..",
                        "org.springframework.security..", "org.hibernate..", "jakarta.persistence..")
                .check(CLASSES);
    }

    @Test
    void presentationUsesUseCasesRatherThanDomainOrPersistence() {
        noClasses().that().resideInAPackage("..internal.presentation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..internal.infrastructure..", "..internal.domain..", "..internal.application.port..")
                .check(CLASSES);
    }
}
