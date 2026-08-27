package com.zimo.module.feishu.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FeishuChannelMessageParser {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEXT_MESSAGE_TYPE = "text";
    private static final String FILE_MESSAGE_TYPE = "file";
    private static final String EXCEL_PROJECT_COMMAND = "\u89e3\u6790Excel\u751f\u6210\u9879\u76ee";

    public Optional<FeishuAgentCommandMessage> parse(Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Collections.emptyMap() : payload;
        Map<String, Object> event = asMap(safePayload.get("event"));
        Map<String, Object> message = asMap(event.get("message"));
        String messageType = getString(message, "message_type", "messageType");
        if (!TEXT_MESSAGE_TYPE.equals(messageType) && !FILE_MESSAGE_TYPE.equals(messageType)) {
            return Optional.empty();
        }
        String chatType = getString(message, "chat_type", "chatType");
        boolean mentionedBot = !asList(message.get("mentions")).isEmpty();
        if (!mentionedBot && "group".equalsIgnoreCase(chatType) && !FILE_MESSAGE_TYPE.equals(messageType)) {
            return Optional.empty();
        }

        Map<String, Object> senderId = getMap(asMap(event.get("sender")), "sender_id", "senderId");
        Map<String, Object> content = content(getString(message, "content"));
        String rawText = TEXT_MESSAGE_TYPE.equals(messageType) ? asString(content.get("text")) : "";
        Map<String, Object> attributes = attributes(messageType, content);
        String commandText = TEXT_MESSAGE_TYPE.equals(messageType) ? cleanMentionText(rawText) : fileCommandText(attributes);
        if (FILE_MESSAGE_TYPE.equals(messageType) && !hasText(commandText)) {
            return Optional.empty();
        }
        return Optional.of(new FeishuAgentCommandMessage(
                getString(message, "message_id", "messageId"),
                getString(message, "chat_id", "chatId"),
                chatType,
                getString(asMap(safePayload.get("header")), "tenant_key", "tenantKey"),
                getString(senderId, "user_id", "userId"),
                getString(senderId, "open_id", "openId"),
                getString(senderId, "union_id", "unionId"),
                rawText,
                commandText,
                messageType,
                mentionedBot,
                attributes
        ));
    }

    private static Map<String, Object> content(String contentJson) {
        if (!hasText(contentJson)) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(contentJson, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of("text", contentJson);
        }
    }

    private static Map<String, Object> attributes(String messageType, Map<String, Object> content) {
        if (!FILE_MESSAGE_TYPE.equals(messageType)) {
            return Collections.emptyMap();
        }
        String fileName = getString(content, "file_name", "fileName", "name");
        String fileKey = getString(content, "file_key", "fileKey");
        if (!hasText(fileName) && !hasText(fileKey)) {
            return Collections.emptyMap();
        }
        java.util.LinkedHashMap<String, Object> attributes = new java.util.LinkedHashMap<>();
        attributes.put("messageType", messageType);
        if (hasText(fileKey)) {
            attributes.put("fileKey", fileKey);
        }
        if (hasText(fileName)) {
            attributes.put("sourceName", fileName);
            attributes.put("fileName", fileName);
        }
        return attributes;
    }

    private static String fileCommandText(Map<String, Object> attributes) {
        String fileName = getString(attributes, "sourceName", "fileName");
        String normalized = fileName.toLowerCase();
        if (normalized.endsWith(".xlsx") || normalized.endsWith(".xls")) {
            return EXCEL_PROJECT_COMMAND;
        }
        return "";
    }

    private static String cleanMentionText(String rawText) {
        if (!hasText(rawText)) {
            return "";
        }
        return rawText.replaceAll("@\\S+", "").trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    private static List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return Collections.emptyList();
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, Object> getMap(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Map<String, Object> value = asMap(source.get(key));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return Collections.emptyMap();
    }

    private static String getString(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String value = asString(source.get(key));
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
