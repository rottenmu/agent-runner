package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.cli.document.DocumentCreateRequest;
import com.zimo.module.feishu.cli.document.FeishuDocumentCliService;
import com.zimo.starter.ai.skill.AiSkill;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.util.Map;
import java.util.Objects;

public class FeishuDocumentCreateAiSkill extends FeishuAiSkillSupport implements AiSkill {
    private final FeishuDocumentCliService documentCliService;

    public FeishuDocumentCreateAiSkill(FeishuDocumentCliService documentCliService) {
        this.documentCliService = Objects.requireNonNull(documentCliService, "documentCliService must not be null");
    }

    @Override
    public String name() {
        return "feishu_document_create";
    }

    @Override
    public String description() {
        return "创建飞书文档";
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        try {
            return result(documentCliService.createDocument(new DocumentCreateRequest(
                    requireText(arguments, "title"),
                    text(arguments, "folderToken"))));
        } catch (Exception e) {
            return fail(e);
        }
    }
}
