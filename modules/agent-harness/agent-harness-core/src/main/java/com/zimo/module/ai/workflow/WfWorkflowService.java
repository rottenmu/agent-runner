package com.zimo.module.ai.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.framework.common.validation.ValidationUtil;
import com.zimo.module.ai.mapper.WfWorkflowMapper;
import com.zimo.module.ai.mapper.WfWorkflowVersionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * 工作流定义服务：CRUD、发布生成版本、模板应用。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
public class WfWorkflowService {

    private final WfWorkflowMapper workflowMapper;
    private final WfWorkflowVersionMapper versionMapper;

    public WfWorkflowService(
            WfWorkflowMapper workflowMapper,
            WfWorkflowVersionMapper versionMapper) {
        this.workflowMapper = Objects.requireNonNull(workflowMapper, "workflowMapper must not be null");
        this.versionMapper = Objects.requireNonNull(versionMapper, "versionMapper must not be null");
    }

    /** 全部流程（按更新时间倒序）。 */
    public List<WfWorkflow> list() {
        return workflowMapper.selectList(Wrappers.<WfWorkflow>lambdaQuery()
                .orderByDesc(WfWorkflow::getUpdatedAt));
    }

    /** 创建流程（草稿）。 */
    public WfWorkflow create(String name, String description, String definitionJson) {
        requireName(name);
        WfWorkflow workflow = new WfWorkflow();
        workflow.setName(name.trim());
        workflow.setDescription(description);
        workflow.setDefinitionJson(definitionJson == null ? "{\"nodes\":[],\"edges\":[]}" : definitionJson);
        workflow.setVersionNo(1);
        workflow.setStatus("draft");
        workflow.setSource("manual");
        LocalDateTime now = LocalDateTime.now();
        workflow.setCreatedAt(now);
        workflow.setUpdatedAt(now);
        workflowMapper.insert(workflow);
        return workflow;
    }

    /** 更新流程定义；已发布流程更新后回到草稿状态。 */
    public WfWorkflow update(Long id, String name, String description, String definitionJson) {
        WfWorkflow workflow = requireWorkflow(id);
        if (StringUtils.hasText(name)) {
            workflow.setName(name.trim());
        }
        if (description != null) {
            workflow.setDescription(description);
        }
        if (StringUtils.hasText(definitionJson)) {
            workflow.setDefinitionJson(definitionJson);
        }
        if ("published".equals(workflow.getStatus())) {
            workflow.setStatus("draft");
        }
        workflow.setUpdatedAt(LocalDateTime.now());
        workflowMapper.updateById(workflow);
        return workflow;
    }

    /** 删除流程（软删除）。 */
    public boolean delete(Long id) {
        return workflowMapper.deleteById(id) > 0;
    }

    /** 发布流程：保存当前定义为版本快照，版本号递增，状态置为已发布。 */
    public WfWorkflow publish(Long id, String changeNote) {
        WfWorkflow workflow = requireWorkflow(id);
        WfWorkflowVersion version = new WfWorkflowVersion();
        version.setWorkflowId(id);
        version.setVersionNo(workflow.getVersionNo());
        version.setDefinitionJson(workflow.getDefinitionJson());
        version.setChangeNote(changeNote);
        version.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(version);

        workflow.setVersionNo(workflow.getVersionNo() + 1);
        workflow.setStatus("published");
        workflow.setUpdatedAt(LocalDateTime.now());
        workflowMapper.updateById(workflow);
        return workflow;
    }

    /** 流程版本列表。 */
    public List<WfWorkflowVersion> versions(Long workflowId) {
        return versionMapper.selectList(Wrappers.<WfWorkflowVersion>lambdaQuery()
                .eq(WfWorkflowVersion::getWorkflowId, workflowId)
                .orderByDesc(WfWorkflowVersion::getVersionNo));
    }

    /** 按模板创建流程（应用于模板市场）。 */
    public WfWorkflow applyTemplate(Long templateId, WfTemplate template, String name) {
        requireName(name);
        WfWorkflow workflow = new WfWorkflow();
        workflow.setName(name.trim());
        workflow.setDescription(template.getDescription());
        workflow.setDefinitionJson(template.getDefinitionJson());
        workflow.setVersionNo(1);
        workflow.setStatus("draft");
        workflow.setSource("marketplace");
        workflow.setTemplateId(templateId);
        LocalDateTime now = LocalDateTime.now();
        workflow.setCreatedAt(now);
        workflow.setUpdatedAt(now);
        workflowMapper.insert(workflow);
        return workflow;
    }

    public WfWorkflow requireWorkflow(Long id) {
        WfWorkflow workflow = workflowMapper.selectById(id);
        ValidationUtil.requireNotNull(workflow, "流程不存在: id=");
        return workflow;
    }

    private void requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("流程名称不能为空");
        }
    }
}
