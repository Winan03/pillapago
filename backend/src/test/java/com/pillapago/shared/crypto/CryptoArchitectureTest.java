package com.pillapago.shared.crypto;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class CryptoArchitectureTest {

    @Test
    @DisplayName("Centralización Tink: Ninguna clase fuera de com.pillapago.shared.crypto.. puede acceder a Google Tink")
    void googleTinkMustBeCentralizedInSharedCrypto() {
        JavaClasses importedClasses = new ClassFileImporter()
                .importPackages("com.pillapago..");

        ArchRule tinkCentralizationRule = noClasses()
                .that()
                .resideOutsideOfPackages(
                        "com.pillapago.shared.crypto..",
                        "com.pillapago..*Test*",
                        "com.pillapago..*Tests*"
                )
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.google.crypto.tink..")
                .because("Todo el cifrado de campo con Google Tink debe residir exclusivamente en com.pillapago.shared.crypto (RNF-31 / ENC)");

        tinkCentralizationRule.check(importedClasses);
    }

    @Test
    @DisplayName("Aislamiento de Negocio: Los módulos de negocio no deben usar directamente primitivas de cifrado javax.crypto ni Tink")
    void businessModulesMustNotDependDirectlyOnLowLevelCrypto() {
        JavaClasses importedClasses = new ClassFileImporter()
                .importPackages("com.pillapago..");

        ArchRule businessIsolationRule = noClasses()
                .that()
                .resideInAnyPackage(
                        "com.pillapago.evaluation..",
                        "com.pillapago.rules..",
                        "com.pillapago.lists..",
                        "com.pillapago.chargeback..",
                        "com.pillapago.cases..",
                        "com.pillapago.tenant..",
                        "com.pillapago.audit..",
                        "com.pillapago.messaging.."
                )
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.google.crypto.tink..",
                        "javax.crypto.."
                )
                .because("Los módulos de negocio deben delegar cualquier operación criptográfica en shared/crypto y nunca invocar cifrado por su cuenta");

        businessIsolationRule.check(importedClasses);
    }
}
