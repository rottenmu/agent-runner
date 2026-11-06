package com.zimo.module.rag;

import com.zimo.framework.common.PluginRegister;

/**
 * RAG 知识库插件身份声明。
 *
 * <p>只声明插件元数据，不含任何业务逻辑与依赖注入；由 {@code RagAutoConfiguration}
 * 注册为 Bean，再由 {@code PluginRegistry} 汇总进 {@code GET /api/plugins}。</p>
 *
 * <p>{@code apiPrefix} 对应 {@code RagKbController} / {@code RagDocumentController} 等
 * 实际控制器前缀 {@code /api/biz/rag}；{@code frontendModule} 必须与前端
 * {@code localPluginModules} 的 key、以及目录 {@code frontend/modules/rag} 三者一致。</p>
 *
 * @author WorkBuddy
 * @since 2026-11-06
 */
public class RagPluginRegister implements PluginRegister {

    @Override
    public String getPluginId() {
        return "rag";
    }

    @Override
    public String getPluginName() {
        return "RAG 知识库";
    }

    @Override
    public String getApiPrefix() {
        return "/api/biz/rag";
    }

    @Override
    public String getFrontendRoute() {
        return "/rag";
    }

    @Override
    public String getFrontendModule() {
        return "rag";
    }

    @Override
    public String getAgentName() {
        return "rag-agent";
    }

    @Override
    public int getOrder() {
        return 9;
    }
}
