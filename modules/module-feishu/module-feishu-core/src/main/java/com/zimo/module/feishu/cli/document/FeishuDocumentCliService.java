package com.zimo.module.feishu.cli.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zimo.module.feishu.cli.FeishuCliCommandRequest;
import com.zimo.module.feishu.cli.FeishuCliCommandResult;
import com.zimo.module.feishu.cli.FeishuCliTemplate;

import java.util.Objects;

public class FeishuDocumentCliService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final FeishuCliTemplate template;

    public FeishuDocumentCliService(FeishuCliTemplate template) {
        this.template = Objects.requireNonNull(template, "template must not be null");
    }

    public FeishuCliCommandResult createDocument(DocumentCreateRequest request) {
        FeishuCliCommandRequest command = FeishuCliCommandRequest
                .api("document", "POST", "/open-apis/docx/v1/documents")
                .withData("title", request.getTitle());
        if (request.getFolderToken() != null && !request.getFolderToken().trim().isEmpty()) {
            command = command.withData("folder_token", request.getFolderToken());
        }
        return template.execute(command);
    }

    public FeishuCliCommandResult appendContent(DocumentAppendRequest request) {
        return template.execute(FeishuCliCommandRequest
                .api("document", "POST", "/open-apis/docx/v1/documents/"
                        + request.getDocumentId() + "/blocks/" + request.getBlockId() + "/children")
                .withData("content", request.getContent()));
    }

    public FeishuCliCommandResult getDocumentLink(String documentToken) {
        if (documentToken == null || documentToken.trim().isEmpty()) {
            throw new IllegalArgumentException("documentToken must not be blank");
        }
        ObjectNode json = OBJECT_MAPPER.createObjectNode();
        json.put("url", "https://feishu.cn/docx/" + documentToken);
        return FeishuCliCommandResult.success(json.toString(), json, 0L, 1);
    }
}
