package com.zimo.module.ai.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.framework.common.validation.ValidationUtil;
import com.zimo.module.ai.mapper.WfTemplateMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * 工作流模板市场服务。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
public class WfTemplateService {

    private final WfTemplateMapper templateMapper;

    public WfTemplateService(WfTemplateMapper templateMapper) {
        this.templateMapper = Objects.requireNonNull(templateMapper, "templateMapper must not be null");
    }

    /** 模板列表（可按分类/关键字过滤）。 */
    public List<WfTemplate> list(String category, String keyword) {
        String like = StringUtils.hasText(keyword) ? "%" + keyword.trim() + "%" : null;
        return templateMapper.selectList(Wrappers.<WfTemplate>lambdaQuery()
                .eq(StringUtils.hasText(category), WfTemplate::getCategory, category)
                .and(StringUtils.hasText(like), w -> w
                        .like(WfTemplate::getName, like)
                        .or()
                        .like(WfTemplate::getDescription, like)
                        .or()
                        .like(WfTemplate::getTags, like))
                .orderByDesc(WfTemplate::getDownloadCount));
    }

    /** 创建模板。 */
    public WfTemplate create(String name, String description, String definitionJson,
            String category, String tags) {
        requireName(name);
        WfTemplate template = new WfTemplate();
        template.setName(name.trim());
        template.setDescription(description);
        template.setDefinitionJson(definitionJson == null ? "{\"nodes\":[],\"edges\":[]}" : definitionJson);
        template.setCategory(StringUtils.hasText(category) ? category : "通用");
        template.setTags(tags);
        template.setDownloadCount(0);
        template.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(template);
        return template;
    }

    /** 更新模板。 */
    public WfTemplate update(Long id, String name, String description, String definitionJson,
            String category, String tags) {
        WfTemplate template = requireTemplate(id);
        if (StringUtils.hasText(name)) {
            template.setName(name.trim());
        }
        if (description != null) {
            template.setDescription(description);
        }
        if (StringUtils.hasText(definitionJson)) {
            template.setDefinitionJson(definitionJson);
        }
        if (StringUtils.hasText(category)) {
            template.setCategory(category);
        }
        if (tags != null) {
            template.setTags(tags);
        }
        templateMapper.updateById(template);
        return template;
    }

    /** 删除模板。 */
    public boolean delete(Long id) {
        return templateMapper.deleteById(id) > 0;
    }

    /** 应用模板：下载计数 +1 并返回模板（由调用方创建流程）。 */
    public WfTemplate apply(Long id) {
        WfTemplate template = requireTemplate(id);
        template.setDownloadCount(template.getDownloadCount() + 1);
        templateMapper.updateById(template);
        return template;
    }

    public WfTemplate requireTemplate(Long id) {
        WfTemplate template = templateMapper.selectById(id);
        ValidationUtil.requireNotNull(template, "模板不存在: id=");
        return template;
    }

    private void requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
    }
}
