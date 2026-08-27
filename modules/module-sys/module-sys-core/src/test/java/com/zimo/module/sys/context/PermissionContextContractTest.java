package com.zimo.module.sys.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zimo.module.sys.enums.DataScopeEnum;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PermissionContextContractTest {

    @AfterEach
    void tearDown() {
        PermissionCache.clear();
        PermissionCache.unbindCurrent();
        PermissionCache.setBackend(PermissionCache.NoopBackend.INSTANCE);
    }

    @Test
    void userPermissionContextExposesOnlyReadMethodsAndDefensiveCopies() {
        UserPermissionContext context = UserPermissionContext.of(
                7L,
                "zhangsan",
                "factory-01",
                "factory-one",
                Set.of("sys:role:list", "pm:project:edit"),
                DataScopeEnum.FACTORY,
                Set.of("phone", "email")
        );

        assertThat(context.getUserId()).isEqualTo(7L);
        assertThat(context.getAccount()).isEqualTo("zhangsan");
        assertThat(context.getOrganizationId()).isEqualTo("factory-01");
        assertThat(context.getOrganizationName()).isEqualTo("factory-one");
        assertThat(context.getDataScope()).isEqualTo(DataScopeEnum.FACTORY);
        assertThat(context.hasPermission("sys:role:list")).isTrue();
        assertThat(context.hasAnyPermission("missing", "pm:project:edit")).isTrue();
        assertThat(context.needDesensitize("phone")).isTrue();
        assertThat(context.getPermissions()).containsExactlyInAnyOrder("sys:role:list", "pm:project:edit");

        assertThatThrownBy(() -> context.getPermissions().add("sys:role:add"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> context.getDesensitizeFields().add("mobile"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(UserPermissionContext.class.getMethods())
                .extracting(Method::getName)
                .noneMatch(name -> name.startsWith("set"));
    }

    @Test
    void staticContextReadsCurrentBoundUserFromPermissionCache() {
        UserPermissionContext context = UserPermissionContext.of(
                8L,
                "lisi",
                "workshop-01",
                "assembly-workshop",
                Set.of("wms:stock:query"),
                DataScopeEnum.WORKSHOP,
                Set.of("phone")
        );
        PermissionCache.put(context);
        PermissionCache.bindCurrent(8L);

        assertThat(UserPermissionContext.current()).isPresent();
        assertThat(UserPermissionContext.currentUserId()).isEqualTo(Optional.of(8L));
        assertThat(UserPermissionContext.currentAccount()).isEqualTo(Optional.of("lisi"));
        assertThat(UserPermissionContext.currentOrganizationId()).isEqualTo(Optional.of("workshop-01"));
        assertThat(UserPermissionContext.currentDataScope()).isEqualTo(Optional.of(DataScopeEnum.WORKSHOP));
        assertThat(UserPermissionContext.currentPermissions()).containsExactly("wms:stock:query");
        assertThat(UserPermissionContext.hasCurrentPermission("wms:stock:query")).isTrue();
        assertThat(UserPermissionContext.currentNeedDesensitize("phone")).isTrue();
    }

    @Test
    void permissionCacheExpiresAndCleansLocalEntries() throws Exception {
        UserPermissionContext context = UserPermissionContext.of(
                9L,
                "wangwu",
                "group",
                "manufacturing-group",
                Set.of("sys:user:list"),
                DataScopeEnum.GROUP,
                Set.of()
        );

        PermissionCache.put(context, Duration.ofMillis(20));
        assertThat(PermissionCache.get(9L)).contains(context);

        Thread.sleep(40L);

        assertThat(PermissionCache.get(9L)).isEmpty();
        assertThat(PermissionCache.cleanupExpired()).isZero();
        assertThat(PermissionCache.size()).isZero();
    }

    @Test
    void permissionCacheSupportsRedisLikeBackendExtension() {
        RecordingBackend backend = new RecordingBackend();
        PermissionCache.setBackend(backend);
        UserPermissionContext context = UserPermissionContext.of(
                10L,
                "zhaoliu",
                "factory-02",
                "factory-two",
                Set.of("sys:menu:list"),
                DataScopeEnum.FACTORY,
                Set.of("email")
        );

        PermissionCache.put(context, Duration.ofMinutes(5));
        PermissionCache.remove(10L);

        assertThat(backend.savedUserId).isEqualTo(10L);
        assertThat(backend.deletedUserId).isEqualTo(10L);
    }

    private static class RecordingBackend implements PermissionCache.Backend {
        private Long savedUserId;
        private Long deletedUserId;

        @Override
        public void save(UserPermissionContext context, Duration ttl) {
            this.savedUserId = context.getUserId();
        }

        @Override
        public Optional<UserPermissionContext> load(Long userId) {
            return Optional.empty();
        }

        @Override
        public void delete(Long userId) {
            this.deletedUserId = userId;
        }
    }
}
