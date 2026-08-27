package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.module.ai.mapper.AiManagedSkillConfigMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的自定义技能配置仓储实现。
 *
 * <p>所有查询自动排除逻辑删除数据；写操作仅覆盖 {@code ai_agent_skill_config} 单表。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public class MybatisPlusAiManagedSkillConfigRepository implements AiManagedSkillConfigRepository {

    private final AiManagedSkillConfigMapper mapper;

    /**
     * 创建技能配置仓储。
     *
     * @param mapper 技能配置 Mapper，不允许为空
     */
    public MybatisPlusAiManagedSkillConfigRepository(AiManagedSkillConfigMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public List<AiManagedSkillConfig> listActive() {
        return mapper.selectList(Wrappers.<AiManagedSkillConfig>lambdaQuery()
                .orderByAsc(AiManagedSkillConfig::getCreatedAt)
                .orderByAsc(AiManagedSkillConfig::getId));
    }

    @Override
    public Optional<AiManagedSkillConfig> findActiveByName(String skillName) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<AiManagedSkillConfig>lambdaQuery()
                .eq(AiManagedSkillConfig::getSkillName, skillName)
                .last("LIMIT 1")));
    }

    @Override
    public AiManagedSkillConfig save(AiManagedSkillConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        if (config.getId() == null) {
            if (config.getCreatedAt() == null) {
                config.setCreatedAt(java.time.LocalDateTime.now());
            }
            mapper.insert(config);
        } else {
            mapper.updateById(config);
        }
        return config;
    }

    @Override
    public boolean softDelete(String skillName) {
        return mapper.delete(Wrappers.<AiManagedSkillConfig>lambdaQuery()
                .eq(AiManagedSkillConfig::getSkillName, skillName)) > 0;
    }
}
