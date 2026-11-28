package com.velan.production.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import org.springframework.web.bind.annotation.RestController;
import jakarta.persistence.MappedSuperclass;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@AnalyzeClasses(packages = "com.velan.production", importOptions = ImportOption.DoNotIncludeTests.class)
class EntityRulesTests {

        private static final Set<String> FORBIDDEN_LOMBOK_ANNOTATIONS = Set.of(
                        "lombok.Data",
                        "lombok.EqualsAndHashCode",
                        "lombok.ToString");

        @ArchTest
        static final ArchRule no_forbidden_lombok_annotations_on_domain_classes = classes()
                        .that().resideInAPackage("..internal.domain..")
                        .should(new ArchCondition<>("not be annotated with @Data, @EqualsAndHashCode, or @ToString") {
                                @Override
                                public void check(JavaClass item, ConditionEvents events) {
                                        item.getAnnotations().stream()
                                                        .map(a -> a.getRawType().getName())
                                                        .filter(FORBIDDEN_LOMBOK_ANNOTATIONS::contains)
                                                        .forEach(ann -> events.add(SimpleConditionEvent.violated(
                                                                        item,
                                                                        item.getName() + " is annotated with forbidden @"
                                                                                        + ann)));
                                }
                        });

        @ArchTest
        static final ArchRule no_public_setStatus_in_internal_domain = methods()
                        .that().areDeclaredInClassesThat().resideInAPackage("..internal.domain..")
                        .and().haveName("setStatus")
                        .should(new ArchCondition<>("not be public") {
                                @Override
                                public void check(JavaMethod method, ConditionEvents events) {
                                        if (method.getModifiers().contains(JavaModifier.PUBLIC)) {
                                                events.add(SimpleConditionEvent.violated(
                                                                method, method.getFullName()
                                                                                + " must not be public; use explicit state transition methods"));
                                        }
                                }
                        });

        @ArchTest
        static final ArchRule enums_must_reside_in_domain_package = classes()
                        .that().areEnums()
                        .should().resideInAnyPackage("..domain..", "..shared.error..", "..shared.audit..");

        @ArchTest
        static final ArchRule mapped_superclasses_must_reside_in_shared = classes()
                        .that().areAnnotatedWith(MappedSuperclass.class)
                        .should().resideInAPackage("..shared.persistence..");

        @ArchTest
        static final ArchRule rest_controllers_must_reside_in_api_package = classes()
                        .that().areAnnotatedWith(RestController.class)
                        .should().resideInAPackage("..api..");
}
