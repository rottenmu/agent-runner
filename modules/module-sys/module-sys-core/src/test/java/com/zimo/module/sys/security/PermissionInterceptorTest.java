package com.zimo.module.sys.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zimo.module.sys.annotation.HasPerm;
import com.zimo.module.sys.annotation.IgnorePermission;
import com.zimo.module.sys.context.PermissionCache;
import com.zimo.module.sys.context.UserPermissionContext;
import com.zimo.module.sys.enums.DataScopeEnum;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

class PermissionInterceptorTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        PermissionCache.clear();
        PermissionCache.unbindCurrent();
    }

    @Test
    void permitsWhitelistPathWithoutPermissionContext() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor(true, List.of("/api/auth/**"));

        assertThatCode(() -> interceptor.preHandle(request("/api/auth/login"), response, handler("secured")))
                .doesNotThrowAnyException();
    }

    @Test
    void defaultWhitelistPermitsRegisterWithoutPermissionContext() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor();

        assertThatCode(() -> interceptor.preHandle(request("/api/auth/register"), response, handler("secured")))
                .doesNotThrowAnyException();
    }

    @Test
    void permitsMethodMarkedIgnorePermission() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor();

        assertThatCode(() -> interceptor.preHandle(request("/api/sys/public"), response, handler("ignored")))
                .doesNotThrowAnyException();
    }

    @Test
    void permitsWhenCurrentUserOwnsMethodPermission() throws Exception {
        bindUserWithPermissions("sys:role:list", "sys:user:list");
        PermissionInterceptor interceptor = new PermissionInterceptor();

        assertThatCode(() -> interceptor.preHandle(request("/api/sys/roles"), response, handler("secured")))
                .doesNotThrowAnyException();
    }

    @Test
    void permitsWildcardPermissionThroughUnifiedPermissionService() throws Exception {
        bindUserWithPermissions("sys:role:*");
        PermissionInterceptor interceptor = new PermissionInterceptor();

        assertThatCode(() -> interceptor.preHandle(request("/api/sys/roles"), response, handler("secured")))
                .doesNotThrowAnyException();
    }

    @Test
    void permitsWhenCurrentUserOwnsClassPermission() throws Exception {
        bindUserWithPermissions("sys:role:list");
        PermissionInterceptor interceptor = new PermissionInterceptor();

        assertThatCode(() -> interceptor.preHandle(request("/api/sys/roles/export"), response, handler("classSecured")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWithoutRequiredPermissionAsForbidden() throws Exception {
        bindUserWithPermissions("sys:user:list");
        PermissionInterceptor interceptor = new PermissionInterceptor();

        assertThatThrownBy(() -> interceptor.preHandle(request("/api/sys/roles"), response, handler("secured")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void permitsAllRequestsWhenPermissionSwitchIsDisabled() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor(false, List.of());

        assertThatCode(() -> interceptor.preHandle(request("/api/sys/roles"), response, handler("secured")))
                .doesNotThrowAnyException();
    }

    @Test
    void permitsNonHandlerMethodRequests() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor();

        assertThatCode(() -> interceptor.preHandle(request("/assets/app.js"), response, new Object()))
                .doesNotThrowAnyException();
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        DemoController controller = new DemoController();
        return new HandlerMethod(controller, DemoController.class.getDeclaredMethod(methodName));
    }

    private static void bindUserWithPermissions(String... permissions) {
        UserPermissionContext context = UserPermissionContext.of(
                1L,
                "admin",
                "factory-01",
                "factory-one",
                Set.of(permissions),
                DataScopeEnum.FACTORY,
                Set.of()
        );
        PermissionCache.bindCurrent(context);
    }

    @HasPerm("sys:role:list")
    private static class DemoController {

        @HasPerm("sys:role:list")
        void secured() {
        }

        @IgnorePermission
        @HasPerm("sys:role:secret")
        void ignored() {
        }

        void classSecured() {
        }
    }
}
