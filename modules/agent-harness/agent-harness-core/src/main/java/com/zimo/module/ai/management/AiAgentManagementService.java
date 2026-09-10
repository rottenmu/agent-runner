package com.zimo.module.ai.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.zimo.framework.common.validation.ValidationUtil;
import cn.hutool.core.map.MapUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.ai.skill.AiApiSkillConfig;
import com.zimo.framework.ai.skill.AiSkillDescriptor;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.util.StringUtils;

/**
 * AI 智能体与技能管理服务。
 *
 * <p>服务维护数据库活动记录的进程内快照，并通过仓储完成单次写入；当前不声明跨仓储 Spring 事务。
 * 所有管理方法串行访问快照。智能体或已绑定技能发生变化时，先通知运行时失效旧 HarnessAgent，
 * 再持久化新配置，避免继续复用旧权限和工具元数据。参数错误统一抛出 {@link IllegalArgumentException}。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public class AiAgentManagementService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {
    };

    private final AiSkillRegistry skillRegistry;
    private final AiManagedSkillConfigRepository skillConfigRepository;
    private final AiManagedAgentRepository agentRepository;
    private final List<AiManagedAgentRuntimeInvalidator> runtimeInvalidators;
    private final Map<String, AiManagedAgent> agents = new LinkedHashMap<>();
    private final Map<String, Long> skillPromptTemplateIds = new LinkedHashMap<>();
    private final Map<String, String> skillAgentIds = new LinkedHashMap<>();
    private final Map<String, Long> skillApiRegistryIds = new LinkedHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    /**
     * 创建管理服务并加载仓储、贡献者和 API 技能配置。
     *
     * @param skillRegistry 技能注册表，不允许为空
     * @param contributors 外部智能体贡献者，允许为空
     * @param skillConfigRepository 技能配置仓储，测试或未启用持久化时允许为空
     * @param agentRepository 智能体仓储，测试或未启用持久化时允许为空
     * @param runtimeInvalidators 运行时失效端口，允许为空或包含空元素
     */
    public AiAgentManagementService(
            AiSkillRegistry skillRegistry,
            List<AiManagedAgentContributor> contributors,
            AiManagedSkillConfigRepository skillConfigRepository,
            AiManagedAgentRepository agentRepository,
            AiManagedAgentRuntimeInvalidator... runtimeInvalidators) {
        this.skillRegistry = Objects.requireNonNull(skillRegistry, "skillRegistry must not be null");
        this.skillConfigRepository = skillConfigRepository;
        this.agentRepository = agentRepository;
        this.runtimeInvalidators = runtimeInvalidators == null
                ? List.of()
                : Arrays.stream(runtimeInvalidators)
                        .filter(Objects::nonNull)
                        .toList();
        loadManagedAgents(contributors);
        loadConfiguredApiSkills();
    }

    /**
     * 查询当前全部活动智能体快照。
     *
     * @return 按加载顺序返回的副本列表，无数据时返回空列表
     */
    public synchronized List<AiManagedAgent> listAgents() {
        return new ArrayList<>(agents.values());
    }

    /**
     * 按智能体 ID 查找已启用的智能体。
     *
     * <p>输入为空白、智能体不存在或智能体已停用时返回空；查询仅访问当前已加载的管理快照，
     * 不触发数据库写入，也不改变智能体状态。</p>
     *
     * @param id 智能体 ID，允许为 {@code null} 或空白
     * @return 已启用的智能体；未匹配可用智能体时返回 {@link Optional#empty()}
     */
    public synchronized Optional<AiManagedAgent> findEnabledAgentById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(agents.get(id.trim()))
                .filter(AiManagedAgent::enabled);
    }
    /**
     * 查询内置技能和 API 技能的管理视图。
     *
     * @return 包含绑定数量和脱敏 API 配置的技能列表
     */
    public synchronized List<AiManagedSkill> listSkills() {
        return skillRegistry.list().stream()
                .map(this::toManagedSkill)
                .toList();
    }

    /**
     * 按渠道查询第一个已启用默认智能体，仅供旧版单租户调用。
     *
     * @param channel 渠道编码，允许为空
     * @return 第一个匹配智能体；未匹配时返回空
     */
    public synchronized Optional<AiManagedAgent> findDefaultAgentForChannel(String channel) {
        return agents.values().stream()
                .filter(AiManagedAgent::enabled)
                .filter(agent -> agent.isDefaultForChannel(channel))
                .findFirst();
    }

    /**
     * 在指定内部租户的数据范围内查找渠道默认智能体。
     *
     * <p>查询只读取当前管理快照，不触发写入。{@code tenantId} 为空白时不允许跨租户降级，
     * 直接返回空结果；同一租户存在多个渠道默认配置时沿用管理快照顺序返回第一个。</p>
     *
     * @param channel 渠道编码，允许为空；为空时不匹配任何智能体
     * @param tenantId 内部租户标识，是智能体数据隔离维度，不允许为空白
     * @return 同时匹配租户、渠道且已启用的智能体；未匹配时返回 {@link Optional#empty()}
     */
    public synchronized Optional<AiManagedAgent> findDefaultAgentForChannel(
            String channel,
            String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return Optional.empty();
        }
        return agents.values().stream()
                .filter(AiManagedAgent::enabled)
                .filter(agent -> tenantId.equals(agent.tenantId()))
                .filter(agent -> agent.isDefaultForChannel(channel))
                .findFirst();
    }

    /**
     * 创建智能体并同步写入仓储与进程快照。
     *
     * @param request 创建请求，名称等必填字段由转换流程校验
     * @return 已生成业务 ID 且 tenantId 已规范化的智能体
     * @throws IllegalArgumentException 当请求字段不合法时抛出
     */
    public synchronized AiManagedAgent create(AiManagedAgentRequest request) {
        AiManagedAgent agent = toAgent("a" + System.currentTimeMillis() + sequence.incrementAndGet(), request, java.time.LocalDateTime.now());
        saveAgent(agent);
        agents.put(agent.id(), agent);
        return agent;
    }

    /**
     * 更新智能体，并在写入前失效旧租户下的运行时实例。
     *
     * @param id 智能体业务 ID，不允许为空且必须存在
     * @param request 完整更新请求
     * @return 更新后的智能体；目标不存在时返回 {@code null}
     * @throws IllegalArgumentException 当请求字段不合法时抛出
     */
    public synchronized AiManagedAgent update(String id, AiManagedAgentRequest request) {
        AiManagedAgent existing = agents.get(id);
        if (existing == null) {
            return null;
        }
        AiManagedAgent agent = toAgent(id, request, existing.createdAt());
        invalidateRuntime(existing);
        saveAgent(agent);
        agents.put(id, agent);
        return agent;
    }

    /**
     * 软删除智能体，并失效其全部 HarnessAgent 配置版本。
     *
     * @param id 智能体业务 ID，不允许为空
     * @return {@code true} 表示软删除成功，目标不存在或仓储拒绝时返回 {@code false}
     */
    public synchronized boolean delete(String id) {
        AiManagedAgent existing = agents.get(id);
        if (existing == null) {
            return false;
        }
        invalidateRuntime(existing);
        boolean deleted = agentRepository == null || agentRepository.softDelete(id);
        if (deleted) {
            agents.remove(id);
        }
        return deleted;
    }

    /**
     * 绑定或解除技能提示词模板。
     *
     * @param name 技能名称，不允许为空
     * @param promptTemplateId 模板主键；为空表示解除绑定
     * @return 更新后的技能视图；技能不存在时返回 {@code null}
     */
    public synchronized AiManagedSkill bindSkillPromptTemplate(String name, Long promptTemplateId) {
        Optional<AiSkillDescriptor> descriptor = skillRegistry.list().stream()
                .filter(skill -> skill.name().equals(name))
                .findFirst();
        if (descriptor.isEmpty()) {
            return null;
        }
        if (promptTemplateId == null) {
            skillPromptTemplateIds.remove(name);
        } else {
            skillPromptTemplateIds.put(name, promptTemplateId);
        }
        persistPromptTemplateBinding(name, promptTemplateId);
        return toManagedSkill(descriptor.get());
    }

    /**
     * 创建运行时 API 技能，并失效已预先引用该技能的智能体实例。
     *
     * @param request API 技能定义，名称、地址和方法必须合法
     * @return 创建后的脱敏技能视图
     * @throws IllegalArgumentException 当名称冲突或配置不合法时抛出
     */
    public synchronized AiManagedSkill createApiSkill(AiManagedSkillRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String name = normalizedSkillName(request.getName());
        if (skillRegistry.hasBeanSkill(name)) {
            throw new IllegalArgumentException("\u6280\u80fd\u540d\u79f0\u5df2\u88ab\u5185\u7f6e\u6280\u80fd\u5360\u7528");
        }
        if (skillRegistry.apiSkillConfig(name).isPresent()) {
            throw new IllegalArgumentException("API\u6280\u80fd\u5df2\u5b58\u5728");
        }
        AiApiSkillConfig saved = skillRegistry.registerApiSkill(toApiSkillConfig(name, request));
        try {
            saveSkillConfig(request, saved);
        } catch (RuntimeException ex) {
            skillRegistry.removeApiSkill(saved.name());
            throw ex;
        }
        invalidateAgentsUsingSkill(name);
        return toManagedSkill(saved);
    }

    /**
     * 完整更新 API 技能，并失效所有绑定智能体的旧权限和工具描述。
     *
     * @param name 现有技能名称，不允许修改
     * @param request 完整技能定义
     * @return 更新后的脱敏技能视图
     * @throws IllegalArgumentException 当技能不存在、属于内置技能或名称变化时抛出
     */
    public synchronized AiManagedSkill updateApiSkill(String name, AiManagedSkillRequest request) {
        name = normalizedSkillName(name);
        Objects.requireNonNull(request, "request must not be null");
        if (skillRegistry.hasBeanSkill(name)) {
            throw new IllegalArgumentException("\u5185\u7f6e\u6280\u80fd\u4e0d\u53ef\u7f16\u8f91");
        }
        if (StringUtils.hasText(request.getName()) && !name.equals(request.getName().trim())) {
            throw new IllegalArgumentException("\u6280\u80fd\u540d\u79f0\u521b\u5efa\u540e\u4e0d\u5141\u8bb8\u4fee\u6539");
        }
        AiApiSkillConfig previous = skillRegistry.apiSkillConfig(name)
                .orElseThrow(() -> new IllegalArgumentException("API\u6280\u80fd\u4e0d\u5b58\u5728"));
        AiApiSkillConfig saved = skillRegistry.registerApiSkill(toApiSkillConfig(name, request));
        try {
            saveSkillConfig(request, saved);
        } catch (RuntimeException ex) {
            skillRegistry.registerApiSkill(previous);
            throw ex;
        }
        invalidateAgentsUsingSkill(name);
        return toManagedSkill(saved);
    }

    /**
     * 仅更新 API 调用配置，并失效所有绑定智能体实例。
     *
     * @param name API 技能名称，不允许为空
     * @param request API 地址、方法、请求头和启用状态
     * @return 更新后的脱敏技能视图
     * @throws IllegalArgumentException 当技能不存在或参数不合法时抛出
     */
    public synchronized AiManagedSkill updateApiSkillConfig(String name, AiSkillApiConfigRequest request) {
        requireText(name, "\u6280\u80fd\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a");
        Objects.requireNonNull(request, "request must not be null");
        AiApiSkillConfig existing = skillRegistry.apiSkillConfig(name)
                .orElseThrow(() -> new IllegalArgumentException("API\u6280\u80fd\u4e0d\u5b58\u5728"));
        AiApiSkillConfig saved = skillRegistry.registerApiSkill(new AiApiSkillConfig(
                existing.name(),
                existing.description(),
                existing.readOnly(),
                request.isEnabled(),
                request.getBaseUrl(),
                request.getPath(),
                request.getMethod(),
                request.getHeaders(),
                request.getTimeoutMillis()));
        updateStoredApiConfig(saved, request.getApiRegistryId());
        invalidateAgentsUsingSkill(name);
        return toManagedSkill(saved);
    }

    /**
     * 删除 API 技能、解除智能体绑定并失效相关运行时实例。
     *
     * @param name API 技能名称，不允许为空
     * @return 注册表或仓储任一侧删除成功时返回 {@code true}
     * @throws IllegalArgumentException 当目标属于内置技能时抛出
     */
    public synchronized boolean deleteApiSkill(String name) {
        if (skillRegistry.hasBeanSkill(name)) {
            throw new IllegalArgumentException("\u5185\u7f6e\u6280\u80fd\u4e0d\u53ef\u5220\u9664");
        }
        skillPromptTemplateIds.remove(name);
        skillAgentIds.remove(name);
        skillApiRegistryIds.remove(name);
        boolean removedFromRepository = skillConfigRepository != null && skillConfigRepository.softDelete(name);
        boolean removedFromRegistry = skillRegistry.removeApiSkill(name);
        removeSkillBindings(name);
        return removedFromRegistry || removedFromRepository;
    }

    private long referenceCount(AiSkillDescriptor skill) {
        return agents.values().stream()
                .filter(agent -> agent.skillIds().contains(skill.name()))
                .count();
    }

    private AiManagedSkill toManagedSkill(AiSkillDescriptor skill) {
        Optional<AiApiSkillConfig> apiConfig = skillRegistry.apiSkillConfig(skill.name());
        if (apiConfig.isPresent()) {
            return toManagedSkill(apiConfig.get());
        }
        return new AiManagedSkill(
                skill.name(),
                skill.description(),
                skill.readOnly(),
                referenceCount(skill),
                skillPromptTemplateIds.get(skill.name()),
                null,
                "bean",
                true,
                null,
                null);
    }

    private AiManagedSkill toManagedSkill(AiApiSkillConfig config) {
        Optional<AiManagedSkillConfig> stored = findStoredSkill(config.name());
        Long promptTemplateId = stored.map(AiManagedSkillConfig::getPromptTemplateId)
                .orElse(skillPromptTemplateIds.get(config.name()));
        String agentId = stored.map(AiManagedSkillConfig::getAgentId).orElse(null);
        if (agentId == null) {
            agentId = skillAgentIds.get(config.name());
        }
        return new AiManagedSkill(
                config.name(),
                config.description(),
                config.readOnly(),
                referenceCount(new AiSkillDescriptor(config.name(), config.description(), config.readOnly())),
                promptTemplateId,
                apiConfig(config.name()),
                "api",
                config.enabled(),
                agentId,
                stored.map(AiManagedSkillConfig::getCreatedAt).orElse(null));
    }

    private AiApiSkillConfig toApiSkillConfig(String name, AiManagedSkillRequest request) {
        AiSkillApiConfigRequest apiConfig = request.getApiConfig();
        ValidationUtil.requireNotNull(apiConfig, "API\u6280\u80fd\u914d\u7f6e\u4e0d\u80fd\u4e3a\u7a7a");
        Map<String, String> existingHeaders = skillRegistry.apiSkillConfig(name)
                .map(AiApiSkillConfig::headers)
                .orElse(Map.of());
        return new AiApiSkillConfig(
                name,
                request.getDescription(),
                request.isReadOnly(),
                apiConfig.isEnabled(),
                apiConfig.getBaseUrl(),
                apiConfig.getPath(),
                apiConfig.getMethod(),
                mergeRedactedHeaders(apiConfig.getHeaders(), existingHeaders),
                apiConfig.getTimeoutMillis());
    }

    private AiApiSkillConfig toApiSkillConfig(AiManagedSkillConfig config) {
        return new AiApiSkillConfig(
                config.getSkillName(),
                config.getSkillDescription(),
                config.isReadOnly(),
                config.isEnabled(),
                config.getBaseUrl(),
                config.getApiPath(),
                config.getHttpMethod(),
                headersFromJson(config.getRequestHeaders()),
                config.getTimeoutMillis());
    }

    private AiSkillApiConfigResponse apiConfig(String skillName) {
        Long apiRegistryId = apiRegistryId(skillName);
        return skillRegistry.apiSkillConfig(skillName)
                .map(config -> new AiSkillApiConfigResponse(
                        config.enabled(),
                        config.baseUrl(),
                        config.path(),
                        config.method(),
                        redactedHeaders(config.headers()),
                        config.timeoutMillis(),
                        apiRegistryId))
                .orElse(null);
    }

    private void loadConfiguredApiSkills() {
        if (skillConfigRepository == null) {
            return;
        }
        for (AiManagedSkillConfig config : skillConfigRepository.listActive()) {
            skillRegistry.registerApiSkill(toApiSkillConfig(config));
            if (config.getPromptTemplateId() != null) {
                skillPromptTemplateIds.put(config.getSkillName(), config.getPromptTemplateId());
            }
        }
    }

    private void saveSkillConfig(AiManagedSkillRequest request, AiApiSkillConfig saved) {
        if (skillConfigRepository == null) {
            if (StringUtils.hasText(request.getAgentId())) {
                skillAgentIds.put(saved.name(), request.getAgentId().trim());
            } else {
                skillAgentIds.remove(saved.name());
            }
            if (request.getPromptTemplateId() != null) {
                skillPromptTemplateIds.put(saved.name(), request.getPromptTemplateId());
            }
            rememberApiRegistryId(saved.name(), request.getApiConfig().getApiRegistryId());
            return;
        }
        AiManagedSkillConfig config = findStoredSkill(saved.name()).orElseGet(AiManagedSkillConfig::new);
        config.setAgentId(textOrNull(request.getAgentId()));
        config.setSkillName(saved.name());
        config.setSkillDescription(saved.description());
        config.setSkillType("api");
        config.setReadOnly(saved.readOnly());
        config.setEnabled(saved.enabled());
        config.setBaseUrl(saved.baseUrl());
        config.setApiPath(saved.path());
        config.setHttpMethod(saved.method());
        config.setRequestHeaders(headersToJson(saved.headers()));
        config.setTimeoutMillis(saved.timeoutMillis());
        config.setApiRegistryId(request.getApiConfig().getApiRegistryId());
        config.setPromptTemplateId(request.getPromptTemplateId());
        config.setDeleted(false);
        skillConfigRepository.save(config);
        if (request.getPromptTemplateId() == null) {
            skillPromptTemplateIds.remove(saved.name());
        } else {
            skillPromptTemplateIds.put(saved.name(), request.getPromptTemplateId());
        }
        if (StringUtils.hasText(request.getAgentId())) {
            skillAgentIds.put(saved.name(), request.getAgentId().trim());
        } else {
            skillAgentIds.remove(saved.name());
        }
    }

    private void updateStoredApiConfig(AiApiSkillConfig saved, Long apiRegistryId) {
        if (skillConfigRepository == null) {
            rememberApiRegistryId(saved.name(), apiRegistryId);
            return;
        }
        Optional<AiManagedSkillConfig> existing = findStoredSkill(saved.name());
        if (existing.isEmpty()) {
            return;
        }
        AiManagedSkillConfig config = existing.get();
        config.setSkillDescription(saved.description());
        config.setReadOnly(saved.readOnly());
        config.setEnabled(saved.enabled());
        config.setBaseUrl(saved.baseUrl());
        config.setApiPath(saved.path());
        config.setHttpMethod(saved.method());
        config.setRequestHeaders(headersToJson(saved.headers()));
        config.setTimeoutMillis(saved.timeoutMillis());
        config.setApiRegistryId(apiRegistryId);
        skillConfigRepository.save(config);
    }

    private void persistPromptTemplateBinding(String name, Long promptTemplateId) {
        if (skillConfigRepository == null) {
            return;
        }
        findStoredSkill(name).ifPresent(config -> {
            config.setPromptTemplateId(promptTemplateId);
            skillConfigRepository.save(config);
        });
    }

    private Long apiRegistryId(String skillName) {
        return findStoredSkill(skillName)
                .map(AiManagedSkillConfig::getApiRegistryId)
                .orElseGet(() -> skillApiRegistryIds.get(skillName));
    }

    private void rememberApiRegistryId(String name, Long apiRegistryId) {
        if (apiRegistryId == null) {
            skillApiRegistryIds.remove(name);
        } else {
            skillApiRegistryIds.put(name, apiRegistryId);
        }
    }

    private Optional<AiManagedSkillConfig> findStoredSkill(String name) {
        if (skillConfigRepository == null) {
            return Optional.empty();
        }
        return skillConfigRepository.findActiveByName(name);
    }

    private void removeSkillBindings(String name) {
        List<AiManagedAgent> updatedAgents = agents.values().stream()
                .filter(agent -> agent.skillIds().contains(name))
                .map(agent -> new AiManagedAgent(
                        agent.id(),
                        agent.name(),
                        agent.desc(),
                        agent.persona(),
                        agent.model(),
                        agent.promptTemplateId(),
                        agent.skillIds().stream().filter(skillName -> !name.equals(skillName)).toList(),
                        agent.agentType(),
                        agent.agentConfig(),
                        agent.enabled(),
                        agent.userId(),
                        agent.tenantId(),
                        agent.userName(),
                        agent.defaultChannels(),
                        agent.createdAt()))
                .toList();
        for (AiManagedAgent agent : updatedAgents) {
            invalidateRuntime(agent);
            saveAgent(agent);
            agents.put(agent.id(), agent);
        }
    }

    private void invalidateAgentsUsingSkill(String skillName) {
        agents.values().stream()
                .filter(agent -> agent.skillIds().contains(skillName))
                .forEach(this::invalidateRuntime);
    }

    private void invalidateRuntime(AiManagedAgent agent) {
        for (AiManagedAgentRuntimeInvalidator invalidator : runtimeInvalidators) {
            invalidator.invalidate(agent.tenantId(), agent.id());
        }
    }

    private Map<String, String> redactedHeaders(Map<String, String> headers) {
        if (MapUtil.isEmpty(headers)) {
            return Map.of();
        }
        Map<String, String> redacted = new LinkedHashMap<>();
        headers.forEach((name, value) -> redacted.put(name, isSensitiveHeader(name) ? "[redacted]" : value));
        return redacted;
    }

    private boolean isSensitiveHeader(String name) {
        String normalized = name == null ? "" : name.toLowerCase();
        return normalized.contains("authorization")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("key");
    }

    private Map<String, String> mergeRedactedHeaders(
            Map<String, String> requestHeaders,
            Map<String, String> existingHeaders) {
        if (requestHeaders == null || requestHeaders.isEmpty()) {
            return Map.of();
        }
        Map<String, String> merged = new LinkedHashMap<>();
        requestHeaders.forEach((name, value) -> {
            if ("[redacted]".equals(value) && existingHeaders != null && existingHeaders.containsKey(name)) {
                merged.put(name, existingHeaders.get(name));
            } else {
                merged.put(name, value);
            }
        });
        return merged;
    }

    private String headersToJson(Map<String, String> headers) {
        if (MapUtil.isEmpty(headers)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(headers);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("\u8bf7\u6c42\u5934JSON\u4e0d\u5408\u6cd5", ex);
        }
    }

    private Map<String, String> headersFromJson(String headersJson) {
        if (!StringUtils.hasText(headersJson)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(headersJson, STRING_MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("\u8bf7\u6c42\u5934JSON\u4e0d\u5408\u6cd5", ex);
        }
    }

    private AiManagedAgent toAgent(String id, AiManagedAgentRequest request, java.time.LocalDateTime createdAt) {
        Objects.requireNonNull(request, "request must not be null");
        requireText(request.getName(), "\u667a\u80fd\u4f53\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a");
        requireText(request.getDesc(), "\u667a\u80fd\u4f53\u63cf\u8ff0\u4e0d\u80fd\u4e3a\u7a7a");
        return new AiManagedAgent(
                id,
                request.getName().trim(),
                request.getDesc().trim(),
                textOrDefault(request.getPersona(), ""),
                textOrDefault(request.getModel(), "qwen-plus"),
                request.getPromptTemplateId(),
                request.getSkillIds(),
                textOrDefault(request.getAgentType(), "conversation"),
                request.getAgentConfig(),
                request.isEnabled(),
                textOrDefault(request.getUserId(), "u001"),
                request.getTenantId(),
                textOrDefault(request.getUserName(), "\u5f20\u5efa\u56fd"),
                request.getDefaultChannels(),
                createdAt);
    }

    private void loadManagedAgents(List<AiManagedAgentContributor> contributors) {
        if (agentRepository == null) {
            seedContributedAgents(contributors);
            seedAgents();
            return;
        }
        List<AiManagedAgent> storedAgents = agentRepository.listActive();
        storedAgents.forEach(agent -> agents.put(agent.id(), agent));
        seedContributedAgents(contributors);
        if (!storedAgents.isEmpty() || agentRepository.hasAny()) {
            return;
        }
        seedAgents();
        persistSeedAgents();
    }

    private void persistSeedAgents() {
        if (agentRepository == null) {
            return;
        }
        agents.values().forEach(agentRepository::save);
    }

    private void saveAgent(AiManagedAgent agent) {
        if (agentRepository != null) {
            agentRepository.save(agent);
        }
    }
    private void seedAgents() {
        agents.putIfAbsent("project-management-agent", new AiManagedAgent(
                "project-management-agent",
                "项目管理智能体",
                "面向飞书对话的项目管理智能体，支持项目生成、项目编码和项目视图查询",
                "你是项目管理智能体。用户发送项目相关消息时，先理解意图，再调用绑定技能获取数据。返回结果必须是标准化结构化 JSON，包含 headers、rows、actions 三部分，禁止输出 Markdown、长文本和多余注释。",
                "qwen-plus",
                null,
                List.of("parse_excel_projects", "generate_project_code", "view_project_code", "view_all_projects"),
                true,
                "system",
                "系统初始化",
                List.of("feishu")));
        agents.putIfAbsent("a1", new AiManagedAgent(
                "a1",
                "\u91c7\u8d2d\u5ba1\u6279\u52a9\u624b",
                "\u81ea\u52a8\u5ba1\u6279\u5e38\u89c4\u91c7\u8d2d\u5355\u5e76\u63d0\u793a\u5f02\u5e38\u98ce\u9669",
                "\u4f60\u662f\u91c7\u8d2d\u5ba1\u6279\u52a9\u624b\uff0c\u5148\u5224\u65ad\u91c7\u8d2d\u5355\u662f\u5426\u7b26\u5408\u5e38\u89c4\u89c4\u5219\uff0c\u518d\u7ed9\u51fa\u6e05\u6670\u5efa\u8bae\u3002",
                "qwen-max",
                null,
                List.of("generate_plan", "route_plugin_task"),
                true,
                "u001",
                "\u5f20\u5efa\u56fd",
                List.of()));
        agents.putIfAbsent("a2", new AiManagedAgent(
                "a2",
                "\u5e93\u5b58\u7ba1\u5bb6",
                "\u8ddf\u8e2a\u5e93\u5b58\u5f02\u5e38\u5e76\u6c47\u603b\u5904\u7406\u5efa\u8bae",
                "\u4f60\u662f\u5e93\u5b58\u7ba1\u7406\u52a9\u624b\uff0c\u5173\u6ce8\u7f3a\u6599\u3001\u5446\u6ede\u3001\u8d85\u50a8\u548c\u5468\u8f6c\u98ce\u9669\u3002",
                "qwen-plus",
                null,
                List.of("summarize", "echo"),
                true,
                "u001",
                "\u5f20\u5efa\u56fd",
                List.of()));
        agents.putIfAbsent("a3", new AiManagedAgent(
                "a3",
                "\u6392\u7a0b\u52a9\u624b",
                "\u6839\u636e\u751f\u4ea7\u4efb\u52a1\u751f\u6210\u6392\u7a0b\u68c0\u67e5\u6e05\u5355",
                "\u4f60\u662f\u751f\u4ea7\u6392\u7a0b\u52a9\u624b\uff0c\u56de\u7b54\u65f6\u4f18\u5148\u7ed9\u51fa\u53ef\u6267\u884c\u6b65\u9aa4\u548c\u4f9d\u8d56\u6761\u4ef6\u3002",
                "qwen-max",
                null,
                List.of("generate_plan"),
                false,
                "u001",
                "\u5f20\u5efa\u56fd",
                List.of()));
    }

    private void seedContributedAgents(List<AiManagedAgentContributor> contributors) {
        if (contributors == null) {
            return;
        }
        for (AiManagedAgentContributor contributor : contributors) {
            if (contributor == null) {
                continue;
            }
            List<AiManagedAgent> contributedAgents = contributor.agents();
            if (contributedAgents == null) {
                continue;
            }
            for (AiManagedAgent agent : contributedAgents) {
                if (agent != null && StringUtils.hasText(agent.id())) {
                    agents.put(agent.id(), agent);
                }
            }
        }
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizedSkillName(String value) {
        requireText(value, "\u6280\u80fd\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a");
        return value.trim();
    }

    private String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String textOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
