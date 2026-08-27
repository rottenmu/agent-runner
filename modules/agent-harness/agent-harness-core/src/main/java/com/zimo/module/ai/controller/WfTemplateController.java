package com.zimo.module.ai.controller;

import com.zimo.framework.common.ApiResponse;
import cn.hutool.core.util.StrUtil;
import com.zimo.module.ai.workflow.WfTemplate;
import com.zimo.module.ai.workflow.WfTemplateService;
import com.zimo.module.ai.workflow.WfWorkflow;
import com.zimo.module.ai.workflow.WfWorkflowService;
import java.util.List;
import java.util.Map;
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
 * 工作流模板市场接口。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@RestController
@RequestMapping("/api/biz/wf/templates")
public class WfTemplateController {

    private final WfTemplateService templateService;
    private final WfWorkflowService workflowService;

    public WfTemplateController(WfTemplateService templateService, WfWorkflowService workflowService) {
        this.templateService = templateService;
        this.workflowService = workflowService;
    }

    @GetMapping
    public ApiResponse<List<WfTemplate>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(templateService.list(category, keyword));
    }

    @PostMapping
    public ApiResponse<WfTemplate> create(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(templateService.create(
                body.get("name"), body.get("description"), body.get("definitionJson"),
                body.get("category"), body.get("tags")));
    }

    @PutMapping("/{id}")
    public ApiResponse<WfTemplate> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(templateService.update(
                id, body.get("name"), body.get("description"), body.get("definitionJson"),
                body.get("category"), body.get("tags")));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return ApiResponse.ok();
    }

    /** 应用模板：下载计数 +1，并以模板创建新流程。 */
    @PostMapping("/{id}/apply")
    public ApiResponse<WfWorkflow> apply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        WfTemplate template = templateService.apply(id);
        String name = body == null ? null : body.get("name");
        String flowName = (StrUtil.isBlank(name)) ? template.getName() + "（模板应用）" : name;
        return ApiResponse.ok(workflowService.applyTemplate(id, template, flowName));
    }
}
