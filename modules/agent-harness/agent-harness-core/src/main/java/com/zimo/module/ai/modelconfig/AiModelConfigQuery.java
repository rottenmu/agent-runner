package com.zimo.module.ai.modelconfig;

/**
 * AI 模型配置查询条件。
 *
 * @param env 运行环境过滤条件，允许为空
 * @param provider 模型供应商过滤条件，允许为空
 * @param status 启停状态过滤条件，允许 enabled/disabled 或为空
 * @param keyword 配置名称、模型 ID 或描述关键字，允许为空
 */
public record AiModelConfigQuery(
        String env,
        String provider,
        String status,
        String keyword) {
}