package com.spendly.transactionservice;

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
            .importPackages("com.spendly.transactionservice");

    @Test
    void layersPointOneWayOnly() {
        ArchRule rule = Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("api")
                .definedBy("com.spendly.transactionservice.api..")
                .layer("service")
                .definedBy("com.spendly.transactionservice.service..")
                .layer("repository")
                .definedBy("com.spendly.transactionservice.repository..")
                .layer("domain")
                .definedBy("com.spendly.transactionservice.domain..")
                .whereLayer("api")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("service")
                .mayOnlyBeAccessedByLayers("api")
                .whereLayer("repository")
                .mayOnlyBeAccessedByLayers("service");

        rule.check(CLASSES);
    }

    @Test
    void entitiesNeverCrossTheHttpBoundary() {
        ArchRule rule = ArchRuleDefinition.noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("com.spendly.transactionservice.api..")
                .and()
                .arePublic()
                .should()
                .haveRawReturnType(com.spendly.transactionservice.domain.Transaction.class)
                .because("controllers return DTO records, never entities (CLAUDE.md 7)");

        rule.check(CLASSES);
    }

    @Test
    void repositoriesAreOnlyReachedFromTheServiceLayer() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.spendly.transactionservice.api..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.spendly.transactionservice.repository..")
                .because("a controller that reaches a repository skips the transaction boundary (CLAUDE.md 6.15)");

        rule.check(CLASSES);
    }

    @Test
    void publicServiceMethodsCarryATransactionBoundary() {
        ArchRule rule = ArchRuleDefinition.classes()
                .that()
                .resideInAPackage("com.spendly.transactionservice.service..")
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
                .resideInAPackage("com.spendly.transactionservice.domain..")
                .should()
                .haveNameMatching("(?i).*(password|secret|credential|passwordHash).*")
                .because("Spendly never stores credentials; Keycloak owns them (CLAUDE.md 8)");

        rule.check(CLASSES);
    }
}
