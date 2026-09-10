package com.zimo.module.ds.skill;

import com.zimo.framework.ai.skill.AiSkill;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.ai.skill.AiSkillResult;
import com.zimo.module.ds.connector.DsConnector;
import com.zimo.module.ds.entity.DsDataSource;
import com.zimo.module.ds.service.DsDataSourceService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * 数据源查询技能：智能体通过该技能查询已配置的数据源（数据库 / 文档 / OCR / 网页 / 接口 / 文件服务器 / ERP）。
 *
 * <p>参数：{@code datasourceId} 或 {@code datasource}（名称）指定数据源；可按数据源类型传入
 * {@code sql}、{@code path}、{@code url} 等预览参数。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class DsQuerySkill implements AiSkill {

    private final DsDataSourceService service;

    public DsQuerySkill(DsDataSourceService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @Override
    public String name() {
        return "query_datasource";
    }

    @Override
    public String description() {
        return "查询已配置的数据源并返回表格数据。支持数据库(SQL)、Excel/Word/PDF 文档、扫描件 OCR、网页、"
                + "HTTP 接口、文件服务器、ERP 数据表。参数：datasourceId(数据源ID)或 datasource(数据源名称)，"
                + "可选 sql/path/url 等查询参数。回答涉及业务数据前应先调用本工具。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        if (CollUtil.isEmpty(arguments)) {
            return AiSkillResult.fail("请提供数据源标识（datasourceId 或 datasource 名称）");
        }
        DsDataSource source = resolveDataSource(arguments);
        if (source == null) {
            return AiSkillResult.fail("未找到数据源，可用：query_datasource 支持已配置数据源（数据源管理页维护）");
        }
        Map<String, Object> params = new LinkedHashMap<>(arguments);
        params.remove("datasourceId");
        params.remove("datasource");
        try {
            DsConnector.DsDataPreview preview = service.preview(source.getId(), params);
            return AiSkillResult.ok(formatPreview(source.getName(), preview));
        } catch (Exception e) {
            return AiSkillResult.fail("数据源查询失败：" + safeMessage(e));
        }
    }

    private DsDataSource resolveDataSource(Map<String, Object> arguments) {
        Object id = arguments.get("datasourceId");
        if (id != null && StringUtils.hasText(String.valueOf(id))) {
            for (DsDataSource source : service.list()) {
                if (String.valueOf(source.getId()).equals(String.valueOf(id))) {
                    return source;
                }
            }
        }
        Object name = arguments.get("datasource");
        if (name != null && StringUtils.hasText(String.valueOf(name))) {
            String wanted = String.valueOf(name).trim();
            for (DsDataSource source : service.list()) {
                if (source.getName().equalsIgnoreCase(wanted)) {
                    return source;
                }
            }
        }
        return null;
    }

    private String formatPreview(String sourceName, DsConnector.DsDataPreview preview) {
        if (preview == null) {
            return "数据源 [" + sourceName + "] 无返回结果";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("数据源 [").append(sourceName).append("]：").append(preview.message()).append("\n");
        List<String> columns = preview.columns();
        if (columns == null || columns.isEmpty()) {
            return builder.append("（无表格数据）").toString();
        }
        builder.append(String.join(" | ", columns)).append("\n");
        List<Map<String, Object>> rows = preview.rows();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                List<String> cells = columns.stream()
                        .map(col -> String.valueOf(row.getOrDefault(col, "")))
                        .toList();
                builder.append(String.join(" | ", cells)).append("\n");
            }
        }
        if (preview.total() > (rows == null ? 0 : rows.size())) {
            builder.append("（共 ").append(preview.total()).append(" 行，已展示前 ").append(rows.size()).append(" 行）");
        }
        return builder.toString();
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
