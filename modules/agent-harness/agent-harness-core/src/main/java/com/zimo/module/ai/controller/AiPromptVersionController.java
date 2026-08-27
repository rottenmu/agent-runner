package com.zimo.module.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zimo.framework.common.validation.ValidationUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.ai.management.AiPromptTemplate;
import com.zimo.module.ai.management.AiPromptTemplateRequest;
import com.zimo.module.ai.management.AiPromptTemplateService;
import com.zimo.module.ai.management.AiPromptVersion;
import com.zimo.module.ai.management.AiPromptVersionService;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提示词模板版本 / 快照接口。
 *
 * <p>提供版本列表、版本详情、手动快照与版本回滚能力。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@RestController
@RequestMapping("/api/biz/ai/prompt-templates")
public class AiPromptVersionController {

    private final AiPromptVersionService versionService;
    private final AiPromptTemplateService templateService;
    private final ObjectMapper objectMapper;

    public AiPromptVersionController(
            AiPromptVersionService versionService,
            AiPromptTemplateService templateService,
            ObjectMapper objectMapper) {
        this.versionService = versionService;
        this.templateService = templateService;
        this.objectMapper = objectMapper;
    }

    /** 查询模板的全部版本与快照。 */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<AiPromptVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(versionService.listByPrompt(id));
    }

    /** 查询单个版本 / 快照详情（含内容 JSON）。 */
    @GetMapping("/versions/{versionId}")
    public ApiResponse<AiPromptVersion> versionDetail(@PathVariable Long versionId) {
        AiPromptVersion version = requireVersion(versionId);
        return ApiResponse.ok(version);
    }

    /** 为模板手动创建快照。 */
    @PostMapping("/{id}/versions/snapshot")
    public ApiResponse<AiPromptVersion> snapshot(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String name = body == null ? null : body.get("snapshotName");
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("快照名称不能为空");
        }
        AiPromptTemplate template = requireTemplate(id);
        AiPromptVersion version = versionService.createSnapshot(template, name, null, null);
        return ApiResponse.ok(version);
    }

    /** 将模板回滚到指定版本（回滚后自动生成新版本记录）。 */
    @PutMapping("/versions/{versionId}/restore")
    public ApiResponse<AiPromptTemplate> restore(@PathVariable Long versionId) {
        AiPromptVersion version = requireVersion(versionId);
        AiPromptTemplate snapshotTemplate = fromJson(version.getContentJson());
        if (snapshotTemplate == null || snapshotTemplate.getId() == null) {
            throw new IllegalArgumentException("版本内容为空或损坏，无法回滚");
        }
        AiPromptTemplateRequest request = toRequest(snapshotTemplate);
        AiPromptTemplate restored = templateService.update(snapshotTemplate.getId(), request);
        ValidationUtil.requireNotNull(restored, "原模板不存在，无法回滚");
        versionService.capture(restored, null, null);
        return ApiResponse.ok(restored);
    }

    private AiPromptVersion requireVersion(Long versionId) {
        AiPromptVersion version = versionService.getById(versionId);
        ValidationUtil.requireNotNull(version, "版本不存在: id=");
        return version;
    }

    private AiPromptTemplate requireTemplate(Long id) {
        List<AiPromptTemplate> list = templateService.list();
        return list.stream().filter(t -> id.equals(t.getId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: id=" + id));
    }

    /** 将版本 JSON 反序列化为模板实体。 */
    private AiPromptTemplate fromJson(String contentJson) {
        if (!StringUtils.hasText(contentJson)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(contentJson);
            AiPromptTemplate template = new AiPromptTemplate();
            template.setId(node.hasNonNull("id") ? node.get("id").asLong() : null);
            template.setTemplateCode(text(node, "templateCode"));
            template.setTemplateName(text(node, "templateName"));
            template.setDescription(text(node, "description"));
            template.setTemplateType(text(node, "templateType"));
            template.setContextText(text(node, "contextText"));
            template.setObjectiveText(text(node, "objectiveText"));
            template.setStyleText(text(node, "styleText"));
            template.setToneText(text(node, "toneText"));
            template.setAudienceText(text(node, "audienceText"));
            template.setResponseText(text(node, "responseText"));
            template.setSourceType(text(node, "sourceType"));
            template.setBusinessDescription(text(node, "businessDescription"));
            if (node.has("enabled")) {
                template.setEnabled(node.get("enabled").asBoolean());
            }
            return template;
        } catch (Exception e) {
            throw new IllegalArgumentException("版本内容解析失败", e);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private AiPromptTemplateRequest toRequest(AiPromptTemplate template) {
        AiPromptTemplateRequest request = new AiPromptTemplateRequest();
        request.setTemplateCode(orEmpty(template.getTemplateCode()));
        request.setTemplateName(orEmpty(template.getTemplateName()));
        request.setDescription(template.getDescription());
        request.setTemplateType(orEmpty(template.getTemplateType()));
        request.setContextText(orEmpty(template.getContextText()));
        request.setObjectiveText(orEmpty(template.getObjectiveText()));
        request.setStyleText(orEmpty(template.getStyleText()));
        request.setToneText(orEmpty(template.getToneText()));
        request.setAudienceText(orEmpty(template.getAudienceText()));
        request.setResponseText(orEmpty(template.getResponseText()));
        request.setSourceType(orEmpty(template.getSourceType()));
        request.setBusinessDescription(template.getBusinessDescription());
        request.setEnabled(template.isEnabled());
        return request;
    }

    private String orEmpty(String value) {
        return StringUtils.hasText(value) ? value : "";
    }
}
