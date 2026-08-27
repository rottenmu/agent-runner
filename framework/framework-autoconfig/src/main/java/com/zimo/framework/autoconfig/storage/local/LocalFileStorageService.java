package com.zimo.framework.autoconfig.storage.local;

import com.zimo.framework.common.storage.FileStorageService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地磁盘文件存储（engine = {@code local}）：文件系统中间件的参考实现。
 *
 * <p>key 按路径映射：{@code {root}/{key}}，key 支持目录分隔（{@code a/b/c.txt}）。
 * 可作为接入其他文件存储中间件（MinIO / S3 / OSS）的适配范式：实现
 * {@link FileStorageService} 五方法并接入对应 SDK 即可。</p>
 */
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path root;

    public LocalFileStorageService(String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建本地存储根目录: " + rootPath, e);
        }
        log.info("本地文件存储已启动，根目录: {}", this.root);
    }

    @Override
    public void store(String key, byte[] data) {
        Path target = resolve(key);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, data);
        } catch (IOException e) {
            throw new RuntimeException("本地存储写入失败: " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        Path target = resolve(key);
        try {
            return Files.exists(target) ? Files.readAllBytes(target) : null;
        } catch (IOException e) {
            throw new RuntimeException("本地存储读取失败: " + key, e);
        }
    }

    @Override
    public boolean delete(String key) {
        Path target = resolve(key);
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new RuntimeException("本地存储删除失败: " + key, e);
        }
    }

    @Override
    public List<String> list(String prefix) {
        List<String> keys = new ArrayList<>();
        Path base = resolve(prefix);
        Path start = base.getParent() == null ? root : base.getParent();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(p -> p.toString().replace('\\', '/'))
                    .filter(k -> k.startsWith(prefix))
                    .forEach(keys::add);
        } catch (IOException e) {
            log.warn("本地存储 list 失败: {}", e.getMessage());
        }
        return keys;
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    /** 防路径穿越：key 规范化后必须仍在 root 内。 */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的存储 key（路径穿越）: " + key);
        }
        return target;
    }

    /** 便捷写入（UTF-8 文本）。 */
    public void storeString(String key, String content) {
        store(key, content.getBytes(StandardCharsets.UTF_8));
    }

    /** 存储定位描述：实际文件绝对路径。 */
    @Override
    public String describe(String key) {
        return resolve(key).toString();
    }
}
