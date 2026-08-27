package com.zimo.agentapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiRegistryOwnershipTest {

    private static final List<String> OLD_ADMIN_CLASSES = List.of(
            "AdminApiRegistryController",
            "AdminApiRegistryItem",
            "AdminApiRegistryModuleGroup",
            "AdminApiRegistryQuery",
            "AdminApiRegistryReader",
            "AdminApiRegistryRepository",
            "AdminApiRegistryService");

    @Test
    void apiRegistryManagementBelongsToSystemModule() throws ClassNotFoundException {
        for (String className : OLD_ADMIN_CLASSES) {
            assertThatThrownBy(() -> Class.forName(
                    "com.zimo.admin.apiregistry." + className))
                    .isInstanceOf(ClassNotFoundException.class);
        }
        assertThat(Class.forName(
                "com.zimo.module.sys.apiregistry.SysApiRegistryController"))
                .isNotNull();
    }
}