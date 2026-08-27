package com.zimo.module.ai.plugin;

import com.zimo.starter.ai.plugin.DynamicPluginManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 动态插件管理接口（`/api/ai/plugins`）。
 */
@RestController
@RequestMapping("/api/ai/plugins")
public class AiPluginAdminController {

    private final DynamicPluginManager pluginManager;

    public AiPluginAdminController(DynamicPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    /** 已加载插件列表。 */
    @GetMapping
    public List<DynamicPluginManager.PluginInfo> list() {
        return pluginManager.list();
    }

    /** 上传并装载插件 jar（落到插件目录后热加载）。 */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (name == null || !name.endsWith(".jar")) {
            throw new IllegalArgumentException("仅支持 .jar 插件包");
        }
        Path target = Path.of("data/plugins").toAbsolutePath().normalize().resolve(name);
        Files.createDirectories(target.getParent());
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        DynamicPluginManager.PluginInfo info = pluginManager.loadJar(target);
        return Map.of("installed", name, "loaded", info != null);
    }

    /** 按 jar 名装载插件。 */
    @PostMapping("/load")
    public Map<String, Object> load(@RequestParam String jar) {
        DynamicPluginManager.PluginInfo info = pluginManager.loadJar(
                Path.of("data/plugins").toAbsolutePath().normalize().resolve(jar));
        return Map.of("loaded", info != null, "info", info);
    }

    /** 卸载插件（按 jar 名或插件 id）。 */
    @PostMapping("/unload")
    public Map<String, Object> unload(@RequestParam String key) {
        return Map.of("unloaded", pluginManager.unload(key));
    }
}
