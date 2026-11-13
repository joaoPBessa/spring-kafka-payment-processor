package com.joaopbessa.payments.producer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import jakarta.persistence.Entity;

@DisplayName("Architecture rules (payment-producer-api)")
class ArchitectureTest {

    private static final String BASE = "com.joaopbessa.payments.producer";
    private static final String API = BASE + ".api..";
    private static final String CONFIG = BASE + ".config..";
    private static final String CONTROLLERS = BASE + ".controllers..";
    private static final String DOMAIN = BASE + ".domain..";
    private static final String REPOSITORIES = BASE + ".repositories..";
    private static final String SERVICES = BASE + ".services..";

    private static JavaClasses producerClasses;

    @BeforeAll
    static void importProducerClasses() {
        producerClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    @Test
    @DisplayName("Should have no cycles between packages")
    void shouldHaveNoCyclesBetweenPackages() {
        slices().matching(BASE + ".(**)").should().beFreeOfCycles().check(producerClasses);
    }

    @Test
    @DisplayName("Should keep the repositories independent of the API, the services and the controllers")
    void shouldKeepTheRepositoriesIndependentOfTheApiTheServicesAndTheControllers() {
        noClasses().that().resideInAPackage(REPOSITORIES)
                .should().dependOnClassesThat().resideInAnyPackage(API, SERVICES, CONTROLLERS)
                .check(producerClasses);
    }

    @Test
    @DisplayName("Should keep the domain independent of every other layer")
    void shouldKeepTheDomainIndependentOfEveryOtherLayer() {
        noClasses().that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(API, SERVICES, REPOSITORIES, CONTROLLERS, CONFIG)
                .check(producerClasses);
    }

    @Test
    @DisplayName("Should not let anything depend on the controllers")
    void shouldNotLetAnythingDependOnTheControllers() {
        noClasses().that().resideOutsideOfPackage(CONTROLLERS)
                .should().dependOnClassesThat().resideInAPackage(CONTROLLERS)
                .check(producerClasses);
    }

    @Test
    @DisplayName("Should let only the controllers use the services")
    void shouldLetOnlyTheControllersUseTheServices() {
        classes().that().resideInAPackage(SERVICES)
                .should().onlyHaveDependentClassesThat().resideInAnyPackage(CONTROLLERS, SERVICES)
                .check(producerClasses);
    }

    @Test
    @DisplayName("Should keep the REST controllers in the controllers package")
    void shouldKeepTheRestControllersInTheControllersPackage() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage(CONTROLLERS)
                .check(producerClasses);
    }

    @Test
    @DisplayName("Should keep the JPA entities in the domain package")
    void shouldKeepTheJpaEntitiesInTheDomainPackage() {
        classes().that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage(DOMAIN)
                .check(producerClasses);
    }

    @Test
    @DisplayName("Should use constructor injection only")
    void shouldUseConstructorInjectionOnly() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(producerClasses);
    }

    @Test
    @DisplayName("Should not write to the standard streams")
    void shouldNotWriteToTheStandardStreams() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(producerClasses);
    }

}
