package com.zimo.module.ds.connector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源连接器 SPI：每种数据源类型提供一个实现，负责测试连接与数据预览。
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public interface DsConnector {

    /**
     * 连接器支持的数据源类型标识。
     *
     * @return database/document/ocr/web/api/file_server/erp
     */
    String type();

    /**
     * 测试连接是否可用。
     *
     * @param config 连接配置（来自数据源 configJson）
     * @return 测试结果消息；成功时以 "OK" 开头
     */
    String test(Map<String, Object> config);

    /**
     * 预览数据。
     *
     * @param config 连接配置
     * @param params 预览参数（如 sql、path、url）
     * @return 结构化预览结果
     */
    DsDataPreview preview(Map<String, Object> config, Map<String, Object> params);

    /**
     * 数据预览结果：列 + 行。
     *
     * @param columns 列名列表
     * @param rows 数据行
     * @param total 总行数（可能大于 rows.size()）
     * @param message 附加说明
     */
    record DsDataPreview(List<String> columns, List<Map<String, Object>> rows, int total, String message) {

        @SuppressWarnings("unchecked")
        public static DsDataPreview of(List<String> columns, List<? extends Map<String, ?>> rows, String message) {
            return new DsDataPreview(columns, normalize(rows), rows == null ? 0 : rows.size(), message);
        }

        @SuppressWarnings("unchecked")
        public static DsDataPreview of(List<String> columns, List<? extends Map<String, ?>> rows, int total, String message) {
            return new DsDataPreview(columns, normalize(rows), total, message);
        }

        @SuppressWarnings("unchecked")
        private static List<Map<String, Object>> normalize(List<? extends Map<String, ?>> rows) {
            List<Map<String, Object>> normalized = new ArrayList<>();
            if (rows != null) {
                for (Map<String, ?> row : rows) {
                    normalized.add(new LinkedHashMap<>((Map<String, Object>) row));
                }
            }
            return normalized;
        }
    }
}
