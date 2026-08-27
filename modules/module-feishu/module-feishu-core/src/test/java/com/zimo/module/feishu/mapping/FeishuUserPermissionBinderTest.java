package com.zimo.module.feishu.mapping;

import com.zimo.module.sys.context.PermissionCache;
import com.zimo.module.sys.context.UserPermissionContext;
import com.zimo.module.sys.enums.DataScopeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuUserPermissionBinderTest {

    @AfterEach
    void tearDown() {
        PermissionCache.unbindCurrent();
        PermissionCache.clear();
    }

    @Test
    void bindsAndCleansCurrentPermissionContext() throws Exception {
        FeishuUserPermissionBinder binder = new FeishuUserPermissionBinder();
        FeishuInternalUserSnapshot snapshot = new FeishuInternalUserSnapshot(
                7L,
                "planner",
                "factory_1",
                "factory one",
                Set.of("pm:project:list"),
                DataScopeEnum.FACTORY
        );

        AutoCloseable scope = binder.bind(snapshot);

        assertThat(UserPermissionContext.currentUserId()).contains(7L);
        assertThat(UserPermissionContext.currentAccount()).contains("planner");
        assertThat(UserPermissionContext.currentPermissions()).contains("pm:project:list");

        scope.close();

        assertThat(UserPermissionContext.current()).isEmpty();
    }
}
