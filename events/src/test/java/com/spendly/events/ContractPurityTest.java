package com.spendly.events;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * Guards the one property that makes this module reusable: it is a contract library, not an application. The moment a
 * Spring or persistence type leaks in, every consumer inherits it.
 */
class ContractPurityTest {

    private static final JavaClasses EVENTS = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.spendly.events");

    @Test
    void noFrameworkDependencies() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "javax..",
                        "com.fasterxml.jackson..",
                        "org.hibernate..",
                        "org.apache.kafka..",
                        "org.slf4j..")
                .because("events is a contract-only module and must not carry a framework (FI-8)");

        rule.check(EVENTS);
    }

    @Test
    void eventsAreImmutableRecords() {
        ArchRule rule = ArchRuleDefinition.classes()
                .that()
                .implement(DomainEvent.class)
                .should()
                .beRecords()
                .because("event payloads are values, not mutable beans");

        rule.check(EVENTS);
    }

    @Test
    void noMutableStaticState() {
        ArchRule rule = ArchRuleDefinition.fields()
                .that()
                .areStatic()
                .should()
                .beFinal()
                .because("a shared contract module must be safe to use from any thread");

        rule.check(EVENTS);
    }
}
