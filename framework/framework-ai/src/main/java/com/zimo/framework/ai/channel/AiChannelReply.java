package com.zimo.framework.ai.channel;

public record AiChannelReply(Type type, String content) {
    public enum Type {
        TEXT
    }

    public static AiChannelReply text(String content) {
        return new AiChannelReply(Type.TEXT, content == null ? "" : content);
    }
}
