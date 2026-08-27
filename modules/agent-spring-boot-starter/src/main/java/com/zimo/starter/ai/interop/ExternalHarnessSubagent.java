package com.zimo.starter.ai.interop;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * 外部 harness 子智能体节点声明（dsh A8 meta-harness）。
 *
 * <p>在 agentConfig 中声明外部 harness 节点（Claude Code / Codex / 自建 harness），
 * 通过子 agent provider 在编排时构建远端 {@code SubagentDeclaration}，交给
 * AgentScope 2.0.2 的 remote 执行机制（url/headers）调度。</p>
 *
 * @param name        节点名称（声明唯一，必填）
 * @param description 节点职责描述
 * @param url         外部 harness 远程端点（必填，http/https）
 * @param headers     请求头（如 Bearer token），可空
 * @param model       远端模型名覆盖，可空
 * @param maxIters    远端最大迭代次数，正数生效
 * @param remoteStreaming 是否开启远端 streaming
 */
public record ExternalHarnessSubagent(
        String name,
        String description,
        String url,
        Map<String, String> headers,
        String model,
        int maxIters,
        boolean remoteStreaming) {

    public ExternalHarnessSubagent {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public boolean valid() {
        return name != null && !name.isBlank()
                && url != null && !url.isBlank();
    }

    /** 从 agentConfig {@code externalHarness.tasks[]} 节点解析。 */
    public static ExternalHarnessSubagent fromJson(JsonNode node) {
        String name = node.path("name").asText();
        String description = node.path("description").asText("");
        String url = node.path("url").asText("");
        String model = node.path("model").asText(null);
        int maxIters = node.path("maxIters").asInt(0);
        boolean remoteStreaming = node.path("remoteStreaming").asBoolean(false);

        Map<String, String> headers = Map.of();
        if (node.path("headers").isObject()) {
            java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
            node.path("headers").fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual()) {
                    map.put(entry.getKey(), entry.getValue().asText());
                }
            });
            headers = Map.copyOf(map);
        }
        return new ExternalHarnessSubagent(
                name, description, url, headers, model, maxIters, remoteStreaming);
    }
}