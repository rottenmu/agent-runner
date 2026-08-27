package com.zimo.module.feishu.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/api/feishu/events")
public class FeishuEventController {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String URL_VERIFICATION = "url_verification";
    private static final String MESSAGE_RECEIVE_EVENT = "im.message.receive_v1";

    private final FeishuEventProperties properties;
    private final FeishuEventHandler eventHandler;

    public FeishuEventController(FeishuEventProperties properties, FeishuEventHandler eventHandler) {
        this.properties = properties;
        this.eventHandler = eventHandler;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> receive(@RequestBody Map<String, Object> payload) {
        verifyToken(payload);

        if (URL_VERIFICATION.equals(asString(payload.get("type")))) {
            return ResponseEntity.ok(Map.of("challenge", asString(payload.get("challenge"))));
        }

        if (MESSAGE_RECEIVE_EVENT.equals(extractEventType(payload)) && hasMentions(payload)) {
            eventHandler.handleBotMention(toBotMentionEvent(payload));
        }

        return ResponseEntity.ok(Map.of("code", 0));
    }

    private void verifyToken(Map<String, Object> payload) {
        String token = extractToken(payload);
        if (!StringUtils.hasText(properties.getVerificationToken()) || !properties.getVerificationToken().equals(token)) {
            throw new ResponseStatusException(FORBIDDEN, "Invalid feishu verification token");
        }
    }

    private static String extractToken(Map<String, Object> payload) {
        String rootToken = asString(payload.get("token"));
        if (StringUtils.hasText(rootToken)) {
            return rootToken;
        }
        return asString(asMap(payload.get("header")).get("token"));
    }

    private static String extractEventType(Map<String, Object> payload) {
        String rootEventType = asString(payload.get("event_type"));
        if (StringUtils.hasText(rootEventType)) {
            return rootEventType;
        }
        return asString(asMap(payload.get("header")).get("event_type"));
    }

    private static boolean hasMentions(Map<String, Object> payload) {
        return !asList(message(payload).get("mentions")).isEmpty();
    }

    private static FeishuBotMentionEvent toBotMentionEvent(Map<String, Object> payload) {
        Map<String, Object> event = asMap(payload.get("event"));
        Map<String, Object> message = asMap(event.get("message"));
        Map<String, Object> senderId = asMap(asMap(event.get("sender")).get("sender_id"));
        return new FeishuBotMentionEvent(
                asString(message.get("message_id")),
                asString(message.get("chat_id")),
                extractText(asString(message.get("content"))),
                asString(senderId.get("open_id")),
                asString(senderId.get("user_id")));
    }

    private static Map<String, Object> message(Map<String, Object> payload) {
        return asMap(asMap(payload.get("event")).get("message"));
    }

    private static String extractText(String contentJson) {
        if (!StringUtils.hasText(contentJson)) {
            return "";
        }
        try {
            return asString(OBJECT_MAPPER.readValue(contentJson, new TypeReference<Map<String, Object>>() {}).get("text"));
        } catch (Exception e) {
            return contentJson;
        }
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
}
