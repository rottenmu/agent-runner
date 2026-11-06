package com.zimo.module.ds;

import com.zimo.framework.common.PluginRegister;

/**
 * 数据源管理插件身份声明。
 *
 * <p>只声明插件元数据，不含任何业务逻辑与依赖注入；由 {@code DsAutoConfiguration}
 * 注册为 Bean，再由 {@code PluginRegistry} 汇总进 {@code GET /api/plugins}。</p>
 *
 * <p>{@code apiPrefix} 对应 {@code DsDataSourceController} 等实际控制器前缀 {@code /api/biz/ds}；
 * {@code frontendModule} 必须与前端 {@code localPluginModules} 的 key、以及目录
 * {@code frontend/modules/datasource} 三者一致。</p>
 *
 * @author WorkBuddy
 * @since 2026-11-06
 */
public class DsPluginRegister implements PluginRegister {

    @Override
    public String getPluginId() {
        return "datasource";
    }

    @Override
    public String getPluginName() {
        return "数据源管理";
    }

    @Override
    public String getApiPrefix() {
        return "/api/biz/ds";
    }

    @Override
    public String getFrontendRoute() {
        return "/ds";
    }

    @Override
    public String getFrontendModule() {
        return "datasource";
    }

    @Override
    public String getAgentName() {
        return "datasource-agent";
    }

    @Override
    public int getOrder() {
        return 8;
    }
}
