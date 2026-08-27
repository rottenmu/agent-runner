package com.zimo.module.feishu.cli;

public class FeishuCliException extends RuntimeException {
    public FeishuCliException(String message) {
        super(message);
    }

    public FeishuCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
