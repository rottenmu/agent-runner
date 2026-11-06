package com.zimo.module.trace;

import com.zimo.framework.common.PluginRegister;

/**
 * 链路观测插件身份声明。
 *
 * <p>只声明插件元数据，不含任何业务逻辑与依赖注入；由 {@code GenAiTraceAutoConfiguration}
 * 注册为 Bean，再由 {@code PluginRegistry} 汇总进 {@code GET /api/plugins}。</p>
 *
 * <p><b>本插件的 {@code pluginId} 是 {@code agent-trace-ui} 而不是 {@code agenttrace}</b>：
 * 必须与前端 {@code localPluginModules} 的 key、目录名 {@code frontend/modules/agent-trace-ui}
 * 三者严格一致，否则 loader 找不到模块只会打一条 {@code Frontend module not found for plugin}
 * 提示，表现为「菜单项在、点进去空白」。</p>
 *
 * <p>{@code apiPrefix} 取 {@code /api/biz/ai/observ}：观测 UI 的数据来自
 * {@code ObservController}，该控制器物理上仍在 agent-harness（本次不做迁移），
 * 这里按<b>逻辑归属</b>登记。</p>
 *
 * @author WorkBuddy
 * @since 2026-11-06
 */
public class TracePluginRegister implements PluginRegister {

    @Override
    public String getPluginId() {
        return "agent-trace-ui";
    }

    @Override
    public String getPluginName() {
        return "链路观测";
    }

    @Override
    public String getApiPrefix() {
        return "/api/biz/ai/observ";
    }

    @Override
    public String getFrontendRoute() {
        return "/agent-trace";
    }

    @Override
    public String getFrontendModule() {
        return "agent-trace-ui";
    }

    @Override
    public String getAgentName() {
        return "trace-agent";
    }

    @Override
    public int getOrder() {
        return 14;
    }
}
