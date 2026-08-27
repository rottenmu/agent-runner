package com.zimo.module.ai.modelconfig;

import java.util.List;

/**
 * AI 模型配置批量导入请求体。
 *
 * @param configs 待导入模型配置列表，允许为空
 */
public record AiModelConfigImportRequest(List<AiModelConfigRequest> configs) {

    public AiModelConfigImportRequest {
        configs = configs == null ? List.of() : List.copyOf(configs);
    }
}