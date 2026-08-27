package com.zimo.module.ai.management;

/**
 * AI 管理模块的提示词模板生成请求，用于根据业务简述生成 CoSTAR 草稿。
 *
 * <p>生成结果只作为草稿返回，由调用方确认后再通过保存接口写入仓储。</p>
 *
 * @author xingju
 * @since 2026-07-08
 */
public class AiPromptTemplateGenerateRequest {
    /**
     * 业务简述，用于描述希望智能体支持的场景、任务和约束。
     */
    private String businessDescription;

    /**
     * 模板类型：agent 表示智能体提示词模板，skill 表示技能提示词模板。
     */
    private String templateType = "agent";

    public String getBusinessDescription() {
        return businessDescription;
    }

    public void setBusinessDescription(String businessDescription) {
        this.businessDescription = businessDescription;
    }

    public String getTemplateType() {
        return templateType;
    }

    public void setTemplateType(String templateType) {
        this.templateType = templateType;
    }
}
