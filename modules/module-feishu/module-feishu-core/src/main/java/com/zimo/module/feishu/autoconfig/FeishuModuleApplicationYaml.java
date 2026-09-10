package com.zimo.module.feishu.autoconfig;

import java.util.Properties;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

final class FeishuModuleApplicationYaml {
    static final String LOCATION = "module-feishu/application.yaml";

    private final Environment environment;
    private final Properties properties;

    private FeishuModuleApplicationYaml(Environment environment, Properties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    static FeishuModuleApplicationYaml load(Environment environment) {
        Resource resource = new ClassPathResource(LOCATION);
        if (!resource.exists()) {
            throw new IllegalStateException(LOCATION + " is required for Feishu module configuration");
        }

        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(resource);
        Properties loaded = factoryBean.getObject();
        return new FeishuModuleApplicationYaml(environment, loaded == null ? new Properties() : loaded);
    }

    String get(String key) {
        String environmentValue = environment.getProperty(key);
        if (environmentValue != null) {
            return environmentValue;
        }

        String value = properties.getProperty(key);
        return value == null ? null : environment.resolvePlaceholders(value);
    }
}
