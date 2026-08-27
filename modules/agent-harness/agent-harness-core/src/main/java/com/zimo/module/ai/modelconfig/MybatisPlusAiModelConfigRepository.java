package com.zimo.module.ai.modelconfig;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.module.ai.modelconfig.mapper.AiModelConfigMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.util.StringUtils;

/**
 * 基于 MyBatis-Plus 的 AI 模型配置仓储。
 *
 * @author Codex
 * @since 2026-07-23
 */
public class MybatisPlusAiModelConfigRepository implements AiModelConfigRepository {

    private final AiModelConfigMapper mapper;

    /**
     * 创建模型配置仓储。
     *
     * @param mapper 模型配置 Mapper，不能为空
     */
    public MybatisPlusAiModelConfigRepository(AiModelConfigMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public List<AiModelConfigEntity> find(AiModelConfigQuery query) {
        AiModelConfigQuery criteria = query == null
                ? new AiModelConfigQuery(null, null, null, null)
                : query;
        LambdaQueryWrapper<AiModelConfigEntity> wrapper = Wrappers.lambdaQuery();
        addTextFilter(wrapper, AiModelConfigEntity::getEnv, criteria.env());
        addTextFilter(wrapper, AiModelConfigEntity::getProvider, criteria.provider());
        addStatusFilter(wrapper, criteria.status());
        addKeywordFilter(wrapper, criteria.keyword());
        wrapper.orderByDesc(AiModelConfigEntity::getUpdatedAt)
                .orderByDesc(AiModelConfigEntity::getId);
        return mapper.selectList(wrapper);
    }

    @Override
    public Optional<AiModelConfigEntity> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public AiModelConfigEntity insert(AiModelConfigEntity entity) {
        AiModelConfigEntity row = requireEntity(entity);
        LocalDateTime now = LocalDateTime.now();
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(now);
        }
        if (row.getUpdatedAt() == null) {
            row.setUpdatedAt(row.getCreatedAt());
        }
        mapper.insert(row);
        return row;
    }

    @Override
    public AiModelConfigEntity update(AiModelConfigEntity entity) {
        AiModelConfigEntity row = requireEntity(entity);
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
        return row;
    }

    @Override
    public boolean logicalDelete(long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public void updateTestResult(long id, AiModelConfigTestResponse response) {
        AiModelConfigTestResponse result = Objects.requireNonNull(response, "response must not be null");
        mapper.update(null, Wrappers.<AiModelConfigEntity>lambdaUpdate()
                .eq(AiModelConfigEntity::getId, id)
                .set(AiModelConfigEntity::getLastTestStatus, result.status())
                .set(AiModelConfigEntity::getLastTestLatency, result.latency())
                .set(AiModelConfigEntity::getLastTestMessage, result.message())
                .set(AiModelConfigEntity::getLastTestedAt, result.testedAt())
                .set(AiModelConfigEntity::getUpdatedAt, LocalDateTime.now()));
    }

    private <T> void addTextFilter(
            LambdaQueryWrapper<AiModelConfigEntity> wrapper,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<AiModelConfigEntity, T> column,
            String value) {
        if (StringUtils.hasText(value)) {
            wrapper.eq(column, value.trim());
        }
    }

    private void addStatusFilter(LambdaQueryWrapper<AiModelConfigEntity> wrapper, String status) {
        String normalized = status == null ? "" : status.trim();
        if ("enabled".equalsIgnoreCase(normalized)) {
            wrapper.eq(AiModelConfigEntity::isEnabled, true);
        } else if ("disabled".equalsIgnoreCase(normalized)) {
            wrapper.eq(AiModelConfigEntity::isEnabled, false);
        }
    }

    private void addKeywordFilter(LambdaQueryWrapper<AiModelConfigEntity> wrapper, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String value = keyword.trim();
        wrapper.and(condition -> condition
                .like(AiModelConfigEntity::getConfigName, value)
                .or()
                .like(AiModelConfigEntity::getModelId, value)
                .or()
                .like(AiModelConfigEntity::getDescription, value));
    }

    private AiModelConfigEntity requireEntity(AiModelConfigEntity entity) {
        return Objects.requireNonNull(entity, "entity must not be null");
    }
}
