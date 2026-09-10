package com.zimo.framework.ai.plugin;

import com.zimo.framework.ai.agent.AiCapabilityProvider;
import com.zimo.framework.ai.agent.ToolExecutionListener;
import com.zimo.framework.ai.sandbox.SandboxBackend;

/**
 * 插件上下文：插件在 onLoad 中向运行时注册动态能力。
 */
public interface PluginContext {

    /** 注册动态能力提供方（工具集）。 */
    void registerCapability(AiCapabilityProvider capability);

    /** 注册动态工具执行钩子。 */
    void registerListener(ToolExecutionListener listener);

    /** 注册动态沙箱后端。 */
    void registerSandbox(SandboxBackend sandbox);

    /** 注册动态 Profile 补丁。 */
    void registerProfilePatch(com.zimo.framework.ai.agent.AiProfilePatchProvider patch);

    /** 注册动态 around 中间件（对齐 dsh 事件级瀑布：插件可在请求链任意点包裹/接管）。 */
    void registerMiddleware(com.zimo.framework.ai.agent.AiAgentMiddleware middleware);

    /** 插件事件总线：订阅运行时事件（turn 循环/工具流水线/生命周期）。 */
    com.zimo.framework.ai.plugin.PluginEventBus eventBus();

    /** 插件数据目录（data/plugins/{pluginId}/）。 */
    java.nio.file.Path dataDir();
}
