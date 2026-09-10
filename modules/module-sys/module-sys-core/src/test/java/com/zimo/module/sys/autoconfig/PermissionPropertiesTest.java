package com.zimo.module.sys.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

class PermissionPropertiesTest {

    @Test
    void defaultValuesCoverPermissionSwitchesAndWhitelist() {
        PermissionProperties properties = new PermissionProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDataScope().isEnabled()).isTrue();
        assertThat(properties.getDataScope().getDeptColumn()).isEqualTo("dept_id");
        assertThat(properties.getFieldMask().isEnabled()).isTrue();
        assertThat(properties.getFieldMask().getDefaultMask()).isEqualTo("******");
        assertThat(properties.getOperationLog().isEnabled()).isTrue();
        assertThat(properties.getOperationLog().isRecordRequestBody()).isFalse();
        assertThat(properties.getWhitelist().getPaths())
                .contains("/api/auth/login", "/api/auth/register", "/api/auth/logout", "/actuator/health");
    }

    @Test
    void bindsManufacturePermissionPrefixFromYamlStyleKeys() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "manufacture.permission.enabled", "false",
                "manufacture.permission.data-scope.enabled", "false",
                "manufacture.permission.data-scope.dept-column", "organization_id",
                "manufacture.permission.field-mask.enabled", "false",
                "manufacture.permission.operation-log.record-request-body", "true",
                "manufacture.permission.whitelist.paths[0]", "/public/**",
                "manufacture.permission.whitelist.paths[1]", "/health"
        ));

        PermissionProperties properties = new Binder(source)
                .bind("manufacture.permission", Bindable.of(PermissionProperties.class))
                .orElseThrow(() -> new IllegalStateException("manufacture.permission binding failed"));

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getDataScope().isEnabled()).isFalse();
        assertThat(properties.getDataScope().getDeptColumn()).isEqualTo("organization_id");
        assertThat(properties.getFieldMask().isEnabled()).isFalse();
        assertThat(properties.getOperationLog().isRecordRequestBody()).isTrue();
        assertThat(properties.getWhitelist().getPaths()).containsExactly("/public/**", "/health");
    }
}
