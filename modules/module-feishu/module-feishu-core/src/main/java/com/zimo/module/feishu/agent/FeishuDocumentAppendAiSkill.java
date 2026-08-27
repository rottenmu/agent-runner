package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.cli.document.DocumentAppendRequest;
import com.zimo.module.feishu.cli.document.FeishuDocumentCliService;
import com.zimo.starter.ai.skill.AiSkill;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.util.Map;
import java.util.Objects;

public class FeishuDocumentAppendAiSkill extends FeishuAiSkillSupport implements AiSkill {
    private final FeishuDocumentCliService documentCliService;

    public FeishuDocumentAppendAiSkill(FeishuDocumentCliService documentCliService) {
        this.documentCliService = Objects.requireNonNull(documentCliService, "documentCliService must not be null");
    }

    @Override
    public String name() {
        return "feishu_document_append";
    }

    @Override
    public String description() {
        return "向飞书文档追加内容";
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        try {
            return result(documentCliService.appendContent(new DocumentAppendRequest(
                    requireText(arguments, "documentId"),
                    requireText(arguments, "blockId"),
                    requireText(arguments, "content"))));
        } catch (Exception e) {
            return fail(e);
        }
    }
}
