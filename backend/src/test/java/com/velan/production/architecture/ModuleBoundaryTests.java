package com.velan.production.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.velan.production",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTests {

    private static final String[] MODULES = {
            "iam", "order", "engineering", "execution",
            "quality", "qcexception", "vendor", "notification", "integration"
    };

    @ArchTest
    void modules_must_not_import_other_modules_internal_packages(JavaClasses classes) {
        for (String module : MODULES) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.velan.production." + module + "..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(otherInternalPackages(module))
                    .because("Modules may only communicate through another module's api package");
            rule.check(classes);
        }
    }

    @ArchTest
    static final ArchRule shared_must_not_depend_on_any_module =
            noClasses()
                    .that().resideInAPackage("com.velan.production.shared..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.velan.production.iam..",
                            "com.velan.production.order..",
                            "com.velan.production.engineering..",
                            "com.velan.production.execution..",
                            "com.velan.production.quality..",
                            "com.velan.production.qcexception..",
                            "com.velan.production.vendor..",
                            "com.velan.production.notification..",
                            "com.velan.production.integration..")
                    .because("Shared kernel must not depend on any domain module");

    private static String[] otherInternalPackages(String currentModule) {
        return java.util.Arrays.stream(MODULES)
                .filter(m -> !m.equals(currentModule))
                .map(m -> "com.velan.production." + m + ".internal..")
                .toArray(String[]::new);
    }
}
