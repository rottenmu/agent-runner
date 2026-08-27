package com.zimo.module.feishu.reply;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FeishuCardTemplateFactory {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String buildActionCard(String title, String markdown, List<FeishuCardButton> buttons) {
        requireText(title, "title must not be blank");
        requireText(markdown, "markdown must not be blank");

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", Map.of("wide_screen_mode", true));
        card.put("header", Map.of(
                "template", "blue",
                "title", Map.of("tag", "plain_text", "content", title)
        ));

        List<Object> elements = new ArrayList<>();
        elements.add(Map.of("tag", "markdown", "content", markdown));
        if (buttons != null && !buttons.isEmpty()) {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("tag", "action");
            action.put("layout", "bisected");
            List<Object> actions = new ArrayList<>();
            for (FeishuCardButton button : buttons) {
                actions.add(toButton(button));
            }
            action.put("actions", actions);
            elements.add(action);
        }
        card.put("elements", elements);

        try {
            return OBJECT_MAPPER.writeValueAsString(card);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("card cannot be serialized", e);
        }
    }

    private static Map<String, Object> toButton(FeishuCardButton button) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tag", "button");
        item.put("text", Map.of("tag", "plain_text", "content", button.getText()));
        item.put("type", button.getType());
        if (hasText(button.getUrl())) {
            item.put("url", button.getUrl());
        }
        if (!button.getValue().isEmpty()) {
            item.put("value", button.getValue());
        }
        return item;
    }

    private static void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
