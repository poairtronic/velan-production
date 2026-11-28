package com.velan.production.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

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

    @ArchTest
    static final ArchRule integration_api_must_not_depend_on_integration_internal =
            noClasses()
                    .that().resideInAPackage("com.velan.production.integration.api..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.velan.production.integration.internal..")
                    .because("integration.api must remain decoupled from integration.internal");

    @Test
    void only_integration_module_touches_outbox_events_and_rm_sync_history() throws IOException {
        Path root = Path.of("src/main/java/com/velan/production");
        List<String> violations;
        try (Stream<Path> walk = Files.walk(root)) {
            violations = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.normalize().toString().replace('\\', '/').contains("/integration/"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return content.contains("outbox_events") || content.contains("rm_sync_history");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(Path::toString)
                    .toList();
        }
        assertTrue(violations.isEmpty(),
                "Only the integration module may touch outbox_events or rm_sync_history, found: " + violations);
    }

    private static String[] otherInternalPackages(String currentModule) {
        return java.util.Arrays.stream(MODULES)
                .filter(m -> !m.equals(currentModule))
                .map(m -> "com.velan.production." + m + ".internal..")
                .toArray(String[]::new);
    }
}
