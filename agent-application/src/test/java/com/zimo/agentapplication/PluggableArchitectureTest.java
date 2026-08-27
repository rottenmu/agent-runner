package com.zimo.agentapplication;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 可插拔架构依赖方向守卫测试。
 *
 * <p>基于 ArchUnit 校验四层可插拔架构的依赖铁律（对应 Python 侧
 * check_layering.py 的 Java 版本）：</p>
 * <ul>
 *   <li>业务 core 层不得依赖任何 autoconfig 层；</li>
 *   <li>framework-common 不得依赖 Spring 框架；</li>
 *   <li>framework-common 不得依赖任何业务模块。</li>
 * </ul>
 *
 * @author WorkBuddy
 * @since 2026-07-30
 */
class PluggableArchitectureTest {

    /** 待校验的全部类，扫描生产包（不含测试类） */
    private static JavaClasses classes;

    /**
     * 导入待校验的全部生产类。
     */
    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.zimo", "com.zimo");
    }

    /**
     * 校验业务 core 层不得依赖任何 autoconfig 层。
     */
    @Test
    void coreShouldNotDependOnAutoconfig() {
        noClasses()
                .that().resideInAPackage("com.zimo.module..")
                .and().resideOutsideOfPackage("..autoconfig..")
                .should().dependOnClassesThat().resideInAPackage("..autoconfig..")
                .because("core 层依赖 autoconfig 会破坏可插拔架构的单向依赖铁律")
                .check(classes);
    }

    /**
     * 校验 framework-common 不得依赖 Spring 框架。
     */
    @Test
    void frameworkCommonShouldNotDependOnSpring() {
        noClasses()
                .that().resideInAPackage("com.zimo.framework.common..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("framework-common 必须保持零 Spring 依赖，可被任意层安全引用")
                .check(classes);
    }

    /**
     * 校验 framework-common 不得依赖任何业务模块。
     */
    @Test
    void frameworkCommonShouldNotDependOnModules() {
        noClasses()
                .that().resideInAPackage("com.zimo.framework.common..")
                .should().dependOnClassesThat().resideInAPackage("com.zimo.module..")
                .because("framework-common 是最底层公共契约，不得反向依赖业务模块")
                .check(classes);
    }
}
