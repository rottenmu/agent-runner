package com.zimo.module.ai.management;

/**
 * 注册技能绑定提示词模板的请求体。
 */
public class AiSkillPromptTemplateRequest {
    private Long promptTemplateId;

    public Long getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(Long promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
    }
}
