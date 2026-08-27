package com.zimo.framework.autoconfig;

import com.zimo.framework.autoconfig.apiregistry.ApiRegistryProperties;
import com.zimo.framework.autoconfig.apiregistry.ApiRegistryStartupRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class FrameworkApiRegistryAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FrameworkApiRegistryAutoConfiguration.class));

    @Test
    void noDataSourceDoesNotCreateRegistryStartupRunner() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ApiRegistryStartupRunner.class);
        });
    }

    @Test
    void explicitDataSourceNameSelectsConfiguredBean() {
        FrameworkApiRegistryAutoConfiguration configuration = new FrameworkApiRegistryAutoConfiguration();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        DataSource first = mock(DataSource.class);
        DataSource selected = mock(DataSource.class);
        beanFactory.addBean("firstDataSource", first);
        beanFactory.addBean("selectedDataSource", selected);
        ApiRegistryProperties properties = new ApiRegistryProperties();
        properties.setDataSourceBeanName("selectedDataSource");

        JdbcTemplate jdbcTemplate = (JdbcTemplate) configuration
                .apiRegistryJdbcOperations(beanFactory, properties);

        assertThat(jdbcTemplate.getDataSource()).isSameAs(selected);
    }

    @Test
    void multipleDataSourcesRequireExplicitBeanNameEvenWhenOneIsNamedDataSource() {
        FrameworkApiRegistryAutoConfiguration configuration = new FrameworkApiRegistryAutoConfiguration();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("dataSource", mock(DataSource.class));
        beanFactory.addBean("wmsDataSource", mock(DataSource.class));

        assertThatThrownBy(() -> configuration.apiRegistryJdbcOperations(
                beanFactory, new ApiRegistryProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("data-source-bean-name");
    }
}
