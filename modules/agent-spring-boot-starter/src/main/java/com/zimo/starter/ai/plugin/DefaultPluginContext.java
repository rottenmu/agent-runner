package com.zimo.starter.ai.plugin;

import com.zimo.starter.ai.agent.AiCapabilityProvider;
import com.zimo.starter.ai.agent.AiProfilePatchProvider;
import com.zimo.starter.ai.agent.ToolExecutionListener;
import com.zimo.starter.ai.sandbox.SandboxBackend;
import java.nio.file.Path;
import java.util.List;

/**
 * 插件上下文默认实现：注册项记录到管理器（卸载时移除）。
 */
class DefaultPluginContext implements PluginContext {

    private final String pluginId;
    private final DynamicPluginManager manager;
    private final Path dataDir;
    private final List<Object> registrations;
    private final PluginEventBus eventBus;

    DefaultPluginContext(String pluginId, DynamicPluginManager manager, Path dataDir,
                         PluginEventBus eventBus) {
        this(pluginId, manager, dataDir, eventBus, new java.util.ArrayList<>());
    }

    /** 共享注册台账构造：registrations 与 LoadedPlugin 共享同一引用，卸载可回滚。 */
    DefaultPluginContext(String pluginId, DynamicPluginManager manager, Path dataDir,
                         PluginEventBus eventBus, List<Object> registrations) {
        this.pluginId = pluginId;
        this.manager = manager;
        this.dataDir = dataDir;
        this.registrations = registrations == null ? new java.util.ArrayList<>() : registrations;
        this.eventBus = eventBus == null ? new PluginEventBus() : eventBus;
    }

    @Override
    public void registerCapability(AiCapabilityProvider capability) {
        manager.addCapability(capability, registrations);
    }

    @Override
    public void registerListener(ToolExecutionListener listener) {
        manager.addListener(listener, registrations);
    }

    @Override
    public void registerSandbox(SandboxBackend sandbox) {
        manager.addSandbox(sandbox, registrations);
    }

    @Override
    public void registerProfilePatch(AiProfilePatchProvider patch) {
        manager.addPatch(patch, registrations);
    }

    @Override
    public void registerMiddleware(com.zimo.starter.ai.agent.AiAgentMiddleware middleware) {
        manager.addMiddleware(middleware, registrations);
    }

    @Override
    public PluginEventBus eventBus() {
        return eventBus;
    }

    @Override
    public Path dataDir() {
        return dataDir;
    }

    /** 供同包测试：读取注册台账（等价卸载回滚集合）。 */
    List<Object> registrationsForTest() {
        return registrations;
    }
}
