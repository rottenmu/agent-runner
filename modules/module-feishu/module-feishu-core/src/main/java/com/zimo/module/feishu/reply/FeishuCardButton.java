package com.zimo.module.feishu.reply;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FeishuCardButton {
    private final String text;
    private final String type;
    private final String url;
    private final Map<String, Object> value;

    private FeishuCardButton(String text, String type, String url, Map<String, Object> value) {
        this.text = text;
        this.type = hasText(type) ? type : "default";
        this.url = url;
        this.value = value == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    public static FeishuCardButton url(String text, String type, String url) {
        return new FeishuCardButton(text, type, url, Collections.emptyMap());
    }

    public static FeishuCardButton value(String text, String type, Map<String, Object> value) {
        return new FeishuCardButton(text, type, null, value);
    }

    public String getText() {
        return text;
    }

    public String getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, Object> getValue() {
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
