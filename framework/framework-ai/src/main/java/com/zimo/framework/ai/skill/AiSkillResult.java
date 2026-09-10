package com.zimo.framework.ai.skill;

public record AiSkillResult(boolean success, String content) {
    public static AiSkillResult ok(String content) {
        return new AiSkillResult(true, content == null ? "" : content);
    }

    public static AiSkillResult fail(String content) {
        return new AiSkillResult(false, content == null ? "" : content);
    }
}
