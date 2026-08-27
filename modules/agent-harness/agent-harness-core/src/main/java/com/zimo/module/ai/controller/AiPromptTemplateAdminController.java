package com.zimo.module.ai.controller;

import com.zimo.framework.common.ApiResponse;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.common.BizException;
import com.zimo.module.ai.management.AiPromptTemplate;
import com.zimo.module.ai.management.AiPromptTemplateGenerateRequest;
import com.zimo.module.ai.management.AiPromptTemplateRequest;
import com.zimo.module.ai.management.AiPromptTemplateService;
import com.zimo.module.ai.management.AiPromptVersionService;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 提示词模板管理接口，统一前缀为 {@code /api/biz/ai/prompt-templates}。
 *
 * <p>接口面向后台管理页面并统一返回 {@link ApiResponse}；创建 / 更新成功后自动生成版本快照。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@RestController
@RequestMapping("/api/biz/ai/prompt-templates")
public class AiPromptTemplateAdminController {

    private final AiPromptTemplateService service;
    private final AiPromptVersionService versionService;

    /**
     * 创建提示词模板管理控制器。
     *
     * @param service 提示词模板服务，不允许为空
     * @param versionService 提示词版本服务，不允许为空
     */
    public AiPromptTemplateAdminController(
            AiPromptTemplateService service,
            AiPromptVersionService versionService) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.versionService = Objects.requireNonNull(versionService, "versionService must not be null");
    }

    /** 按可选模板类型查询未删除模板，无数据时返回空列表。 */
    @GetMapping
    public ApiResponse<List<AiPromptTemplate>> list(@RequestParam(required = false) String templateType) {
        return AiAdminControllerSupport.call(() -> StrUtil.isBlank(templateType)
                ? service.list()
                : service.list(templateType));
    }

    /** 创建提示词模板，CoSTAR 六段内容必须完整；成功后自动生成版本 v1。 */
    @PostMapping
    public ApiResponse<AiPromptTemplate> create(@RequestBody AiPromptTemplateRequest request) {
        return AiAdminControllerSupport.call(() -> {
            AiPromptTemplate template = service.create(request);
            versionService.capture(template, request.getCreatedBy(), request.getCreatedName());
            return template;
        });
    }

    /** 更新指定提示词模板；目标不存在时抛出 404 业务异常。更新成功后自动生成新版本。 */
    @PutMapping("/{id}")
    public ApiResponse<AiPromptTemplate> update(
            @PathVariable Long id,
            @RequestBody AiPromptTemplateRequest request) {
        return AiAdminControllerSupport.call(() -> {
            AiPromptTemplate template = service.update(id, request);
            if (template == null) {
                throw new BizException(404, "提示词模板不存在");
            }
            versionService.capture(template, request.getUpdatedBy(), request.getUpdatedName());
            return template;
        });
    }

    /** 逻辑删除指定提示词模板；目标不存在时抛出 404 业务异常。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            if (!service.delete(id)) {
                throw new BizException(404, "提示词模板不存在");
            }
            return ApiResponse.ok();
        } catch (IllegalArgumentException exception) {
            throw AiAdminControllerSupport.badRequest(exception);
        }
    }

    /** 根据业务描述生成 CoSTAR 模板草稿，不立即写入数据库。 */
    @PostMapping("/generate")
    public ApiResponse<AiPromptTemplate> generate(@RequestBody AiPromptTemplateGenerateRequest request) {
        return AiAdminControllerSupport.call(() -> service.generate(request));
    }
}
