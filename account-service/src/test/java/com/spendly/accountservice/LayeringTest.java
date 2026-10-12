package com.spendly.accountservice;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

class LayeringTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.spendly.accountservice");

    @Test
    void layersPointOneWayOnly() {
        ArchRule rule = Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("api")
                .definedBy("com.spendly.accountservice.api..")
                .layer("service")
                .definedBy("com.spendly.accountservice.service..")
                .layer("repository")
                .definedBy("com.spendly.accountservice.repository..")
                .layer("domain")
                .definedBy("com.spendly.accountservice.domain..")
                .layer("messaging")
                .definedBy("com.spendly.accountservice.messaging..")
                .whereLayer("api")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("messaging")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("service")
                .mayOnlyBeAccessedByLayers("api", "messaging")
                .whereLayer("repository")
                .mayOnlyBeAccessedByLayers("service");

        rule.check(CLASSES);
    }

    @Test
    void entitiesNeverCrossTheHttpBoundary() {
        ArchRule rule = ArchRuleDefinition.noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("com.spendly.accountservice.api..")
                .and()
                .arePublic()
                .should()
                .haveRawReturnType(com.spendly.accountservice.domain.Account.class)
                .because("controllers return DTO records, never entities (CLAUDE.md 7)");

        rule.check(CLASSES);
    }

    @Test
    void repositoriesAreOnlyReachedFromTheServiceLayer() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.spendly.accountservice.api..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.spendly.accountservice.repository..")
                .because("a controller that reaches a repository skips the transaction boundary (CLAUDE.md 6.15)");

        rule.check(CLASSES);
    }

    @Test
    void publicServiceMethodsCarryATransactionBoundary() {
        ArchRule rule = ArchRuleDefinition.classes()
                .that()
                .resideInAPackage("com.spendly.accountservice.service..")
                .and()
                .areAnnotatedWith(org.springframework.stereotype.Service.class)
                .should()
                .beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                .because("a service method without a transaction commits per repository call (CLAUDE.md 6.15)");

        rule.check(CLASSES);
    }

    @Test
    void fieldInjectionIsNeverUsed() {
        ArchRule rule = ArchRuleDefinition.noFields()
                .should()
                .beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
                .because("constructor injection only (CLAUDE.md 12)");

        rule.check(CLASSES);
    }

    @Test
    void noCredentialFieldSurvivesOnAnEntity() {
        ArchRule rule = ArchRuleDefinition.noFields()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("com.spendly.accountservice.domain..")
                .should()
                .haveNameMatching("(?i).*(password|secret|credential|passwordHash).*")
                .because("Only auth-service stores credentials (CLAUDE.md 8)");

        rule.check(CLASSES);
    }
}
