package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.mapper.AiAgentCapabilityMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 智能体能力配置服务实现。
 *
 * <p>能力项以 JSON 字符串持久化，读取时与默认配置合并，保证前端始终拿到完整结构。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@Service
public class AiAgentCapabilityServiceImpl extends ServiceImpl<AiAgentCapabilityMapper, AiAgentCapability>
        implements AiAgentCapabilityService {

    private final ObjectMapper objectMapper;

    public AiAgentCapabilityServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> getCapability(String agentId) {
        Map<String, Object> result = new LinkedHashMap<>();
        AiAgentCapability capability = getById(agentId);
        result.put("goalDecomposition", merge(capability == null ? null : capability.getGoalDecomposition(),
                "{\"enabled\":false,\"maxSubGoals\":5}"));
        result.put("intentRecognition", merge(capability == null ? null : capability.getIntentRecognition(),
                "{\"enabled\":false,\"threshold\":0.6}"));
        result.put("clarification", merge(capability == null ? null : capability.getClarification(),
                "{\"enabled\":false,\"maxRounds\":3,\"minConfidence\":0.5}"));
        result.put("parameterExtraction", merge(capability == null ? null : capability.getParameterExtraction(),
                "{\"enabled\":false,\"schema\":\"\"}"));
        result.put("qaMemory", merge(capability == null ? null : capability.getQaMemory(),
                "{\"enabled\":false,\"scope\":\"agent\",\"maxEntries\":200}"));
        return result;
    }

    @Override
    public void saveCapability(String agentId, Map<String, Object> config) {
        if (!StringUtils.hasText(agentId)) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        if (config == null) {
            config = Map.of();
        }
        AiAgentCapability capability = getById(agentId);
        if (capability == null) {
            capability = new AiAgentCapability();
            capability.setAgentId(agentId);
        }
        capability.setGoalDecomposition(writeConfig(config.get("goalDecomposition")));
        capability.setIntentRecognition(writeConfig(config.get("intentRecognition")));
        capability.setClarification(writeConfig(config.get("clarification")));
        capability.setParameterExtraction(writeConfig(config.get("parameterExtraction")));
        capability.setQaMemory(writeConfig(config.get("qaMemory")));
        capability.setUpdatedAt(LocalDateTime.now());
        saveOrUpdate(capability);
    }

    private String writeConfig(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof String text) {
                return StringUtils.hasText(text) ? text : null;
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("能力配置不是合法 JSON: " + e.getMessage(), e);
        }
    }

    /** 解析存储的 JSON 并与默认配置合并。 */
    private Object merge(String stored, String defaults) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (StringUtils.hasText(defaults)) {
            try {
                result.putAll(objectMapper.readValue(defaults, Map.class));
            } catch (Exception ignored) {
                // 默认配置不合法时忽略
            }
        }
        if (StringUtils.hasText(stored)) {
            try {
                JsonNode node = objectMapper.readTree(stored);
                if (node.isObject()) {
                    node.fields().forEachRemaining(e -> result.put(e.getKey(), parseValue(e.getValue())));
                }
            } catch (Exception ignored) {
                // 存储内容不合法时保留默认值
            }
        }
        return result;
    }

    private Object parseValue(JsonNode node) {
        if (node.isNumber()) {
            return node.isIntegralNumber() ? node.asLong() : node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }
}
