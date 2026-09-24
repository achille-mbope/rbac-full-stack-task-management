package net.mbope.taskmanager.user.internal;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class UserArchitectureTests {
    @Test
    void domainDoesNotDependOnFrameworksOrOuterLayers() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.mbope.taskmanager.user");
        noClasses().that().resideInAPackage("..internal.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "org.hibernate..", "jakarta..",
                        "..internal.application..", "..internal.infrastructure..").check(classes);
    }

    @Test
    void applicationDoesNotDependOnInfrastructureOrPersistenceFrameworks() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.mbope.taskmanager.user");
        noClasses().that().resideInAPackage("..internal.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..internal.infrastructure..", "org.springframework.data..",
                        "org.springframework.security..", "org.hibernate..", "jakarta.persistence..").check(classes);
    }
}
