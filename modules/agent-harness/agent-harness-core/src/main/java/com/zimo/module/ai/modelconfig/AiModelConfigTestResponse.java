package com.zimo.module.ai.modelconfig;

import java.time.LocalDateTime;

/**
 * AI 模型配置连接测试响应体。
 *
 * @param status 测试状态，success 表示通过，failed 表示失败
 * @param latency 模拟测试耗时，单位毫秒
 * @param message 测试结果说明
 * @param testedAt 测试完成时间
 */
public record AiModelConfigTestResponse(
        String status,
        int latency,
        String message,
        LocalDateTime testedAt) {
}