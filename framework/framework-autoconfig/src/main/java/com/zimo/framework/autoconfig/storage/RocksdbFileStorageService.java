package com.zimo.framework.autoconfig.storage;

import com.zimo.framework.common.storage.FileStorageService;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RocksDB 实现的文件存储服务。
 *
 * <p>数据存储在本地目录 {@code data/rocksdb} 下。RocksDB 实例在 Bean 销毁时自动关闭。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
public class RocksdbFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(RocksdbFileStorageService.class);

    private final RocksDB db;
    private final Options options;
    private final String dbPath;

    /**
     * 创建 RocksDB 文件存储服务。
     *
     * @param dbPath 数据存储路径
     * @throws IOException RocksDB 初始化失败时抛出
     */
    public RocksdbFileStorageService(String dbPath) throws IOException {
        this.dbPath = Path.of(dbPath).toAbsolutePath().normalize().toString();
        RocksDB.loadLibrary();
        // Blob 文件（KV 分离）：大值写 blob 文件减少写放大，开启 GC 回收删除/更新空间
        this.options = new Options()
                .setCreateIfMissing(true)
                .setEnableBlobFiles(true)
                .setMinBlobSize(256 * 1024L)
                .setBlobFileSize(128 * 1024 * 1024L)
                .setBlobCompressionType(org.rocksdb.CompressionType.ZSTD_COMPRESSION)
                .setEnableBlobGarbageCollection(true)
                .setBlobGarbageCollectionAgeCutoff(0.5);
        File dir = new File(dbPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建 RocksDB 数据目录: " + dbPath);
        }
        try {
            this.db = RocksDB.open(options, dbPath);
            log.info("RocksDB 文件存储已启动，路径: {}", Path.of(dbPath).toAbsolutePath());
        } catch (RocksDBException e) {
            throw new IOException("RocksDB 初始化失败", e);
        }
    }

    @Override
    public void store(String key, byte[] data) {
        try {
            db.put(key.getBytes(StandardCharsets.UTF_8), data);
        } catch (RocksDBException e) {
            throw new RuntimeException("RocksDB 存储失败: " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return db.get(key.getBytes(StandardCharsets.UTF_8));
        } catch (RocksDBException e) {
            throw new RuntimeException("RocksDB 读取失败: " + key, e);
        }
    }

    @Override
    public boolean delete(String key) {
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] existing = db.get(keyBytes);
            if (existing == null) {
                return false;
            }
            db.delete(keyBytes);
            return true;
        } catch (RocksDBException e) {
            throw new RuntimeException("RocksDB 删除失败: " + key, e);
        }
    }

    @Override
    public List<String> list(String prefix) {
        List<String> result = new ArrayList<>();
        byte[] prefixBytes = prefix != null ? prefix.getBytes(StandardCharsets.UTF_8) : null;
        try (RocksIterator it = db.newIterator()) {
            if (prefixBytes != null && prefixBytes.length > 0) {
                it.seek(prefixBytes);
            } else {
                it.seekToFirst();
            }
            while (it.isValid()) {
                String key = new String(it.key(), StandardCharsets.UTF_8);
                if (prefixBytes != null && prefixBytes.length > 0 && !key.startsWith(prefix)) {
                    break;
                }
                result.add(key);
                it.next();
            }
        }
        return result;
    }

    @Override
    public boolean exists(String key) {
        return get(key) != null;
    }

    /**
     * 关闭 RocksDB 实例，释放资源。
     */
    public void close() {
        log.info("RocksDB 文件存储正在关闭...");
        db.close();
        options.close();
        log.info("RocksDB 文件存储已关闭");
    }

    /** 存储定位描述：RocksDB 数据目录 + 键（展示给用户定位真实落盘位置）。 */
    @Override
    public String describe(String key) {
        return dbPath + "（RocksDB 键：" + key + "）";
    }
}
