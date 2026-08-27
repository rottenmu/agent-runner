package com.zimo.module.ai.management;

import java.time.LocalDateTime;

public record AiManagedSkill(
        String name,
        String description,
        boolean readOnly,
        long referenceCount,
        Long promptTemplateId,
        AiSkillApiConfigResponse apiConfig,
        String source,
        boolean enabled,
        String agentId,
        LocalDateTime createdAt) {
}
