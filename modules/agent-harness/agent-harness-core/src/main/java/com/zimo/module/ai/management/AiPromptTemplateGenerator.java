package com.zimo.module.ai.management;

import org.springframework.util.StringUtils;

/**
 * AI 管理模块的提示词模板生成器，根据业务简述确定性生成 CoSTAR 草稿。
 *
 * <p>当前实现不接入真实大模型，避免测试和本地开发依赖外部服务；后续可在保持返回字段语义不变的前提下替换生成策略。</p>
 *
 * @author xingju
 * @since 2026-07-08
 */
public class AiPromptTemplateGenerator {
    /**
     * 根据业务简述生成不入库的提示词模板草稿。
     *
     * @param businessDescription 业务简述，必须包含有效文本
     * @return sourceType 为 generated 的 CoSTAR 草稿
     * @throws IllegalArgumentException 当业务简述为空时抛出
     */
    public AiPromptTemplate generate(String businessDescription) {
        String description = requireText(businessDescription, "businessDescription");
        String subject = extractSubject(description);
        AiPromptTemplate template = new AiPromptTemplate();
        template.setTemplateName(subject + "提示词");
        template.setDescription("根据业务简述生成的 CoSTAR 提示词草稿");
        template.setContextText("围绕“" + description + "”梳理业务背景、输入信息、处理边界和已有约束。");
        template.setObjectiveText("帮助使用者完成“" + subject + "”相关判断、分析或处理建议，并输出可执行结论。");
        template.setStyleText("采用结构化、可追溯、便于业务人员复核的表达方式，优先使用分点说明。");
        template.setToneText("保持专业、审慎、清晰的语气，对不确定信息明确提示需要人工确认。");
        template.setAudienceText("面向负责“" + subject + "”的一线业务人员、主管和需要复核结果的管理者。");
        template.setResponseText("按“结论、依据、风险、下一步建议”的顺序输出，避免编造未提供的数据。");
        template.setSourceType("generated");
        template.setBusinessDescription(description);
        template.setEnabled(true);
        template.setDeleted(false);
        return template;
    }

    private String extractSubject(String description) {
        int limit = Math.min(description.length(), 12);
        String subject = description.substring(0, limit).trim();
        if (subject.endsWith("生成") && subject.length() > 2) {
            return subject.substring(0, subject.length() - 2);
        }
        return subject;
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
