package com.zimo.module.sys.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.sys.apiregistry.SysApiRegistryController;
import com.zimo.module.sys.apiregistry.SysApiRegistryRepository;
import com.zimo.module.sys.apiregistry.SysApiRegistryService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SysApiRegistryAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SysApiRegistryAutoConfiguration.class));

    @Test
    void createsManagementBeansWhenRegistryJdbcOperationsExists() {
        runner.withUserConfiguration(RegistryJdbcConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(SysApiRegistryRepository.class);
            assertThat(context).hasSingleBean(SysApiRegistryService.class);
            assertThat(context).hasSingleBean(SysApiRegistryController.class);
        });
    }

    @Test
    void backsOffWhenSystemPluginIsDisabled() {
        runner.withPropertyValues("plugin.sys.enabled=false")
                .withUserConfiguration(RegistryJdbcConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SysApiRegistryRepository.class);
                    assertThat(context).doesNotHaveBean(SysApiRegistryService.class);
                    assertThat(context).doesNotHaveBean(SysApiRegistryController.class);
                });
    }
    @Test
    void backsOffWithoutRegistryJdbcOperations() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(SysApiRegistryRepository.class);
            assertThat(context).doesNotHaveBean(SysApiRegistryService.class);
            assertThat(context).doesNotHaveBean(SysApiRegistryController.class);
        });
    }

    @Test
    void excludesApiRegistryPackageFromDefaultComponentScan() {
        ComponentScan componentScan = SysAutoConfiguration.class.getAnnotation(ComponentScan.class);

        assertThat(componentScan.excludeFilters()).anySatisfy(filter -> {
            assertThat(filter.type()).isEqualTo(FilterType.REGEX);
            assertThat(filter.pattern()).contains(
                    "com\\.zimo\\.module\\.sys\\.apiregistry\\..*");
        });
    }

    @Test
    void publishesAutoConfigurationThroughBootImportsAndSpringFactories() throws IOException {
        String imports = read("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        String factories = read("META-INF/spring.factories");

        assertThat(imports).contains("com.zimo.module.sys.autoconfig.SysApiRegistryAutoConfiguration");
        assertThat(factories).contains("com.zimo.module.sys.autoconfig.SysApiRegistryAutoConfiguration");
    }

    private String read(String path) throws IOException {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Configuration(proxyBeanMethods = false)
    static class RegistryJdbcConfiguration {

        @Bean("apiRegistryJdbcOperations")
        JdbcOperations apiRegistryJdbcOperations() {
            return new JdbcTemplate(new DriverManagerDataSource());
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
