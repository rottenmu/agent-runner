package com.zimo.module.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.zimo.framework.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    void mapsSaTokenExceptionsToApiResponses() {
        ApiResponse<Void> notLogin = handler.handleNotLogin(new NotLoginException("not login", null, null));
        ApiResponse<Void> notPermission = handler.handleNotPermission(new NotPermissionException("sys:user:list"));
        ApiResponse<Void> notRole = handler.handleNotRole(new NotRoleException("admin"));

        assertThat(notLogin.getCode()).isEqualTo(401);
        assertThat(notPermission.getCode()).isEqualTo(403);
        assertThat(notRole.getCode()).isEqualTo(403);
    }

    @Test
    void runsBeforeApplicationWideFallbackHandlers() {
        Order order = AnnotationUtils.findAnnotation(AuthExceptionHandler.class, Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
