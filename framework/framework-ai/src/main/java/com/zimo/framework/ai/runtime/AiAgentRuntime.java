package com.zimo.framework.ai.runtime;

import com.zimo.framework.ai.skill.AiSkillDescriptor;
import java.util.List;

public record AiAgentRuntime(
        String agentName,
        String modelName,
        String modelType,
        List<AiSkillDescriptor> skillDescriptors,
        AiAgentRuntimeStatus status,
        String message) {
}
