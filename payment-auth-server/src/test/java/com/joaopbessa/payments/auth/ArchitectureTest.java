package com.joaopbessa.payments.auth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.joaopbessa.payments.auth.config.AuthServerProperties;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import jakarta.persistence.Entity;

@DisplayName("Architecture rules (payment-auth-server)")
class ArchitectureTest {

    private static final String BASE = "com.joaopbessa.payments.auth";
    private static final String CONFIG = BASE + ".config..";
    private static final String DOMAIN = BASE + ".domain..";
    private static final String REPOSITORIES = BASE + ".repositories..";
    private static final String SECURITY = BASE + ".security..";
    private static final String TOKENS = BASE + ".tokens..";

    private static JavaClasses authClasses;

    @BeforeAll
    static void importAuthClasses() {
        authClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    @Test
    @DisplayName("Should have no cycles between packages")
    void shouldHaveNoCyclesBetweenPackages() {
        slices().matching(BASE + ".(**)").should().beFreeOfCycles().check(authClasses);
    }

    @Test
    @DisplayName("Should keep the domain and the repositories independent of security, tokens and configuration")
    void shouldKeepTheDomainAndTheRepositoriesIndependent() {
        noClasses().that().resideInAnyPackage(DOMAIN, REPOSITORIES)
                .should().dependOnClassesThat().resideInAnyPackage(SECURITY, TOKENS, CONFIG)
                .check(authClasses);
    }

    @Test
    @DisplayName("Should keep the user lookup independent of the token and configuration code")
    void shouldKeepTheUserLookupIndependentOfTokensAndConfiguration() {
        noClasses().that().resideInAPackage(SECURITY)
                .should().dependOnClassesThat().resideInAnyPackage(TOKENS, CONFIG)
                .check(authClasses);
    }

    @Test
    @DisplayName("Should keep the token claims independent of the users, the repositories and the configuration")
    void shouldKeepTheTokenClaimsIndependent() {
        noClasses().that().resideInAPackage(TOKENS)
                .should().dependOnClassesThat().resideInAnyPackage(DOMAIN, REPOSITORIES, SECURITY, CONFIG)
                .check(authClasses);
    }

    @Test
    @DisplayName("Should not let any code outside the configuration read the settings that hold client secrets")
    void shouldKeepTheClientSecretsInsideTheConfiguration() {
        noClasses().that().resideOutsideOfPackage(CONFIG)
                .should().dependOnClassesThat().haveFullyQualifiedName(AuthServerProperties.class.getName())
                .check(authClasses);
    }

    @Test
    @DisplayName("Should keep the JPA entities in the domain package")
    void shouldKeepTheJpaEntitiesInTheDomainPackage() {
        classes().that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage(DOMAIN)
                .check(authClasses);
    }

    @Test
    @DisplayName("Should use constructor injection only")
    void shouldUseConstructorInjectionOnly() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(authClasses);
    }

    @Test
    @DisplayName("Should not write to the standard streams")
    void shouldNotWriteToTheStandardStreams() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(authClasses);
    }

}
