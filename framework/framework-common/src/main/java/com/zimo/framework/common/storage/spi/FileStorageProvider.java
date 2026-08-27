package com.zimo.framework.common.storage.spi;

import com.zimo.framework.common.storage.FileStorageService;

/**
 * 文件存储后端 SPI：将文件存储中间件（RocksDB / 本地磁盘 / MinIO / S3 / OSS 等）插拔化。
 *
 * <p>接入新存储中间件只需：</p>
 * <ol>
 *   <li>实现本接口并声明 {@link #engine()}（如 {@code "minio"}），内部实现
 *       {@link FileStorageService} 五个方法（store/get/delete/list/exists）；</li>
 *   <li>将实现注册为 Spring Bean（或加入 Provider 集合）；</li>
 *   <li>配置 {@code framework.storage.engine=minio} 一键切换。</li>
 * </ol>
 * <p>业务模块只依赖 {@link FileStorageService}，与底层存储中间件完全解耦。</p>
 */
public interface FileStorageProvider {

    /** 引擎名（配置 {@code framework.storage.engine} 匹配值）。 */
    String engine();

    /** 创建文件存储服务实例。 */
    FileStorageService create(FileStorageContext context);
}
