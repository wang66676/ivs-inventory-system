package com.ivs.app;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块边界硬约束（《进销存系统落地方案 v1.0》第 11.2 节）：
 * 业务模块之间不允许直接依赖，只能通过领域事件或 app 层编排通信。
 */
class ModuleBoundaryTest {

    private static final List<String> BUSINESS_MODULES = List.of(
            "masterdata", "inventory", "procurement", "sales", "billing", "retail", "policy", "lease");

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.ivs");

    @Test
    void 业务模块之间不允许直接依赖() {
        for (String module : BUSINESS_MODULES) {
            String[] others = BUSINESS_MODULES.stream()
                    .filter(other -> !other.equals(module))
                    .map(other -> "com.ivs." + other + "..")
                    .toArray(String[]::new);
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ivs." + module + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(others);
            rule.check(CLASSES);
        }
    }

    @Test
    void 平台模块不允许依赖业务模块() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ivs.platform..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BUSINESS_MODULES.stream().map(m -> "com.ivs." + m + "..").toArray(String[]::new));
        rule.check(CLASSES);
    }

    @Test
    void app层不允许被其他模块依赖() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.ivs.app..")
                .should().dependOnClassesThat().resideInAPackage("com.ivs.app..");
        rule.check(CLASSES);
    }
}
