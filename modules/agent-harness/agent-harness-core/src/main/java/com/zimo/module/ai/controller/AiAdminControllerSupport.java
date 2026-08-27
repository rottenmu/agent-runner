package com.zimo.module.ai.controller;

import com.zimo.framework.common.ApiResponse;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.common.BizException;

/**
 * AI 管理控制器的统一调用适配工具。
 *
 * <p>仅负责将领域参数异常转换为平台业务异常，不承载具体管理业务。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
final class AiAdminControllerSupport {

    private AiAdminControllerSupport() {
    }

    static <T> ApiResponse<T> call(ServiceCall<T> call) {
        try {
            return ApiResponse.ok(call.execute());
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    static BizException badRequest(IllegalArgumentException exception) {
        String message = exception.getMessage();
        return new BizException(400, StrUtil.isBlank(message) ? "请求参数不合法" : message);
    }

    @FunctionalInterface
    interface ServiceCall<T> {

        T execute();
    }
}
