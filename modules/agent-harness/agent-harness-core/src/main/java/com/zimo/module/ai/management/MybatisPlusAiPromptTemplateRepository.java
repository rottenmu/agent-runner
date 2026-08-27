package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.StrUtil;
import com.zimo.module.ai.mapper.AiPromptTemplateMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的提示词模板仓储实现。
 *
 * <p>所有查询自动排除逻辑删除数据；写操作仅覆盖 {@code ai_prompt_template} 单表。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public class MybatisPlusAiPromptTemplateRepository implements AiPromptTemplateRepository {

    private final AiPromptTemplateMapper mapper;

    /**
     * 创建提示词模板仓储。
     *
     * @param mapper 提示词模板 Mapper，不允许为空
     */
    public MybatisPlusAiPromptTemplateRepository(AiPromptTemplateMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public List<AiPromptTemplate> listActive() {
        return mapper.selectList(Wrappers.<AiPromptTemplate>lambdaQuery()
                .orderByAsc(AiPromptTemplate::getCreatedAt)
                .orderByAsc(AiPromptTemplate::getId));
    }

    @Override
    public List<AiPromptTemplate> listActive(String templateType) {
        if (StrUtil.isBlank(templateType)) {
            return listActive();
        }
        return mapper.selectList(Wrappers.<AiPromptTemplate>lambdaQuery()
                .eq(AiPromptTemplate::getTemplateType, templateType)
                .orderByAsc(AiPromptTemplate::getCreatedAt)
                .orderByAsc(AiPromptTemplate::getId));
    }

    @Override
    public Optional<AiPromptTemplate> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public AiPromptTemplate save(AiPromptTemplate template) {
        Objects.requireNonNull(template, "template must not be null");
        if (template.getId() == null) {
            mapper.insert(template);
        } else {
            mapper.updateById(template);
        }
        return template;
    }

    @Override
    public boolean softDelete(Long id) {
        return mapper.deleteById(id) > 0;
    }
}
