package com.zimo.module.ds.connector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源连接器注册表：按类型返回对应实现。
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class DsConnectorFactory {

    private final Map<String, DsConnector> connectors = new LinkedHashMap<>();

    public DsConnectorFactory() {
        register(new DsConnectors.DatabaseConnector());
        register(new DsConnectors.DocumentConnector());
        register(new DsConnectors.OcrConnector());
        register(new DsConnectors.WebConnector());
        register(new DsConnectors.ApiConnector());
        register(new DsConnectors.FileServerConnector());
        register(new DsConnectors.ErpConnector());
    }

    private void register(DsConnector connector) {
        connectors.put(connector.type(), connector);
    }

    /**
     * 按类型获取连接器。
     *
     * @param type 数据源类型
     * @return 连接器；未知类型返回 {@code null}
     */
    public DsConnector get(String type) {
        return type == null ? null : connectors.get(type);
    }

    /**
     * 全部支持的类型与说明。
     *
     * @return 类型元数据列表
     */
    public List<Map<String, String>> types() {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(typeInfo("database", "数据库", "JDBC 连接，执行 SQL 查询"));
        list.add(typeInfo("document", "Excel/Word/PDF", "解析文档为结构化文本与表格"));
        list.add(typeInfo("ocr", "扫描件 OCR", "图片扫描件文字识别"));
        list.add(typeInfo("web", "网页", "抓取网页正文与表格"));
        list.add(typeInfo("api", "接口数据", "HTTP 接口返回 JSON 转表格"));
        list.add(typeInfo("file_server", "文件服务器", "本地目录 / FTP 文件浏览"));
        list.add(typeInfo("erp", "ERP 数据表", "ERP 业务数据表查询"));
        return list;
    }

    private Map<String, String> typeInfo(String value, String label, String desc) {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("value", value);
        info.put("label", label);
        info.put("description", desc);
        return info;
    }
}
