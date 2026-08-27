package com.zimo.module.ai.modelconfig;

/**
 * AI 模型配置批量导入响应体。
 *
 * @param successCount 成功导入数量
 * @param skippedCount 跳过或失败数量
 */
public record AiModelConfigImportResponse(int successCount, int skippedCount) {
}