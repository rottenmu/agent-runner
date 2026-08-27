package com.zimo.starter.ai.plugin;

/**
 * 动态插件 SPI：插件 jar 中的实现类，具备装载/卸载生命周期。
 *
 * <p>插件 jar 放入 {@code data/plugins/} 后由 {@link DynamicPluginManager} 动态加载：
 * <ol>
 *   <li>onLoad：通过 {@link PluginContext} 注册能力（AiCapabilityProvider）、
 *       工具钩子（ToolExecutionListener）、沙箱（SandboxBackend）等；</li>
 *   <li>onUnload：撤销注册（管理器自动移除），随 ClassLoader 关闭回收。</li>
 * </ol></p>
 */
public interface AiPlugin {

    /** 插件唯一标识。 */
    String id();

    /** 插件版本。 */
    String version();

    /** 插件装载回调。 */
    void onLoad(PluginContext context);

    /** 插件卸载回调（撤销注册）。 */
    void onUnload();
}
