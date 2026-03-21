package com.genie.query.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LlmLayerDependencyArchTest {

    private static final String BASE_PACKAGE = "com.genie.query";

    @Test
    void domain_should_not_depend_on_infrastructure_llm_properties() {
        JavaClasses classes = new ClassFileImporter().importPackages(BASE_PACKAGE);
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure.llm..");
        rule.check(classes);
    }

    @Test
    void application_should_not_depend_on_infrastructure_llm_properties() {
        JavaClasses classes = new ClassFileImporter().importPackages(BASE_PACKAGE);
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure.llm..");
        rule.check(classes);
    }
}
