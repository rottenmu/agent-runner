package com.zimo.module.ds.service;

import com.zimo.module.ds.connector.DsConnector;
import com.zimo.module.ds.connector.DsConnectorFactory;
import com.zimo.module.ds.entity.DsDataSource;
import com.zimo.module.ds.mapper.DsDataSourceMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * 数据源管理服务：CRUD、类型目录、连接测试与数据预览。
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class DsDataSourceService {

    private final DsDataSourceMapper mapper;
    private final DsConnectorFactory connectorFactory;
    private final ObjectMapper objectMapper;

    public DsDataSourceService(
            DsDataSourceMapper mapper,
            DsConnectorFactory connectorFactory,
            ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.connectorFactory = Objects.requireNonNull(connectorFactory, "connectorFactory must not be null");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /** 数据源列表。 */
    public List<DsDataSource> list() {
        return mapper.selectList(Wrappers.<DsDataSource>lambdaQuery()
                .orderByDesc(DsDataSource::getId));
    }

    /** 支持的数据源类型目录。 */
    public List<Map<String, String>> types() {
        return connectorFactory.types();
    }

    /** 创建数据源。 */
    public DsDataSource create(String name, String type, String description, String configJson) {
        requireText(name, "数据源名称不能为空");
        requireText(type, "数据源类型不能为空");
        if (connectorFactory.get(type) == null) {
            throw new IllegalArgumentException("不支持的数据源类型: " + type);
        }
        DsDataSource ds = new DsDataSource();
        ds.setName(name.trim());
        ds.setType(type.trim());
        ds.setDescription(description);
        ds.setConfigJson(configJson == null ? "{}" : configJson);
        ds.setEnabled(true);
        LocalDateTime now = LocalDateTime.now();
        ds.setCreatedAt(now);
        ds.setUpdatedAt(now);
        mapper.insert(ds);
        return ds;
    }

    /** 更新数据源。 */
    public DsDataSource update(Long id, String name, String type, String description, String configJson) {
        DsDataSource ds = requireDataSource(id);
        if (StringUtils.hasText(name)) {
            ds.setName(name.trim());
        }
        if (StringUtils.hasText(type)) {
            ds.setType(type.trim());
        }
        if (description != null) {
            ds.setDescription(description);
        }
        if (StringUtils.hasText(configJson)) {
            ds.setConfigJson(configJson);
        }
        ds.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(ds);
        return ds;
    }

    /** 删除数据源（软删除）。 */
    public boolean delete(Long id) {
        return mapper.deleteById(id) > 0;
    }

    /** 测试连接。 */
    public String test(Long id, Map<String, Object> params) {
        DsDataSource ds = requireDataSource(id);
        DsConnector connector = connectorFactory.get(ds.getType());
        if (connector == null) {
            throw new IllegalArgumentException("不支持的数据源类型: " + ds.getType());
        }
        return connector.test(mergeParams(ds, params));
    }

    /** 预览数据。 */
    public DsConnector.DsDataPreview preview(Long id, Map<String, Object> params) {
        DsDataSource ds = requireDataSource(id);
        DsConnector connector = connectorFactory.get(ds.getType());
        if (connector == null) {
            throw new IllegalArgumentException("不支持的数据源类型: " + ds.getType());
        }
        return connector.preview(mergeParams(ds, params), params == null ? Map.of() : params);
    }

    /** 数据源配置与请求参数合并（请求参数优先）。 */
    private Map<String, Object> mergeParams(DsDataSource ds, Map<String, Object> params) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(parseConfig(ds.getConfigJson()));
        if (params != null) {
            merged.putAll(params);
        }
        return merged;
    }

    private Map<String, Object> parseConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(configJson, Map.class);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private DsDataSource requireDataSource(Long id) {
        DsDataSource ds = mapper.selectById(id);
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在: id=" + id);
        }
        return ds;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
