package com.zimo.module.ai;

import com.zimo.framework.common.PluginRegister;

/**
 * 可视化工作流编排插件身份声明。
 *
 * <p>只声明插件元数据，不含任何业务逻辑与依赖注入；由 {@code AiWorkflowAutoConfiguration}
 * 注册为 Bean，再由 {@code PluginRegistry} 汇总进 {@code GET /api/plugins}。</p>
 *
 * <p>{@code apiPrefix} 对应 {@code WfWorkflowController} / {@code WfRunController} 等
 * 实际控制器前缀 {@code /api/biz/wf}；{@code frontendModule} 必须与前端
 * {@code localPluginModules} 的 key、以及目录 {@code frontend/modules/workflow} 三者一致。</p>
 *
 * @author WorkBuddy
 * @since 2026-11-06
 */
public class WorkflowPluginRegister implements PluginRegister {

    @Override
    public String getPluginId() {
        return "workflow";
    }

    @Override
    public String getPluginName() {
        return "工作流编排";
    }

    @Override
    public String getApiPrefix() {
        return "/api/biz/wf";
    }

    @Override
    public String getFrontendRoute() {
        return "/wf";
    }

    @Override
    public String getFrontendModule() {
        return "workflow";
    }

    @Override
    public String getAgentName() {
        return "workflow-agent";
    }

    @Override
    public int getOrder() {
        return 7;
    }
}
