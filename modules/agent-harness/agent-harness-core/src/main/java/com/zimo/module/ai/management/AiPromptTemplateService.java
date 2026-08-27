package com.zimo.module.ai.management;

import java.time.LocalDateTime;
import com.zimo.framework.common.validation.ValidationUtil;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.util.StringUtils;

/**
 * AI 管理模块的提示词模板服务，负责 CoSTAR 校验、模板保存、草稿生成和软删除。
 *
 * <p>该服务只编排领域校验与仓储调用，不直接连接数据库；事务边界由后续持久化装配层负责。</p>
 *
 * @author xingju
 * @since 2026-07-08
 */
public class AiPromptTemplateService {
    private final AiPromptTemplateRepository repository;
    private final AiPromptTemplateGenerator generator;

    public AiPromptTemplateService(
            AiPromptTemplateRepository repository,
            AiPromptTemplateGenerator generator) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
    }

    /**
     * 查询所有未软删除的提示词模板。
     *
     * @return 活跃模板列表；没有数据时返回空列表
     */
    public List<AiPromptTemplate> list() {
        return repository.listActive();
    }

    /**
     * 按模板类型查询所有未软删除的提示词模板。
     *
     * @param templateType 模板类型；为空时返回全部类型
     * @return 活跃模板列表
     */
    public List<AiPromptTemplate> list(String templateType) {
        return repository.listActive(normalizeNullableTemplateType(templateType));
    }

    /**
     * 新增提示词模板。
     *
     * @param request 保存请求，必须包含 templateName、合法 sourceType 和 CoSTAR 六段内容
     * @return 仓储保存后的模板实体
     * @throws IllegalArgumentException 当必填字段为空或 sourceType 不合法时抛出
     */
    public AiPromptTemplate create(AiPromptTemplateRequest request) {
        AiPromptTemplate template = toTemplate(null, request);
        LocalDateTime now = LocalDateTime.now();
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        return repository.save(template);
    }

    /**
     * 编辑提示词模板。
     *
     * @param id 主键 ID，必须存在且未软删除
     * @param request 保存请求，必须包含 templateName、合法 sourceType 和 CoSTAR 六段内容
     * @return 更新后的模板；模板不存在时返回 null
     * @throws IllegalArgumentException 当 id 为空、必填字段为空或 sourceType 不合法时抛出
     */
    public AiPromptTemplate update(Long id, AiPromptTemplateRequest request) {
        requireId(id);
        Optional<AiPromptTemplate> existingTemplate = repository.findById(id);
        if (existingTemplate.isEmpty()) {
            return null;
        }
        AiPromptTemplate existing = existingTemplate.get();
        AiPromptTemplate template = toTemplate(id, request);
        template.setCreatedBy(existing.getCreatedBy());
        template.setCreatedName(existing.getCreatedName());
        template.setCreatedAt(existing.getCreatedAt());
        template.setUpdatedAt(LocalDateTime.now());
        return repository.save(template);
    }

    /**
     * 根据业务简述生成提示词模板草稿。
     *
     * @param request 生成请求，businessDescription 必须包含有效文本
     * @return sourceType 为 generated 的草稿，不写入仓储
     * @throws IllegalArgumentException 当业务简述为空时抛出
     */
    public AiPromptTemplate generate(AiPromptTemplateGenerateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        AiPromptTemplate draft = generator.generate(request.getBusinessDescription());
        draft.setTemplateType(normalizeTemplateType(request.getTemplateType()));
        return draft;
    }

    /**
     * 对提示词模板执行软删除。
     *
     * @param id 主键 ID，不允许为空
     * @return true 表示软删除成功，false 表示模板不存在或已删除
     * @throws IllegalArgumentException 当 id 为空时抛出
     */
    public boolean delete(Long id) {
        requireId(id);
        return repository.softDelete(id);
    }

    private AiPromptTemplate toTemplate(Long id, AiPromptTemplateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        AiPromptTemplate template = new AiPromptTemplate();
        template.setId(id);
        template.setTemplateCode(requireText(request.getTemplateCode(), "templateCode"));
        template.setTemplateName(requireText(request.getTemplateName(), "templateName"));
        template.setDescription(trimToNull(request.getDescription()));
        template.setTemplateType(normalizeTemplateType(request.getTemplateType()));
        template.setContextText(requireText(request.getContextText(), "contextText"));
        template.setObjectiveText(requireText(request.getObjectiveText(), "objectiveText"));
        template.setStyleText(requireText(request.getStyleText(), "styleText"));
        template.setToneText(requireText(request.getToneText(), "toneText"));
        template.setAudienceText(requireText(request.getAudienceText(), "audienceText"));
        template.setResponseText(requireText(request.getResponseText(), "responseText"));
        template.setSourceType(normalizeSourceType(request.getSourceType()));
        template.setBusinessDescription(trimToNull(request.getBusinessDescription()));
        template.setEnabled(request.isEnabled());
        template.setDeleted(false);
        template.setCreatedBy(trimToNull(request.getCreatedBy()));
        template.setCreatedName(trimToNull(request.getCreatedName()));
        template.setUpdatedBy(trimToNull(request.getUpdatedBy()));
        template.setUpdatedName(trimToNull(request.getUpdatedName()));
        return template;
    }

    private String normalizeSourceType(String sourceType) {
        String normalized = StringUtils.hasText(sourceType) ? sourceType.trim() : "manual";
        if (!"manual".equals(normalized) && !"generated".equals(normalized)) {
            throw new IllegalArgumentException("sourceType只能为manual或generated");
        }
        return normalized;
    }

    private String normalizeTemplateType(String templateType) {
        String normalized = StringUtils.hasText(templateType) ? templateType.trim() : "agent";
        if (!"agent".equals(normalized) && !"skill".equals(normalized)) {
            throw new IllegalArgumentException("templateType只能为agent或skill");
        }
        return normalized;
    }

    private String normalizeNullableTemplateType(String templateType) {
        if (!StringUtils.hasText(templateType)) {
            return null;
        }
        return normalizeTemplateType(templateType);
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireId(Long id) {
        ValidationUtil.requireNotNull(id, "id不能为空");
    }
}
