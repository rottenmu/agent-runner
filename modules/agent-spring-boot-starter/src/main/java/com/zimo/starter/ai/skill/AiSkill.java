package com.zimo.starter.ai.skill;

import java.util.List;
import java.util.Map;

public interface AiSkill {
    String name();

    String description();

    boolean readOnly();

    AiSkillResult call(Map<String, Object> arguments);

    /** 可调用参数名声明（用于参数提示/示例生成）；默认无约束。 */
    default List<String> inputParameters() {
        return List.of();
    }
}