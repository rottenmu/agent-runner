package com.zimo.module.feishu.reply;

import com.zimo.module.feishu.log.FeishuMessageLogEntity;
import com.zimo.module.feishu.log.FeishuMessageLogService;
import com.zimo.module.feishu.log.FeishuMessageLogStage;
import com.zimo.module.feishu.message.FeishuMessageResponse;

import java.util.Objects;
import java.util.function.Supplier;

public class FeishuAgentReplyService {
    private static final int MAX_REPLY_PAYLOAD_LENGTH = 2048;

    private final FeishuAgentReplyClient replyClient;
    private final FeishuMessageLogService logService;

    public FeishuAgentReplyService(FeishuAgentReplyClient replyClient) {
        this(replyClient, null);
    }

    public FeishuAgentReplyService(FeishuAgentReplyClient replyClient, FeishuMessageLogService logService) {
        this.replyClient = Objects.requireNonNull(replyClient, "replyClient must not be null");
        this.logService = logService;
    }

    public FeishuMessageResponse replyText(String messageId, String text) {
        requireText(messageId, "messageId must not be blank");
        requireText(text, "text must not be blank");
        return sendAndRecord(messageId, "text", text, () -> replyClient.replyText(messageId, text));
    }

    public FeishuMessageResponse streamText(String messageId, String text, FeishuStreamReplyOptions options) {
        requireText(messageId, "messageId must not be blank");
        requireText(text, "text must not be blank");
        FeishuStreamReplyOptions actualOptions = options == null ? FeishuStreamReplyOptions.defaults() : options;
        int chunkSize = actualOptions.getChunkSize();
        String firstChunk = text.substring(0, Math.min(chunkSize, text.length()));
        FeishuMessageResponse latest = sendAndRecord(
                messageId,
                "stream_text",
                firstChunk,
                () -> replyClient.replyText(messageId, firstChunk)
        );
        if (!latest.isSuccess() || firstChunk.length() == text.length()) {
            return latest;
        }

        String replyMessageId = latest.getMessageId();
        for (int end = firstChunk.length() + chunkSize; end <= text.length() + chunkSize; end += chunkSize) {
            int actualEnd = Math.min(end, text.length());
            sleep(actualOptions);
            String chunk = text.substring(0, actualEnd);
            latest = sendAndRecord(
                    replyMessageId,
                    "stream_text",
                    chunk,
                    () -> replyClient.updateText(replyMessageId, chunk)
            );
            if (!latest.isSuccess() || actualEnd == text.length()) {
                return latest;
            }
        }
        return latest;
    }

    public FeishuMessageResponse replyCard(String messageId, String cardJson) {
        requireText(messageId, "messageId must not be blank");
        requireText(cardJson, "cardJson must not be blank");
        return sendAndRecord(messageId, "card", cardJson, () -> replyClient.replyCard(messageId, cardJson));
    }

    private FeishuMessageResponse sendAndRecord(
            String messageId,
            String replyType,
            String replyPayload,
            Supplier<FeishuMessageResponse> sender
    ) {
        FeishuMessageResponse response = sender.get();
        recordReply(messageId, replyType, replyPayload, response);
        return response;
    }

    private void recordReply(String messageId, String replyType, String replyPayload, FeishuMessageResponse response) {
        if (logService == null) {
            return;
        }
        FeishuMessageLogEntity entity = new FeishuMessageLogEntity();
        entity.setMessageId(messageId);
        entity.setReplyType(replyType);
        entity.setStage(FeishuMessageLogStage.REPLIED.name());
        entity.setSuccess(response != null && response.isSuccess() ? 1 : 0);
        if (response != null && !response.isSuccess()) {
            entity.setErrorCode(response.getCode());
            entity.setErrorMessage(truncate(response.getMessage(), 1024));
        }
        entity.setReplyPayload(truncate(replyPayload, MAX_REPLY_PAYLOAD_LENGTH));
        logService.record(entity);
    }

    private static void sleep(FeishuStreamReplyOptions options) {
        if (options.getInterval().isZero()) {
            return;
        }
        try {
            Thread.sleep(options.getInterval().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
