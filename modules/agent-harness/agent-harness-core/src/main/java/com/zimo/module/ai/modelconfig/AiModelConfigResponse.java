package com.zimo.module.ai.modelconfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 模型配置响应体。
 *
 * <p>响应体只返回 {@code apiKeyMasked}，禁止泄露原始 {@code apiKey}。</p>
 *
 * @param id 主键 ID
 * @param configName 配置名称
 * @param description 配置说明
 * @param provider 模型供应商
 * @param endpoint 模型服务调用地址
 * @param apiKeyMasked 已脱敏的 API Key
 * @param modelId 模型 ID
 * @param env 运行环境
 * @param enabled 启用状态
 * @param temperature 采样温度
 * @param topP Top P 参数
 * @param maxTokens 最大输出 Token 数
 * @param tags 管理端标签
 * @param lastTestStatus 最近一次连接测试状态
 * @param lastTestLatency 最近一次连接测试耗时，单位毫秒
 * @param lastTestMessage 最近一次连接测试消息
 * @param lastTestedAt 最近一次连接测试时间
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record AiModelConfigResponse(
        Long id,
        String configName,
        String description,
        String provider,
        String endpoint,
        String apiKeyMasked,
        String modelId,
        String env,
        boolean enabled,
        BigDecimal temperature,
        BigDecimal topP,
        int maxTokens,
        List<String> tags,
        String lastTestStatus,
        int lastTestLatency,
        String lastTestMessage,
        LocalDateTime lastTestedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public AiModelConfigResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}