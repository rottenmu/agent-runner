package com.zimo.module.ai.controller;

import com.zimo.framework.ai.skill.AiSkillDescriptor;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import com.zimo.framework.ai.skill.AiSkillResult;
import com.zimo.framework.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 技能直调接口：供管理端"AI 对话测试"等场景直接执行技能（绕过模型）。
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
@RestController
@RequestMapping("/api/biz/ai/skills")
public class AiSkillCallController {

    private final AiSkillRegistry skillRegistry;

    public AiSkillCallController(AiSkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    /**
     * 技能列表（含描述，供选择器使用）。
     */
    @GetMapping("/callable")
    public ApiResponse<List<AiSkillDescriptor>> callableSkills() {
        return ApiResponse.ok(skillRegistry.list());
    }

    /**
     * 直接调用技能。
     *
     * @param name      技能名称
     * @param body      {"arguments": {"orderId": "20260801"}}
     * @return {"success": true/false, "content": "结果或错误信息"}
     */
    @PostMapping("/{name}/call")
    public ApiResponse<Map<String, Object>> callSkill(@PathVariable String name,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        Object rawArgs = body == null ? null : body.get("arguments");
        Map<String, Object> arguments = rawArgs instanceof Map<?, ?> map
                ? castArguments(map) : Map.of();
        AiSkillResult result = traceCall(name, arguments);
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("success", result != null && result.success());
        response.put("content", result == null ? "技能未返回结果" : result.content());
        return ApiResponse.ok(response);
    }

    /**
     * 聊天式技能调用：用户自然语言 → 自动解析参数 → 执行技能。
     *
     * <p>解析策略：消息本身是 JSON 对象则直接作为参数；否则按常见单参名
     * （text/message/content/query/input/keywords）尝试调用，失败时返回技能
     * 错误信息并提示补 JSON 参数。返回结构与普通调用一致，便于聊天气泡展示。</p>
     *
     * @param name 技能名称
     * @param body  {"message": "把 hello 转成大写"}
     */
    @PostMapping("/{name}/chat")
    public ApiResponse<Map<String, Object>> chatSkill(@PathVariable String name,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        String message = body == null ? null : String.valueOf(body.getOrDefault("message", ""));
        Map<String, Object> arguments = resolveArguments(message);

        // 尝试调用；缺参失败时给出友好提示
        AiSkillResult result = traceCall(name, arguments);
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        if (result != null && result.success()) {
            response.put("success", true);
            response.put("content", result.content());
            response.put("arguments", arguments);
            return ApiResponse.ok(response);
        }
        String error = result == null ? "技能未返回结果" : result.content();
        response.put("success", false);
        response.put("content", error);
        response.put("arguments", arguments);
        response.put("hint", "调用失败，可用 JSON 参数重试，如：{\"orderId\": \"20260801\"}");
        return ApiResponse.ok(response);
    }

    /**
     * 带 trace 包裹的技能调用：直调场景没有 agent 会话上下文，
     * 显式 begin/end 建立执行链路，使工具流水线打点不丢失。
     */
    private AiSkillResult traceCall(String name, Map<String, Object> arguments) {
        com.zimo.framework.ai.observ.TraceCollector.begin(
                "direct-skill", "skill", name, "skill_call", "direct");
        try {
            return skillRegistry.call(name, arguments);
        } finally {
            com.zimo.framework.ai.observ.TraceCollector.end("ok", name,
                    String.valueOf(arguments), 0, 0);
        }
    }

    /** 消息 → 参数：JSON 对象直用；否则按常见单参名兜底填充。 */
    private Map<String, Object> resolveArguments(String message) {
        if (message == null || message.isBlank()) {
            return Map.of();
        }
        String trimmed = message.trim();
        // 1. 消息本身是 JSON 对象
        if (trimmed.startsWith("{")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> parsed = mapper.readValue(trimmed,
                        new com.fasterxml.jackson.core.type.TypeReference<>() {
                        });
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            } catch (Exception ignored) {
                // 非 JSON，走兜底
            }
        }
        // 2. 常见单参名兜底
        Map<String, Object> arguments = new java.util.LinkedHashMap<>();
        arguments.put("text", trimmed);
        arguments.put("message", trimmed);
        arguments.put("content", trimmed);
        arguments.put("query", trimmed);
        arguments.put("input", trimmed);
        return arguments;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castArguments(Map<?, ?> map) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
