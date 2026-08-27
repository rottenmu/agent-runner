package com.zimo.module.ai.modelconfig;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 模型配置新增或编辑请求体。
 *
 * <p>新增时 {@code apiKey} 必填；编辑时 {@code apiKey} 为空或以 {@code ***}
 * 开头表示沿用原密钥。</p>
 *
 * @param configName 配置名称，必填
 * @param description 配置说明，允许为空
 * @param provider 模型供应商，支持 bailian/custom
 * @param endpoint 模型服务调用地址，必填
 * @param apiKey 模型服务密钥，新增必填，编辑可使用脱敏占位
 * @param modelId 模型 ID，必填
 * @param env 运行环境，支持 dev/staging/prod
 * @param enabled 启用状态，为空时默认 true
 * @param temperature 采样温度，为空时默认 0.70
 * @param topP Top P 参数，为空时默认 0.80
 * @param maxTokens 最大输出 Token 数，为空时默认 4096
 * @param tags 管理端标签，空值按空列表处理
 */
public record AiModelConfigRequest(
        String configName,
        String description,
        String provider,
        String endpoint,
        String apiKey,
        String modelId,
        String env,
        Boolean enabled,
        BigDecimal temperature,
        BigDecimal topP,
        Integer maxTokens,
        List<String> tags) {

    public AiModelConfigRequest {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}