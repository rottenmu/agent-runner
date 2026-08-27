package com.zimo.module.feishu.gateway;

import java.util.List;

public class FeishuAgentCommandRoute {
    private final String name;
    private final List<String> prefixes;
    private final List<String> keywords;
    private final int priority;

    public FeishuAgentCommandRoute(String name, List<String> prefixes, List<String> keywords, int priority) {
        this.name = name;
        this.prefixes = prefixes == null ? List.of() : List.copyOf(prefixes);
        this.keywords = keywords == null ? List.of() : List.copyOf(keywords);
        this.priority = priority;
    }

    public boolean matches(String commandText) {
        if (commandText == null) {
            return false;
        }
        String normalized = commandText.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        return prefixes.stream()
                .filter(FeishuAgentCommandRoute::hasText)
                .anyMatch(normalized::startsWith)
                || keywords.stream()
                .filter(FeishuAgentCommandRoute::hasText)
                .anyMatch(normalized::contains);
    }

    public String getName() {
        return name;
    }

    public List<String> getPrefixes() {
        return prefixes;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public int getPriority() {
        return priority;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
