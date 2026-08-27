package com.zimo.starter.ai.runtime;

import com.zimo.starter.ai.skill.AiSkillDescriptor;
import java.util.List;

public record AiAgentRuntime(
        String agentName,
        String modelName,
        String modelType,
        List<AiSkillDescriptor> skillDescriptors,
        AiAgentRuntimeStatus status,
        String message) {
}
