package com.velan.production.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.velan.production",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringTests {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_application_or_infrastructure =
            noClasses()
                    .that().resideInAPackage("..internal.domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..internal.application..", "..internal.infrastructure..")
                    .because("Domain layer must be isolated from application and infrastructure");

    @ArchTest
    static final ArchRule application_must_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("..internal.application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..internal.infrastructure..")
                    .because("Application layer must not depend on infrastructure");

    @ArchTest
    static final ArchRule api_must_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("com.velan.production.*.api..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..internal.infrastructure..")
                    .because("Module API must not depend on infrastructure");
}
