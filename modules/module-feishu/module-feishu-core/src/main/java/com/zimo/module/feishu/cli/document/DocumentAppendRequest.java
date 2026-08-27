package com.zimo.module.feishu.cli.document;

public class DocumentAppendRequest {
    private final String documentId;
    private final String blockId;
    private final String content;

    public DocumentAppendRequest(String documentId, String blockId, String content) {
        this.documentId = requireText(documentId, "documentId must not be blank");
        this.blockId = requireText(blockId, "blockId must not be blank");
        this.content = requireText(content, "content must not be blank");
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getBlockId() {
        return blockId;
    }

    public String getContent() {
        return content;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
