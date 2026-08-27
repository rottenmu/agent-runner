package com.zimo.starter.ai.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * AI 技能注册表，统一维护 Spring Bean 技能和运行时配置的 API 技能。
 */
public class AiSkillRegistry {
    private final Map<String, AiSkill> skills = new LinkedHashMap<>();
    private final Map<String, AiApiSkill> apiSkills = new LinkedHashMap<>();
    private final Map<String, AiApiSkillConfig> apiSkillConfigs = new LinkedHashMap<>();
    private final RestClient.Builder restClientBuilder;
    private final ToolPipeline pipeline;

    public AiSkillRegistry(List<AiSkill> registeredSkills) {
        this(registeredSkills, RestClient.builder(), ToolPipeline.empty());
    }

    public AiSkillRegistry(List<AiSkill> registeredSkills, RestClient.Builder restClientBuilder) {
        this(registeredSkills, restClientBuilder, ToolPipeline.empty());
    }

    public AiSkillRegistry(List<AiSkill> registeredSkills, RestClient.Builder restClientBuilder,
                           ToolPipeline pipeline) {
        this.restClientBuilder = restClientBuilder == null ? RestClient.builder() : restClientBuilder;
        this.pipeline = pipeline == null ? ToolPipeline.empty() : pipeline;
        if (registeredSkills == null) {
            return;
        }
        for (AiSkill skill : registeredSkills) {
            if (skill != null && skill.name() != null && !skill.name().isBlank()) {
                skills.put(skill.name(), skill);
            }
        }
    }

    /**
     * 查询全部可管理技能描述。
     *
     * @return Spring Bean 技能和 API 技能的描述列表，按注册顺序返回
     */
    public synchronized List<AiSkillDescriptor> list() {
        List<AiSkillDescriptor> descriptors = new ArrayList<>(skills.values().stream()
                .map(skill -> new AiSkillDescriptor(skill.name(), skill.description(), skill.readOnly(),
                        skill.inputParameters()))
                .toList());
        descriptors.addAll(apiSkills.values().stream()
                .map(skill -> new AiSkillDescriptor(skill.name(), skill.description(), skill.readOnly(),
                        skill.inputParameters()))
                .toList());
        return descriptors;
    }

    /**
     * 调用指定技能。
     *
     * @param name 技能名称，不允许为空
     * @param arguments 技能参数，空值按空对象处理
     * @return 技能执行结果；技能不存在时返回失败结果
     */
    public synchronized AiSkillResult call(String name, Map<String, Object> arguments) {
        AiSkill skill = skill(name);
        if (skill == null) {
            return AiSkillResult.fail("Unknown AI skill: " + name);
        }
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        ToolCallContext context = ToolCallContext.of(
                com.zimo.starter.ai.observ.TraceCollector.currentTraceId());
        return pipeline.execute(context, name, args, skill::call);
    }

    /**
     * 注册或更新一个 API 技能。
     *
     * @param config API 技能配置，名称、地址和方法必须合法
     * @return 注册后的 API 技能配置
     */
    public synchronized AiApiSkillConfig registerApiSkill(AiApiSkillConfig config) {
        AiApiSkillConfig normalized = normalize(config);
        apiSkills.put(normalized.name(), new AiApiSkill(normalized, restClientBuilder));
        apiSkillConfigs.put(normalized.name(), normalized);
        return normalized;
    }

    /**
     * 删除运行时配置的 API 技能。
     *
     * @param name 技能名称
     * @return true 表示删除成功，false 表示目标不存在
     */
    public synchronized boolean removeApiSkill(String name) {
        boolean removed = apiSkills.remove(name) != null;
        apiSkillConfigs.remove(name);
        return removed;
    }

    /**
     * 查询 API 技能配置。
     *
     * @param name 技能名称
     * @return API 技能配置；目标不是 API 技能时返回空
     */
    public synchronized Optional<AiApiSkillConfig> apiSkillConfig(String name) {
        return Optional.ofNullable(apiSkillConfigs.get(name));
    }

    /**
     * 判断名称是否已被 Spring Bean 技能占用。
     *
     * @param name 技能名称
     * @return true 表示名称属于静态注册技能
     */
    public synchronized boolean hasBeanSkill(String name) {
        return skills.containsKey(name);
    }

    private AiSkill skill(String name) {
        AiSkill skill = skills.get(name);
        return skill == null ? apiSkills.get(name) : skill;
    }

    private AiApiSkillConfig normalize(AiApiSkillConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("API技能配置不能为空");
        }
        requireText(config.name(), "技能名称不能为空");
        requireText(config.description(), "技能描述不能为空");
        requireText(config.baseUrl(), "API基础地址不能为空");
        requireText(config.path(), "API路径不能为空");
        String method = StringUtils.hasText(config.method()) ? config.method().trim().toUpperCase() : "POST";
        HttpMethodValidator.requireSupported(method);
        return new AiApiSkillConfig(
                config.name().trim(),
                config.description().trim(),
                config.readOnly(),
                config.enabled(),
                config.baseUrl().trim(),
                config.path().trim(),
                method,
                config.headers() == null ? Map.of() : Map.copyOf(config.headers()),
                config.timeoutMillis() > 0 ? config.timeoutMillis() : 3000);
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static final class HttpMethodValidator {
        private static final List<String> SUPPORTED = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

        private static void requireSupported(String method) {
            if (!SUPPORTED.contains(method)) {
                throw new IllegalArgumentException("不支持的API技能HTTP方法: " + method);
            }
        }
    }
}
