package com.zimo.module.auth.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.zimo.framework.common.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ApiResponse<Void> handleNotLogin(NotLoginException e) {
        return ApiResponse.fail(401, "未登录或登录已过期，请重新登录");
    }

    @ExceptionHandler(NotPermissionException.class)
    public ApiResponse<Void> handleNotPermission(NotPermissionException e) {
        return ApiResponse.fail(403, "无权限: " + e.getPermission());
    }

    @ExceptionHandler(NotRoleException.class)
    public ApiResponse<Void> handleNotRole(NotRoleException e) {
        return ApiResponse.fail(403, "无角色: " + e.getRole());
    }
}
