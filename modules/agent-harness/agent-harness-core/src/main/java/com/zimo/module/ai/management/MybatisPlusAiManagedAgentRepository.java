package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.mapper.AiManagedAgentMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的智能体配置仓储实现。
 *
 * <p>领域对象中的技能和默认渠道列表在仓储边界转换为 JSON，数据库访问仅覆盖
 * {@code ai_managed_agent} 单表。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public class MybatisPlusAiManagedAgentRepository implements AiManagedAgentRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final AiManagedAgentMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建智能体配置仓储。
     *
     * @param mapper 智能体 Mapper，不允许为空
     * @param objectMapper JSON 转换器，不允许为空
     */
    public MybatisPlusAiManagedAgentRepository(
            AiManagedAgentMapper mapper,
            ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public List<AiManagedAgent> listActive() {
        return mapper.selectList(Wrappers.<AiManagedAgentEntity>lambdaQuery()
                        .orderByAsc(AiManagedAgentEntity::getCreatedAt)
                        .orderByAsc(AiManagedAgentEntity::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<AiManagedAgent> findActiveById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean hasAny() {
        return mapper.countAll() > 0;
    }

    @Override
    public AiManagedAgent save(AiManagedAgent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        AiManagedAgentEntity entity = toEntity(agent);
        if (mapper.selectById(agent.id()) == null) {
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(java.time.LocalDateTime.now());
            }
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return agent;
    }

    @Override
    public boolean softDelete(String id) {
        return mapper.deleteById(id) > 0;
    }

    private AiManagedAgentEntity toEntity(AiManagedAgent agent) {
        AiManagedAgentEntity entity = new AiManagedAgentEntity();
        entity.setId(agent.id());
        entity.setAgentName(agent.name());
        entity.setAgentDesc(agent.desc());
        entity.setPersona(agent.persona());
        entity.setModelName(agent.model());
        entity.setPromptTemplateId(agent.promptTemplateId());
        entity.setAgentType(agent.agentType());
        entity.setAgentConfig(agent.agentConfig());
        entity.setSkillIds(writeList(agent.skillIds()));
        entity.setEnabled(agent.enabled());
        entity.setUserId(agent.userId());
        entity.setTenantId(agent.tenantId());
        entity.setUserName(agent.userName());
        entity.setDefaultChannels(writeList(agent.defaultChannels()));
        return entity;
    }

    private AiManagedAgent toDomain(AiManagedAgentEntity entity) {
        return new AiManagedAgent(
                entity.getId(),
                entity.getAgentName(),
                entity.getAgentDesc(),
                entity.getPersona(),
                entity.getModelName(),
                entity.getPromptTemplateId(),
                readList(entity.getSkillIds()),
                entity.getAgentType(),
                entity.getAgentConfig(),
                entity.isEnabled(),
                entity.getUserId(),
                entity.getTenantId(),
                entity.getUserName(),
                readList(entity.getDefaultChannels()),
                entity.getCreatedAt());
    }

    private String writeList(List<String> values) {
        try {
            // List 判空用 CollUtil（StrUtil 仅适用于 String）
            return cn.hutool.core.collection.CollUtil.isEmpty(values) ? null : objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("智能体列表字段无法序列化为 JSON", exception);
        }
    }

    private List<String> readList(String value) {
        if (StrUtil.isBlank(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("智能体列表字段不是合法 JSON", exception);
        }
    }
}
