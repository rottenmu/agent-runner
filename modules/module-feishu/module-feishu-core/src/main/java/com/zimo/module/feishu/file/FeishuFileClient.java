package com.zimo.module.feishu.file;

public interface FeishuFileClient {
    FeishuFileDownloadResult downloadMessageFile(String messageId, String fileKey);
}
