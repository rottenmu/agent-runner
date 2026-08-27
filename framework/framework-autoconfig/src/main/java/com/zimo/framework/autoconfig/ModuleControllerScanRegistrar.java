package com.zimo.framework.autoconfig;

import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link ModuleControllerScan} 的实现：按注解 {@code basePackage} 扫描
 * {@code @RestController} 并注册为全限定类名 Bean。
 *
 * <p>等价行为（与既有各模块 {@code @ComponentScan} 模板一致）：</p>
 * <ul>
 *   <li>{@code useDefaultFilters=false}：只注册 controller，绝不连带
 *       service/entity/mapper；</li>
 *   <li>includeFilters = {@code @RestController}：含组合注解（如
 *       {@code @RestControllerAdvice} 不受影响）；</li>
 *   <li>bean 名 = 全限定类名（对应
 *       {@code FullyQualifiedAnnotationBeanNameGenerator}），防不同包同名类
 *       冲突。</li>
 * </ul>
 *
 * @author WorkBuddy
 * @since 2026-08-17
 */
public class ModuleControllerScanRegistrar implements ImportBeanDefinitionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ModuleControllerScanRegistrar.class);

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes(
                ModuleControllerScan.class.getName());
        if (attributes == null) {
            return;
        }
        String basePackage = (String) attributes.get("basePackage");
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<org.springframework.beans.factory.config.BeanDefinition> candidates =
                scanner.findCandidateComponents(basePackage);
        int registered = 0;
        for (org.springframework.beans.factory.config.BeanDefinition candidate : candidates) {
            String beanClassName = candidate.getBeanClassName();
            if (beanClassName == null || registry.containsBeanDefinition(beanClassName)) {
                continue;
            }
            registry.registerBeanDefinition(beanClassName, candidate);
            registered++;
        }
        log.info("ModuleControllerScan[{}] 注册 controller {} 个", basePackage, registered);
    }
}
