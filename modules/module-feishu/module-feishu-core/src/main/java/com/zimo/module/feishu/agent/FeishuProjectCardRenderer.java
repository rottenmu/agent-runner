package com.zimo.module.feishu.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FeishuProjectCardRenderer {
    private static final String DEFAULT_TITLE = "Project Result";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public boolean supports(String content) {
        JsonNode root = parse(content);
        return root != null
                && root.isObject()
                && root.path("headers").isArray()
                && root.path("rows").isArray()
                && root.path("buttons").isArray();
    }

    public String render(String content) {
        JsonNode root = parse(content);
        if (root == null) {
            throw new IllegalArgumentException("Project card payload must be valid JSON");
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", "2.0");
        card.put("config", Map.of(
                "wide_screen_mode", true,
                "width_mode", "fill",
                "streaming_mode", true));
        card.put("header", Map.of(
                "template", "blue",
                "title", Map.of(
                        "tag", "plain_text",
                        "content", text(root.path("title"), DEFAULT_TITLE))));
        card.put("body", Map.of("elements", elements(root)));
        try {
            return OBJECT_MAPPER.writeValueAsString(card);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render Feishu project card", e);
        }
    }

    private List<Map<String, Object>> elements(JsonNode root) {
        List<Map<String, Object>> elements = new java.util.ArrayList<>();
        Map<String, Object> statuses = statusColumns(root.path("rows"));
        if (!statuses.isEmpty()) {
            elements.add(statuses);
        }
        if (hasText(text(root.path("summary"), ""))) {
            elements.add(Map.of(
                    "tag", "column_set",
                    "flex_mode", "none",
                    "background_style", "default",
                    "columns", List.of(Map.of(
                            "tag", "column",
                            "width", "weighted",
                            "weight", 1,
                            "elements", List.of(Map.of(
                                    "tag", "div",
                                    "text", Map.of(
                                            "tag", "plain_text",
                                            "content", text(root.path("summary"), ""))))))));
        }
        elements.add(table(root));
        Map<String, Object> buttons = buttons(root.path("buttons"));
        if (!buttons.isEmpty()) {
            elements.add(buttons);
        }
        return elements;
    }

    private Map<String, Object> statusColumns(JsonNode rows) {
        List<Map<String, Object>> columns = new java.util.ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode status = row.path("status");
            String statusText = text(status.path("text"), "");
            if (!hasText(statusText)) {
                continue;
            }
            String color = text(status.path("color"), "green");
            columns.add(Map.of(
                    "tag", "column",
                    "width", "weighted",
                    "weight", 1,
                    "elements", List.of(Map.of(
                            "tag", "div",
                            "text", Map.of(
                                    "tag", "lark_md",
                                    "content", "<font color='" + color + "'>\u25A0</font> " + statusText)))));
            if (columns.size() >= 3) {
                break;
            }
        }
        if (columns.isEmpty()) {
            return Map.of();
        }
        return Map.of(
                "tag", "column_set",
                "flex_mode", "none",
                "background_style", "default",
                "columns", columns);
    }

    private Map<String, Object> table(JsonNode root) {
        ArrayNode headers = (ArrayNode) root.path("headers");
        List<Map<String, Object>> columns = new java.util.ArrayList<>();
        for (JsonNode header : headers) {
            String key = text(header.path("key"), "");
            if (!hasText(key)) {
                continue;
            }
            columns.add(new LinkedHashMap<>(Map.of(
                    "name", key,
                    "display_name", text(header.path("label"), key),
                    "data_type", "text",
                    "width", "auto")));
        }

        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (JsonNode row : root.path("rows")) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (Map<String, Object> column : columns) {
                String key = String.valueOf(column.get("name"));
                item.put(key, cellValue(row.path(key)));
            }
            rows.add(item);
        }

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("tag", "table");
        table.put("columns", columns);
        table.put("rows", rows);
        table.put("page_size", 10);
        table.put("header_style", Map.of("bold", true));
        return table;
    }

    private Map<String, Object> buttons(JsonNode buttons) {
        List<Map<String, Object>> columns = new java.util.ArrayList<>();
        for (JsonNode button : buttons) {
            String text = text(button.path("text"), "");
            String action = text(button.path("action"), "");
            if (!hasText(text) || !hasText(action)) {
                continue;
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("action", action);
            value.put("value", toObject(button.path("value")));
            Map<String, Object> buttonElement = Map.of(
                    "tag", "button",
                    "text", Map.of(
                            "tag", "plain_text",
                            "content", text),
                    "type", text(button.path("type"), "primary"),
                    "behaviors", List.of(Map.of(
                            "type", "callback",
                            "value", value)));
            columns.add(Map.of(
                    "tag", "column",
                    "width", "weighted",
                    "weight", 1,
                    "elements", List.of(buttonElement)));
        }
        if (columns.isEmpty()) {
            return Map.of();
        }
        return Map.of(
                "tag", "column_set",
                "flex_mode", "none",
                "background_style", "default",
                "columns", columns);
    }

    private Object cellValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        if (node.isObject()) {
            String text = text(node.path("text"), "");
            if (hasText(text)) {
                return text;
            }
        }
        return jsonText(node);
    }

    private Object toObject(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        if (node.isObject()) {
            Map<String, Object> value = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                value.put(field.getKey(), toObject(field.getValue()));
            }
            return value;
        }
        if (node.isArray()) {
            List<Object> values = new java.util.ArrayList<>();
            for (JsonNode item : node) {
                values.add(toObject(item));
            }
            return values;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    private JsonNode parse(String content) {
        if (!hasText(content)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(content);
        } catch (Exception e) {
            return null;
        }
    }

    private String jsonText(JsonNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return node.asText("");
        }
    }

    private String text(JsonNode node, String defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        String value = node.asText(defaultValue);
        return hasText(value) ? value : defaultValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
