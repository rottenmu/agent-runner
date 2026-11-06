package com.zimo.module.tools;

import com.zimo.framework.common.PluginRegister;

/**
 * 工具中心插件身份声明。
 *
 * <p>只声明插件元数据，不含任何业务逻辑与依赖注入；由 {@code ToolsAutoConfiguration}
 * 注册为 Bean，再由 {@code PluginRegistry} 汇总进 {@code GET /api/plugins}。</p>
 *
 * <p>{@code apiPrefix} 取 {@code /api/biz/ai/tools} —— 该模块的控制器（工具定义、权限、
 * 插件市场、脚本）都挂在这个前缀下，而非 {@code /api/biz/tools}。这是本模块与
 * {@code AiPluginRegister}（前缀 {@code /api/ai}）最容易混淆的一点：两者前缀并不互相包含。</p>
 *
 * @author WorkBuddy
 * @since 2026-11-06
 */
public class ToolsPluginRegister implements PluginRegister {

    @Override
    public String getPluginId() {
        return "tools";
    }

    @Override
    public String getPluginName() {
        return "工具中心";
    }

    @Override
    public String getApiPrefix() {
        return "/api/biz/ai/tools";
    }

    @Override
    public String getFrontendRoute() {
        return "/tools";
    }

    @Override
    public String getFrontendModule() {
        return "tools";
    }

    @Override
    public String getAgentName() {
        return "tools-agent";
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
