package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.framework.common.validation.ValidationUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.mapper.AiPromptVersionMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 提示词模板版本 / 快照服务实现。
 *
 * <p>版本内容以 JSON 保存模板完整字段，回滚时由调用方反序列化写回模板。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@Service
public class AiPromptVersionServiceImpl extends ServiceImpl<AiPromptVersionMapper, AiPromptVersion>
        implements AiPromptVersionService {

    private final ObjectMapper objectMapper;

    public AiPromptVersionServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AiPromptVersion> listByPrompt(Long promptId) {
        ValidationUtil.requireNotNull(promptId, "promptId 不能为空");
        return list(Wrappers.<AiPromptVersion>lambdaQuery()
                .eq(AiPromptVersion::getPromptId, promptId)
                .orderByDesc(AiPromptVersion::getVersionNo)
                .orderByAsc(AiPromptVersion::getId));
    }

    @Override
    public AiPromptVersion capture(AiPromptTemplate template, String createdBy, String createdName) {
        return saveVersion(template, "version", null, createdBy, createdName);
    }

    @Override
    public AiPromptVersion createSnapshot(AiPromptTemplate template, String snapshotName,
            String createdBy, String createdName) {
        if (!StringUtils.hasText(snapshotName)) {
            throw new IllegalArgumentException("快照名称不能为空");
        }
        return saveVersion(template, "snapshot", snapshotName.trim(), createdBy, createdName);
    }

    private AiPromptVersion saveVersion(AiPromptTemplate template, String versionType,
            String snapshotName, String createdBy, String createdName) {
        if (template == null || template.getId() == null) {
            throw new IllegalArgumentException("模板不存在");
        }
        AiPromptVersion version = new AiPromptVersion();
        version.setPromptId(template.getId());
        version.setVersionType(versionType);
        version.setSnapshotName(snapshotName);
        version.setContentJson(toJson(template));
        version.setCreatedBy(createdBy);
        version.setCreatedName(createdName);
        version.setCreateTime(LocalDateTime.now());
        version.setVersionNo(nextVersionNo(template.getId()));
        save(version);
        return version;
    }

    private int nextVersionNo(Long promptId) {
        List<AiPromptVersion> existing = list(Wrappers.<AiPromptVersion>lambdaQuery()
                .eq(AiPromptVersion::getPromptId, promptId)
                .orderByDesc(AiPromptVersion::getVersionNo)
                .last("limit 1"));
        if (existing.isEmpty()) {
            return 1;
        }
        return existing.get(0).getVersionNo() + 1;
    }

    private String toJson(AiPromptTemplate template) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", template.getId());
        map.put("templateCode", template.getTemplateCode());
        map.put("templateName", template.getTemplateName());
        map.put("description", template.getDescription());
        map.put("templateType", template.getTemplateType());
        map.put("contextText", template.getContextText());
        map.put("objectiveText", template.getObjectiveText());
        map.put("styleText", template.getStyleText());
        map.put("toneText", template.getToneText());
        map.put("audienceText", template.getAudienceText());
        map.put("responseText", template.getResponseText());
        map.put("sourceType", template.getSourceType());
        map.put("businessDescription", template.getBusinessDescription());
        map.put("enabled", template.isEnabled());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalArgumentException("版本内容序列化失败", e);
        }
    }
}
