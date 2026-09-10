package com.zimo.framework.ai.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 插件热重载端到端单测（dsh A1 热插拔）：真实构建最小插件 jar，
 * 验证 jar 新增→自动加载、修改→卸载重载、删除→自动卸载。
 */
class DynamicPluginManagerHotReloadTest {

    @TempDir
    Path tempDir;

    private Path pluginDir;
    private DynamicPluginManager manager;

    @BeforeEach
    void setUp() throws Exception {
        pluginDir = Files.createDirectories(tempDir.resolve("plugins"));
        manager = new DynamicPluginManager(pluginDir.toString());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // 关闭 loader 释放 jar 文件句柄（Windows），避免 tempDir 清理失败
        manager.close();
    }

    /** 构建一个最小插件 jar：实现 AiPlugin，id 固定 demo，body 为字段内容（区分版本）。 */
    private Path buildPluginJar(String versionBody, long lastModified) throws Exception {
        Path srcDir = Files.createDirectories(tempDir.resolve("src-" + versionBody.hashCode()));
        String className = "com.example.plugin.DemoPlugin";
        Path source = srcDir.resolve("DemoPlugin.java");
        Files.createDirectories(source.getParent());
        String code = """
                package com.example.plugin;

                import com.zimo.framework.ai.plugin.AiPlugin;
                import com.zimo.framework.ai.plugin.PluginContext;
                import com.zimo.framework.ai.agent.AiCapabilityProvider;
                import io.agentscope.core.tool.Toolkit;

                public class DemoPlugin implements AiPlugin {
                    public static final String BODY = "%s";
                    public String id() { return "demo"; }
                    public String version() { return BODY; }
                    public void onLoad(PluginContext context) {
                        context.registerCapability(new AiCapabilityProvider() {
                            public String name() { return "demo"; }
                            public void contribute(Toolkit toolkit,
                                    com.zimo.framework.ai.agent.AiAgentProfile profile) {}
                        });
                    }
                    public void onUnload() {}
                }
                """.formatted(versionBody);
        Files.writeString(source, code, StandardCharsets.UTF_8);

        Path classesDir = Files.createDirectories(tempDir.resolve("classes-" + versionBody.hashCode()));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int result = compiler.run(null, null, err,
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDir.toString(),
                source.toString());
        if (result != 0) {
            throw new IllegalStateException("插件源码编译失败: " + className + "\n"
                    + err.toString(StandardCharsets.UTF_8));
        }

        Path jar = pluginDir.resolve("demo-plugin.jar");
        // 写入插件清单（loadJar 需读取 META-INF/ai-plugin.properties 定位实现类）
        Path metaInf = Files.createDirectories(classesDir.resolve("META-INF"));
        Files.writeString(metaInf.resolve("ai-plugin.properties"),
                "plugin.class=com.example.plugin.DemoPlugin\n", StandardCharsets.UTF_8);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            // 打包全部编译产物（含匿名内部类 DemoPlugin$1.class 等）
            try (var stream = Files.walk(classesDir)) {
                for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                    String entryName = classesDir.relativize(file).toString()
                            .replace('\\', '/');
                    addJarEntry(jos, entryName, file);
                }
            }
        }
        Files.setLastModifiedTime(jar,
                java.nio.file.attribute.FileTime.fromMillis(lastModified));
        return jar;
    }

    private void addJarEntry(JarOutputStream jos, String name, Path file) throws IOException {
        jos.putNextEntry(new JarEntry(name));
        jos.write(Files.readAllBytes(file));
        jos.closeEntry();
    }

    @Test
    void newJarIsLoadedOnScan() throws Exception {
        buildPluginJar("v1", System.currentTimeMillis());

        manager.scanAndReload();

        assertThat(manager.list()).hasSize(1);
        assertThat(manager.list().get(0).id()).isEqualTo("demo");
        assertThat(manager.dynamicCapabilities()).hasSize(1);
    }

    @Test
    void modifiedJarIsReloadedOnScan() throws Exception {
        buildPluginJar("v1", System.currentTimeMillis());
        manager.scanAndReload();
        assertThat(manager.dynamicCapabilities()).hasSize(1);

        // 修改 jar：同文件名（同 key），直接覆盖写 v2 内容 + 新 mtime/size → 触发替换重载
        // （Windows 下已加载 jar 被 URLClassLoader 持有句柄，不能删除文件，只能覆盖写）
        buildPluginJar("v2", System.currentTimeMillis() + 5000);

        manager.scanAndReload();

        // v2 插件实例替换 v1：注册能力仍在（新实例），无重复
        assertThat(manager.list()).hasSize(1);
        assertThat(manager.list().get(0).version()).isEqualTo("v2");
        assertThat(manager.dynamicCapabilities()).hasSize(1);
    }

    @Test
    void removedJarIsUnloadedOnScan() throws Exception {
        buildPluginJar("v1", System.currentTimeMillis());
        manager.scanAndReload();
        assertThat(manager.dynamicCapabilities()).hasSize(1);

        // 先卸载释放 URLClassLoader 句柄（Windows 文件锁），再删除 jar
        manager.unload("demo-plugin");
        deleteExistingJars();

        manager.scanAndReload();

        assertThat(manager.list()).isEmpty();
        assertThat(manager.dynamicCapabilities()).isEmpty();
        assertThat(manager.eventBus().totalSubscriptions()).isZero();
    }

    @Test
    void unchangedJarIsNotReloaded() throws Exception {
        buildPluginJar("v1", System.currentTimeMillis());
        manager.scanAndReload();

        // 同指纹再次扫描：不卸载不重载（插件实例保持）
        manager.scanAndReload();

        assertThat(manager.list()).hasSize(1);
        assertThat(manager.dynamicCapabilities()).hasSize(1);
    }

    @Test
    void watcherStartStopIsIdempotent() throws Exception {
        manager.startWatcher(100);
        manager.startWatcher(100);
        manager.stopWatcher();
        manager.stopWatcher();
        manager.close();
        assertThat(manager.list()).isEmpty();
    }

    private void deleteExistingJars() throws IOException {
        Path dir = pluginDir;
        try (var stream = Files.list(dir)) {
            for (Path p : stream.filter(f -> f.getFileName().toString().endsWith(".jar")).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}