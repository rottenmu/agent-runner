package com.zimo.agentapplication;

import com.zimo.framework.common.BizException;
import com.zimo.framework.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException e) {
        log.warn("BizException: {}", e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return ApiResponse.fail(400, msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("IllegalArgument: {}", e.getMessage());
        return ApiResponse.fail(400, e.getMessage());
    }

    /** 资源不存在：NoResourceFoundException 原落兜底 500，统一映射 404。 */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ApiResponse<Void> handleNotFound(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        log.warn("Resource not found: {}", e.getResourcePath());
        return ApiResponse.fail(404, "资源不存在: " + e.getResourcePath());
    }

    /** 请求方法不支持：映射 405。 */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        log.warn("Method not supported: {}", e.getMethod());
        return ApiResponse.fail(405, "请求方法不支持: " + e.getMethod());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ApiResponse.fail(500, "服务器内部错误");
    }
}
