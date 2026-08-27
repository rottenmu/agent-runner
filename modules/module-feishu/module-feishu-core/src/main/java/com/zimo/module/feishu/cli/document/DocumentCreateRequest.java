package com.zimo.module.feishu.cli.document;

public class DocumentCreateRequest {
    private final String title;
    private final String folderToken;

    public DocumentCreateRequest(String title, String folderToken) {
        this.title = requireText(title, "title must not be blank");
        this.folderToken = folderToken;
    }

    public String getTitle() {
        return title;
    }

    public String getFolderToken() {
        return folderToken;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
