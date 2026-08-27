package com.zimo.module.feishu.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.feishu.cli.FeishuCliCommandResult;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

abstract class FeishuAiSkillSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected String text(Map<String, Object> arguments, String key) {
        Object value = arguments == null ? null : arguments.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    protected String requireText(Map<String, Object> arguments, String key) {
        String value = text(arguments, key);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return value;
    }

    protected Map<String, Object> map(Map<String, Object> arguments, String key) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        if (value instanceof String text && !text.trim().isEmpty()) {
            try {
                return OBJECT_MAPPER.readValue(text, new TypeReference<>() {
                });
            } catch (Exception e) {
                throw new IllegalArgumentException(key + " must be a JSON object");
            }
        }
        return Collections.emptyMap();
    }

    protected AiSkillResult result(FeishuCliCommandResult result) {
        if (result == null) {
            return AiSkillResult.fail("飞书 CLI 未返回结果");
        }
        if (result.isSuccess()) {
            return AiSkillResult.ok(result.getStdout());
        }
        return AiSkillResult.fail("飞书 CLI 调用失败：" + result.getErrorMessage());
    }

    protected AiSkillResult fail(Exception e) {
        String message = e.getMessage();
        return AiSkillResult.fail(StrUtil.isBlank(message) ? e.getClass().getSimpleName() : message);
    }
}
