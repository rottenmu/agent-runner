package com.zimo.framework.ai.interop;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import java.util.ArrayList;
import java.util.List;

/**
 * 外部 harness 子智能体 provider（dsh A8 meta-harness）。
 *
 * <p>从 agentConfig 的 {@code externalHarness.tasks[]} 数组解析外部 harness 节点，
 * 转换为 AgentScope 2.0.2 {@link SubagentDeclaration}（remote url/headers 形态），
 * 供 graph 编排/子 agent 分发出。配置示例：</p>
 * <pre>{@code
 * {
 *   "externalHarness": {
 *     "tasks": [
 *       {
 *         "name": "claude-code",
 *         "description": "交由 Claude Code 评审",
 *         "url": "https://harness.example.com/run",
 *         "headers": { "Authorization": "Bearer xxx" },
 *         "model": "claude-sonnet-4",
 *         "maxIters": 10,
 *         "remoteStreaming": true
 *       }
 *     ]
 *   }
 * }
 * }</pre>
 * 装配时由 {@code localExternalHarnessProvider()} 以无参组件提供；业务可按需替换实现。
 *
 * @author WorkBuddy
 * @since 2026-08-24
 */
public interface ExternalHarnessSubagentProvider {

    /** 解析 model（若 Model 参数被使用，可提供默认模型名）。 */
    default String defaultModel() {
        return null;
    }

    /**
     * 从 agentConfig 提取外部 harness 子智能体声明。
     *
     * @param config 智能体类型专属配置 JSON，可能为空
     * @return 有效节点列表；配置缺失或节点非法时返回空列表
     */
    List<ExternalHarnessSubagent> extract(JsonNode config);

    /**
     * 将声明转换为 AgentScope SubagentDeclaration（remote 形态）。
     * 基于 {@code url}/{@code headers}/{@code model}/{@code maxIters}/{@code remoteStreaming}。
     *
     * @param subagent 外部 harness 子智能体声明
     * @return AgentScope 子智能体声明
     */
    default SubagentDeclaration toDeclaration(ExternalHarnessSubagent subagent) {
        if (subagent == null || !subagent.valid()) {
            throw new IllegalArgumentException("external harness subagent 声明不合法");
        }
        SubagentDeclaration.Builder builder = SubagentDeclaration.builder()
                .name(subagent.name())
                .description(blankToDefault(subagent.description(),
                        "外部 harness 子智能体 " + subagent.name()))
                .url(subagent.url())
                .headers(subagent.headers());
        if (subagent.model() != null && !subagent.model().isBlank()) {
            builder.model(subagent.model());
        }
        if (subagent.maxIters() > 0) {
            builder.maxIters(subagent.maxIters());
        }
        if (subagent.remoteStreaming()) {
            builder.remoteStreaming(true);
        }
        return builder.build();
    }

    /** 默认实现：解析 {@code externalHarness.tasks[]}。 */
    static ExternalHarnessSubagentProvider defaults() {
        return new ExternalHarnessSubagentProvider() {
            @Override
            public List<ExternalHarnessSubagent> extract(JsonNode config) {
                List<ExternalHarnessSubagent> result = new ArrayList<>();
                if (config == null || !config.path("externalHarness").isObject()) {
                    return result;
                }
                JsonNode tasks = config.path("externalHarness").path("tasks");
                if (!tasks.isArray()) {
                    return result;
                }
                tasks.forEach(task -> {
                    ExternalHarnessSubagent subagent = ExternalHarnessSubagent.fromJson(task);
                    if (subagent.valid()) {
                        result.add(subagent);
                    }
                });
                return result;
            }
        };
    }

    /** 与 {@code defaults()} 等价的无参工厂（供 Spring 装配）。 */
    static ExternalHarnessSubagentProvider localDefault() {
        return defaults();
    }

    /** 空串回退默认值。 */
    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}