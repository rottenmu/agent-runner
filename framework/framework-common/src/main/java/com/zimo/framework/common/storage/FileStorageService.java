package com.zimo.framework.common.storage;

import java.util.List;

/**
 * 文件存储服务。
 *
 * <p>提供基于嵌入式键值存储（如 RocksDB）的文件存储能力，用于替代 OSS/文件系统。
 * 支持存储、读取、删除、列表和存在性检查操作。该接口位于 framework-common
 * 零 Spring 依赖层，任何模块均可安全引用。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
public interface FileStorageService {

    /**
     * 存储文件数据。
     *
     * @param key  文件唯一键（建议格式："{模块}:{业务类型}:{id}"）
     * @param data 二进制数据
     */
    void store(String key, byte[] data);

    /**
     * 读取文件数据。
     *
     * @param key 文件唯一键
     * @return 二进制数据，不存在时返回 {@code null}
     */
    byte[] get(String key);

    /**
     * 删除文件。
     *
     * @param key 文件唯一键
     * @return 是否成功删除（key 不存在返回 false）
     */
    boolean delete(String key);

    /**
     * 列出指定前缀的所有 key。
     *
     * @param prefix 前缀（可为空，为空时列出全部）
     * @return 匹配的 key 列表
     */
    List<String> list(String prefix);

    /**
     * 检查 key 是否存在。
     *
     * @param key 文件唯一键
     * @return 是否存在
     */
    boolean exists(String key);

    /** 返回存储定位描述（展示用：真实磁盘路径或键说明），默认返回 key。 */
    default String describe(String key) {
        return key;
    }
}