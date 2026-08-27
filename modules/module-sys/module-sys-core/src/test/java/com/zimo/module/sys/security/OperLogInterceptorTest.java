package com.zimo.module.sys.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.module.sys.context.PermissionCache;
import com.zimo.module.sys.context.UserPermissionContext;
import com.zimo.module.sys.entity.SysOperLog;
import com.zimo.module.sys.enums.DataScopeEnum;
import com.zimo.module.sys.enums.PermOperTypeEnum;
import com.zimo.module.sys.service.OperLogService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class OperLogInterceptorTest {

    @AfterEach
    void tearDown() {
        PermissionCache.clear();
        PermissionCache.unbindCurrent();
    }

    @Test
    void recordsSuccessfulRequestWithOperatorIpParamsCostAndOperationType() throws Exception {
        bindCurrentUser();
        OperLogService.InMemoryRepository repository = new OperLogService.InMemoryRepository();
        OperLogInterceptor interceptor = new OperLogInterceptor(new OperLogService(repository), true, List.of());
        MockHttpServletRequest request = request("POST", "/api/biz/sys/role");
        request.addHeader("X-Forwarded-For", "10.0.0.12, 10.0.0.1");
        request.addParameter("roleName", "manager");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.preHandle(request, response, handler("createRole"));
        interceptor.afterCompletion(request, response, handler("createRole"), null);

        assertThat(repository.findAll()).hasSize(1);
        SysOperLog log = repository.findAll().get(0);
        assertThat(log.getOperUserId()).isEqualTo(7L);
        assertThat(log.getOperAccount()).isEqualTo("zhangsan");
        assertThat(log.getOperIp()).isEqualTo("10.0.0.12");
        assertThat(log.getRequestUri()).isEqualTo("/api/biz/sys/role");
        assertThat(log.getRequestMethod()).isEqualTo("POST");
        assertThat(log.getOperType()).isEqualTo(PermOperTypeEnum.ADD.getCode());
        assertThat(log.getRequestParams()).contains("roleName=manager");
        assertThat(log.getResultStatus()).isEqualTo("SUCCESS");
        assertThat(log.getCostMillis()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void recordsFailedRequestWithExceptionMessage() throws Exception {
        bindCurrentUser();
        OperLogService.InMemoryRepository repository = new OperLogService.InMemoryRepository();
        OperLogInterceptor interceptor = new OperLogInterceptor(new OperLogService(repository));
        MockHttpServletRequest request = request("DELETE", "/api/biz/sys/role/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RuntimeException error = new IllegalStateException("delete failed");

        interceptor.preHandle(request, response, handler("deleteRole"));
        interceptor.afterCompletion(request, response, handler("deleteRole"), error);

        SysOperLog log = repository.findAll().get(0);
        assertThat(log.getOperType()).isEqualTo(PermOperTypeEnum.DELETE.getCode());
        assertThat(log.getResultStatus()).isEqualTo("FAIL");
        assertThat(log.getErrorMessage()).contains("delete failed");
    }

    @Test
    void skipsWhenSwitchDisabledOrPathWhitelisted() throws Exception {
        bindCurrentUser();
        OperLogService.InMemoryRepository repository = new OperLogService.InMemoryRepository();
        OperLogInterceptor disabled = new OperLogInterceptor(new OperLogService(repository), false, List.of());
        OperLogInterceptor whitelisted = new OperLogInterceptor(new OperLogService(repository), true, List.of("/actuator/**"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest disabledRequest = request("POST", "/api/biz/sys/role");
        MockHttpServletRequest whitelistRequest = request("GET", "/actuator/health");

        disabled.preHandle(disabledRequest, response, handler("createRole"));
        disabled.afterCompletion(disabledRequest, response, handler("createRole"), null);
        whitelisted.preHandle(whitelistRequest, response, handler("createRole"));
        whitelisted.afterCompletion(whitelistRequest, response, handler("createRole"), null);

        assertThat(repository.findAll()).isEmpty();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        DemoController controller = new DemoController();
        return new HandlerMethod(controller, DemoController.class.getDeclaredMethod(methodName));
    }

    private static void bindCurrentUser() {
        UserPermissionContext context = UserPermissionContext.of(
                7L,
                "zhangsan",
                "factory-01",
                "factory-one",
                Set.of("sys:role:add"),
                DataScopeEnum.FACTORY,
                Set.of()
        );
        PermissionCache.bindCurrent(context);
    }

    private static class DemoController {

        void createRole() {
        }

        void deleteRole() {
        }
    }
}
