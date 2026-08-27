package com.zimo.starter.ai.plugin;

import com.zimo.starter.ai.agent.AiCapabilityProvider;
import com.zimo.starter.ai.agent.AiProfilePatchProvider;
import com.zimo.starter.ai.agent.ToolExecutionListener;
import com.zimo.starter.ai.sandbox.SandboxBackend;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 动态插件管理器：从插件目录（默认 {@code data/plugins}）动态加载插件 jar。
 *
 * <p>插件 jar 需包含实现 {@link AiPlugin} 的类（构造无参）；加载时反射实例化并调用
 * {@link AiPlugin#onLoad}，注册的动态能力/钩子/沙箱由 {@link #dynamicCapabilities()} 等
 * 暴露给运行时装配链。卸载时调用 onUnload 并关闭隔离 ClassLoader。</p>
 */
public class DynamicPluginManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicPluginManager.class);

    /** 插件描述信息（管理 API 展示）。 */
    public record PluginInfo(String id, String version, String jarName, boolean loaded) {
    }

    private final Path pluginDir;
    private final Map<String, LoadedPlugin> plugins = new ConcurrentHashMap<>();
    /** 插件事件总线（插件订阅运行时事件，卸载按 id 撤销）。 */
    private final PluginEventBus eventBus = new PluginEventBus();
    /** 热重载 watch 线程（可空：未启用时无）。 */
    private volatile Thread watchThread;
    private volatile boolean watchRunning = false;

    /** 动态注册集合（按注册顺序，卸载时移除）。 */
    private final List<AiCapabilityProvider> capabilities = new ArrayList<>();
    private final List<ToolExecutionListener> listeners = new ArrayList<>();
    private final List<SandboxBackend> sandboxes = new ArrayList<>();
    private final List<AiProfilePatchProvider> patches = new ArrayList<>();
    private final List<com.zimo.starter.ai.agent.AiAgentMiddleware> middlewares = new ArrayList<>();

    private static final class LoadedPlugin {
        final String id;
        final AiPlugin instance;
        final URLClassLoader loader;
        final List<Object> registrations = new ArrayList<>();
        /** jar 文件指纹（热重载变更检测）。 */
        volatile long jarLastModified;
        volatile long jarSize;

        LoadedPlugin(String id, AiPlugin instance, URLClassLoader loader) {
            this.id = id;
            this.instance = instance;
            this.loader = loader;
        }
    }

    public DynamicPluginManager(String pluginDir) {
        this.pluginDir = Path.of(pluginDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.pluginDir);
        } catch (java.io.IOException e) {
            log.warn("插件目录创建失败: {}", e.getMessage());
        }
        log.info("动态插件管理器已启动，插件目录: {}", this.pluginDir);
    }

    /** 加载并装载指定 jar（已加载则跳过）。 */
    public synchronized PluginInfo loadJar(Path jar) {
        String name = jar.getFileName().toString();
        String key = name.substring(0, name.length() - 4); // 去 .jar
        if (plugins.containsKey(key)) {
            log.info("插件已加载: {}", key);
            return plugins.get(key) == null ? null : info(key);
        }
        try {
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jar.toUri().toURL()}, getClass().getClassLoader());
            AiPlugin plugin = instantiatePlugin(loader);
            String id = plugin.id();
            LoadedPlugin loaded = new LoadedPlugin(id, plugin, loader);
            loaded.jarLastModified = Files.getLastModifiedTime(jar).toMillis();
            loaded.jarSize = Files.size(jar);
            plugins.put(key, loaded);
            // 共享注册台账：context 的注册项直接落入 LoadedPlugin.registrations，
            // 卸载时 removeRegistrations 才能按台账清理（修复：此前两列表分离导致注册残留）
            PluginContext ctx = new DefaultPluginContext(
                    id, this, pluginDir.resolve(id), eventBus, loaded.registrations);
            plugin.onLoad(ctx);
            eventBus.emit(PluginEventBus.PLUGIN_LOADED,
                    java.util.Map.of("pluginId", id, "version", plugin.version()));
            log.info("插件已装载: {} v{} (jar={})", id, plugin.version(), name);
            return info(key);
        } catch (Exception e) {
            log.warn("插件加载失败 {}: {}", name, e.getMessage());
            return null;
        }
    }

    /** 卸载指定插件（按 jar 名或插件 id）。 */
    public synchronized boolean unload(String keyOrId) {
        LoadedPlugin loaded = plugins.remove(keyOrId);
        if (loaded == null) {
            loaded = plugins.values().stream()
                    .filter(p -> p.id.equals(keyOrId))
                    .findFirst().orElse(null);
            if (loaded != null) {
                plugins.remove(keyFor(loaded.id));
            }
        }
        if (loaded == null) {
            return false;
        }
        try {
            loaded.instance.onUnload();
            removeRegistrations(loaded.registrations);
            eventBus.removeAll(loaded.id);
            loaded.loader.close();
            eventBus.emit(PluginEventBus.PLUGIN_UNLOADED,
                    java.util.Map.of("pluginId", loaded.id));
            log.info("插件已卸载: {}", loaded.id);
            return true;
        } catch (Exception e) {
            log.warn("插件卸载异常 {}: {}", loaded.id, e.getMessage());
            return false;
        }
    }

    /** 关闭：卸载全部插件并释放 ClassLoader（Bean 销毁回调）。 */
    public void close() {
        stopWatcher();
        for (String key : new ArrayList<>(plugins.keySet())) {
            unload(key);
        }
        log.info("动态插件管理器已关闭");
    }

    /* ---------------- 热重载（jar 变更自动 reload） ---------------- */

    /**
     * 启动插件目录 watch 线程：按给定间隔轮询 jar 变更，
     * 新增→加载、修改→卸载重载、删除→卸载。重复调用幂等。
     */
    public synchronized void startWatcher(long intervalMs) {
        if (watchRunning) {
            return;
        }
        long interval = intervalMs > 0 ? intervalMs : 5000;
        watchRunning = true;
        Thread thread = new Thread(() -> {
            log.info("插件热重载 watch 已启动，间隔 {}ms，目录: {}", interval, pluginDir);
            while (watchRunning) {
                try {
                    scanAndReload();
                } catch (Exception e) {
                    log.warn("插件热重载扫描异常: {}", e.getMessage());
                }
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "plugin-hot-reload");
        thread.setDaemon(true);
        watchThread = thread;
        thread.start();
    }

    /** 停止 watch 线程（幂等）。 */
    public synchronized void stopWatcher() {
        watchRunning = false;
        Thread thread = watchThread;
        watchThread = null;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /** 扫描插件目录并与已加载集合对比：执行新增/修改/删除动作。 */
    public synchronized void scanAndReload() {
        java.io.File[] jars = pluginDir.toFile().listFiles((d, n) -> n.endsWith(".jar"));
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (jars != null) {
            for (java.io.File jar : jars) {
                String key = jar.getName().substring(0, jar.getName().length() - 4);
                seen.add(key);
                LoadedPlugin loaded = plugins.get(key);
                if (loaded == null) {
                    log.info("插件热重载：发现新 jar，加载 {}", jar.getName());
                    loadJar(jar.toPath());
                } else if (jarChanged(loaded, jar.toPath())) {
                    log.info("插件热重载：jar 变更，重载 {}", jar.getName());
                    unload(key);
                    loadJar(jar.toPath());
                }
            }
        }
        // 已加载但目录中已删除 → 卸载
        for (String key : new ArrayList<>(plugins.keySet())) {
            if (!seen.contains(key)) {
                log.info("插件热重载：jar 已删除，卸载 {}", key);
                unload(key);
            }
        }
    }

    /** 判断 jar 文件是否相对上次加载发生变化（mtime 或 size）。 */
    private boolean jarChanged(LoadedPlugin loaded, Path jar) {
        try {
            long lastModified = Files.getLastModifiedTime(jar).toMillis();
            long size = Files.size(jar);
            return lastModified != loaded.jarLastModified || size != loaded.jarSize;
        } catch (Exception e) {
            return true;
        }
    }

    /** 列出所有已加载插件。 */
    public List<PluginInfo> list() {
        List<PluginInfo> infos = new ArrayList<>();
        plugins.forEach((key, p) -> infos.add(info(key)));
        return infos;
    }

    private PluginInfo info(String key) {
        LoadedPlugin p = plugins.get(key);
        return p == null ? null
                : new PluginInfo(p.id, p.instance.version(), key + ".jar", true);
    }

    private String keyFor(String pluginId) {
        for (Map.Entry<String, LoadedPlugin> e : plugins.entrySet()) {
            if (e.getValue().id.equals(pluginId)) {
                return e.getKey();
            }
        }
        return pluginId;
    }

    /**
     * 实例化插件：读取 jar 内 {@code META-INF/ai-plugin.properties} 清单
     * （键 {@code plugin.class}），反射实例化实现类（需无参构造）。
     */
    private AiPlugin instantiatePlugin(URLClassLoader loader) throws Exception {
        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream in = loader.getResourceAsStream("META-INF/ai-plugin.properties")) {
            if (in == null) {
                throw new IllegalStateException("插件清单 META-INF/ai-plugin.properties 缺失");
            }
            props.load(in);
        }
        String className = props.getProperty("plugin.class");
        if (className == null || className.isBlank()) {
            throw new IllegalStateException("插件清单缺少 plugin.class");
        }
        Class<?> clazz = Class.forName(className, true, loader);
        if (!AiPlugin.class.isAssignableFrom(clazz)) {
            throw new IllegalStateException("插件类未实现 AiPlugin: " + className);
        }
        return (AiPlugin) clazz.getDeclaredConstructor().newInstance();
    }

    private void removeRegistrations(List<Object> regs) {
        for (Object r : regs) {
            if (r instanceof AiCapabilityProvider p) {
                capabilities.remove(p);
            } else if (r instanceof ToolExecutionListener l) {
                listeners.remove(l);
            } else if (r instanceof SandboxBackend s) {
                sandboxes.remove(s);
            } else if (r instanceof AiProfilePatchProvider p) {
                patches.remove(p);
            } else if (r instanceof com.zimo.starter.ai.agent.AiAgentMiddleware m) {
                middlewares.remove(m);
            }
        }
        regs.clear();
    }

    /* ---------------- 动态注册集合 ---------------- */

    void addCapability(AiCapabilityProvider c, List<Object> regs) {
        capabilities.add(c);
        regs.add(c);
    }

    void addListener(ToolExecutionListener l, List<Object> regs) {
        listeners.add(l);
        regs.add(l);
    }

    void addSandbox(SandboxBackend s, List<Object> regs) {
        sandboxes.add(s);
        regs.add(s);
    }

    void addPatch(AiProfilePatchProvider p, List<Object> regs) {
        patches.add(p);
        regs.add(p);
    }

    void addMiddleware(com.zimo.starter.ai.agent.AiAgentMiddleware m, List<Object> regs) {
        middlewares.add(m);
        regs.add(m);
    }

    /** 动态能力提供方快照（装配时合并）。 */
    public synchronized List<AiCapabilityProvider> dynamicCapabilities() {
        return new ArrayList<>(capabilities);
    }

    /** 动态工具钩子快照。 */
    public synchronized List<ToolExecutionListener> dynamicListeners() {
        return new ArrayList<>(listeners);
    }

    /** 动态沙箱快照。 */
    public synchronized List<SandboxBackend> dynamicSandboxes() {
        return new ArrayList<>(sandboxes);
    }

    /** 动态 Profile 补丁快照。 */
    public synchronized List<AiProfilePatchProvider> dynamicPatches() {
        return new ArrayList<>(patches);
    }

    /** 动态 around 中间件快照（装配时并入 AiAgentService 中间件链）。 */
    public synchronized List<com.zimo.starter.ai.agent.AiAgentMiddleware> dynamicMiddlewares() {
        return new ArrayList<>(middlewares);
    }

    /** 插件事件总线（运行时发布 turn/工具/生命周期事件）。 */
    public PluginEventBus eventBus() {
        return eventBus;
    }

    /* ---------------- 测试辅助（同包） ---------------- */

    /** 供同包测试：执行注册回滚（等价卸载时的 removeRegistrations）。 */
    void removeRegistrationsForTest(List<Object> regs) {
        removeRegistrations(regs);
    }
}
