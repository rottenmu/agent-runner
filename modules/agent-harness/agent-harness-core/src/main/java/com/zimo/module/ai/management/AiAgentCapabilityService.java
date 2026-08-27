package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.Map;

/**
 * 智能体能力配置服务。
 *
 * <p>提供能力配置的读取（含默认值合并）与保存能力。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
public interface AiAgentCapabilityService extends IService<AiAgentCapability> {

    /**
     * 获取智能体能力配置，各能力项无记录时使用默认配置。
     *
     * @param agentId 智能体 ID
     * @return 能力配置 Map（goalDecomposition/intentRecognition/clarification/parameterExtraction/qaMemory）
     */
    Map<String, Object> getCapability(String agentId);

    /**
     * 保存智能体能力配置（不存在时创建）。
     *
     * @param agentId 智能体 ID
     * @param config  能力配置 Map
     */
    void saveCapability(String agentId, Map<String, Object> config);
}
