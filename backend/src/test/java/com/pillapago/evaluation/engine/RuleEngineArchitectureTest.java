package com.pillapago.evaluation.engine;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class RuleEngineArchitectureTest {

    @Test
    @DisplayName("El paquete evaluation.engine debe ser 100% Java puro y no depender de Spring, JPA ni Hibernate")
    void enginePackageMustNotDependOnSpringOrJpa() {
        JavaClasses importedClasses = new ClassFileImporter()
                .importPackages("com.pillapago.evaluation.engine..");

        ArchRule pureJavaRule = noClasses()
                .that()
                .resideInAPackage("com.pillapago.evaluation.engine..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "javax.persistence..",
                        "org.hibernate.."
                )
                .because("El motor de reglas de Pillapago debe ser Java 21 puro para garantizar máxima portabilidad y velocidad");

        pureJavaRule.check(importedClasses);
    }
}
