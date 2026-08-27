package com.zimo.module.sys.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

class SysAutoConfigurationTest {

    @Test
    void scansSysMapperPackageItself() {
        MapperScan mapperScan = SysAutoConfiguration.class.getAnnotation(MapperScan.class);

        assertThat(mapperScan).isNotNull();
        assertThat(mapperScan.value()).contains("com.zimo.module.sys.mapper");
    }

    @Test
    void enablesPermissionProperties() {
        EnableConfigurationProperties annotation =
                SysAutoConfiguration.class.getAnnotation(EnableConfigurationProperties.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(PermissionProperties.class);
    }
}
