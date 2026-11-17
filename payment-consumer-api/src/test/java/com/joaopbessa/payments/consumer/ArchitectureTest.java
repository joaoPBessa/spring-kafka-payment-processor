package com.joaopbessa.payments.consumer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import jakarta.persistence.Entity;

@DisplayName("Architecture rules (payment-consumer-api)")
class ArchitectureTest {

    private static final String BASE = "com.joaopbessa.payments.consumer";
    private static final String API = BASE + ".api..";
    private static final String CONFIG = BASE + ".config..";
    private static final String CONTROLLERS = BASE + ".controllers..";
    private static final String DLT = BASE + ".dlt..";
    private static final String DOMAIN = BASE + ".domain..";
    private static final String LISTENERS = BASE + ".listeners..";
    private static final String MAPPERS = BASE + ".mappers..";
    private static final String METRICS = BASE + ".metrics..";
    private static final String REPOSITORIES = BASE + ".repositories..";
    private static final String SERVICES = BASE + ".services..";

    private static JavaClasses consumerClasses;

    @BeforeAll
    static void importConsumerClasses() {
        consumerClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    @Test
    @DisplayName("Should have no cycles between packages")
    void shouldHaveNoCyclesBetweenPackages() {
        slices().matching(BASE + ".(**)").should().beFreeOfCycles().check(consumerClasses);
    }

    @Test
    @DisplayName("Should keep the dead letter package independent of the configuration and the API")
    void shouldKeepTheDeadLetterPackageIndependentOfTheConfigurationAndTheApi() {
        noClasses().that().resideInAPackage(DLT)
                .should().dependOnClassesThat().resideInAnyPackage(CONFIG, API)
                .check(consumerClasses);
    }

    @Test
    @DisplayName("Should keep the payment pipeline independent of the web layer and the dead letter package")
    void shouldKeepThePaymentPipelineIndependentOfTheWebLayerAndTheDeadLetterPackage() {
        noClasses().that().resideInAnyPackage(DOMAIN, REPOSITORIES, MAPPERS, SERVICES, LISTENERS)
                .should().dependOnClassesThat().resideInAnyPackage(API, DLT, CONTROLLERS, CONFIG)
                .check(consumerClasses);
    }

    @Test
    @DisplayName("Should keep the metrics independent of the rest of the application")
    void shouldKeepTheMetricsIndependentOfTheRestOfTheApplication() {
        noClasses().that().resideInAPackage(METRICS)
                .should().dependOnClassesThat().resideInAnyPackage(
                        API, CONFIG, CONTROLLERS, DLT, DOMAIN, LISTENERS, MAPPERS, REPOSITORIES, SERVICES)
                .check(consumerClasses);
    }

    @Test
    @DisplayName("Should not let anything depend on the controllers")
    void shouldNotLetAnythingDependOnTheControllers() {
        noClasses().that().resideOutsideOfPackage(CONTROLLERS)
                .should().dependOnClassesThat().resideInAPackage(CONTROLLERS)
                .check(consumerClasses);
    }

    @Test
    @DisplayName("Should keep the REST controllers and their advice in the controllers package")
    void shouldKeepTheRestControllersAndTheirAdviceInTheControllersPackage() {
        classes().that().areAnnotatedWith(RestController.class).or().areAnnotatedWith(RestControllerAdvice.class)
                .should().resideInAPackage(CONTROLLERS)
                .check(consumerClasses);
    }

    @Test
    @DisplayName("Should keep the JPA entities in the domain package")
    void shouldKeepTheJpaEntitiesInTheDomainPackage() {
        classes().that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage(DOMAIN)
                .check(consumerClasses);
    }

    @Test
    @DisplayName("Should use constructor injection only")
    void shouldUseConstructorInjectionOnly() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(consumerClasses);
    }

    @Test
    @DisplayName("Should not write to the standard streams")
    void shouldNotWriteToTheStandardStreams() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(consumerClasses);
    }

}
