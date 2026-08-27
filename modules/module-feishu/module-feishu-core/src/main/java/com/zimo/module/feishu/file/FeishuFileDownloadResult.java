package com.zimo.module.feishu.file;

public record FeishuFileDownloadResult(
        boolean success,
        String fileName,
        String contentType,
        byte[] content,
        String errorMessage) {

    public static FeishuFileDownloadResult success(String fileName, String contentType, byte[] content) {
        return new FeishuFileDownloadResult(true, fileName, contentType, content == null ? new byte[0] : content, null);
    }

    public static FeishuFileDownloadResult failure(String errorMessage) {
        return new FeishuFileDownloadResult(false, "", "", new byte[0], errorMessage);
    }
}
