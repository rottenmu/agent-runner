package com.zimo.module.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.zimo.starter.ai.skill.AiSkill;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 表格运算工具：对 CSV / JSON 数组数据执行聚合、筛选、排序、分组等运算。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class TableTool implements AiSkill {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "table";
    }

    @Override
    public String description() {
        return "表格运算：对表格数据执行运算。参数：data(CSV 字符串或 JSON 数组，必填)、"
                + "op(sum/avg/max/min/count/head/tail/filter/sort/group，必填)、"
                + "column(运算列，聚合/排序/分组需要)、value(filter 条件值，如 >=100)、groupBy(分组列)、"
                + "order(asc/desc，排序)。返回运算结果。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        if (arguments == null) {
            return AiSkillResult.fail("缺少参数");
        }
        String data = str(arguments.get("data"));
        String op = str(arguments.get("op"));
        if (!StringUtils.hasText(data) || !StringUtils.hasText(op)) {
            return AiSkillResult.fail("请提供 data 与 op");
        }
        String column = str(arguments.get("column"));
        String groupBy = str(arguments.get("groupBy"));
        String order = str(arguments.get("order"));
        String value = str(arguments.get("value"));
        try {
            List<Map<String, String>> rows = parseRows(data);
            if (rows.isEmpty()) {
                return AiSkillResult.fail("表格无数据");
            }
            return switch (op) {
                case "sum" -> aggregate(rows, column, "求和");
                case "avg" -> aggregate(rows, column, "平均值");
                case "max" -> aggregate(rows, column, "最大值");
                case "min" -> aggregate(rows, column, "最小值");
                case "count" -> AiSkillResult.ok("行数: " + rows.size());
                case "head" -> AiSkillResult.ok(render(rows.subList(0, Math.min(rows.size(), intOr(arguments.get("n"), 10)))));
                case "tail" -> AiSkillResult.ok(render(rows.subList(Math.max(0, rows.size() - intOr(arguments.get("n"), 10)), rows.size())));
                case "filter" -> AiSkillResult.ok(render(filterRows(rows, column, value)));
                case "sort" -> AiSkillResult.ok(render(sortRows(rows, column, order)));
                case "group" -> group(rows, groupBy, column, op);
                default -> AiSkillResult.fail("不支持的操作: " + op
                        + "（支持 sum/avg/max/min/count/head/tail/filter/sort/group）");
            };
        } catch (Exception e) {
            return AiSkillResult.fail("表格运算失败：" + safeMessage(e));
        }
    }

    private AiSkillResult aggregate(List<Map<String, String>> rows, String column, String label) {
        if (!StringUtils.hasText(column)) {
            return AiSkillResult.fail("聚合需要 column");
        }
        double[] values = rows.stream()
                .mapToDouble(r -> parseDouble(r.get(column)))
                .filter(Double::isFinite)
                .toArray();
        if (values.length == 0) {
            return AiSkillResult.fail("列 '" + column + "' 无有效数值");
        }
        double result = switch (label) {
            case "求和" -> java.util.Arrays.stream(values).sum();
            case "平均值" -> java.util.Arrays.stream(values).average().orElse(0);
            case "最大值" -> java.util.Arrays.stream(values).max().orElse(0);
            default -> java.util.Arrays.stream(values).min().orElse(0);
        };
        return AiSkillResult.ok(label + "(" + column + ") = " + Math.round(result * 10000) / 10000.0
                + "（" + values.length + " 个有效值）");
    }

    private List<Map<String, String>> filterRows(List<Map<String, String>> rows, String column, String value) {
        if (!StringUtils.hasText(column) || !StringUtils.hasText(value)) {
            throw new IllegalArgumentException("filter 需要 column 与 value（如 >=100、=北京）");
        }
        String criteria = value.trim();
        char op = criteria.charAt(0);
        String operand = criteria.substring(1).trim();
        if (op == '>') {
            double threshold = parseDouble(operand);
            return rows.stream()
                    .filter(r -> parseDouble(r.get(column)) > threshold)
                    .toList();
        }
        if (op == '<') {
            double threshold = parseDouble(operand);
            return rows.stream()
                    .filter(r -> parseDouble(r.get(column)) < threshold)
                    .toList();
        }
        String wanted = criteria.startsWith("=") ? operand : criteria;
        return rows.stream()
                .filter(r -> wanted.equals(String.valueOf(r.get(column))))
                .toList();
    }

    private List<Map<String, String>> sortRows(List<Map<String, String>> rows, String column, String order) {
        if (!StringUtils.hasText(column)) {
            throw new IllegalArgumentException("sort 需要 column");
        }
        boolean desc = "desc".equalsIgnoreCase(order);
        Comparator<Map<String, String>> comparator = Comparator.comparingDouble(r -> parseDouble(r.get(column)));
        List<Map<String, String>> result = new ArrayList<>(rows);
        result.sort(desc ? comparator.reversed() : comparator);
        return result;
    }

    private AiSkillResult group(List<Map<String, String>> rows, String groupBy, String column, String op) {
        if (!StringUtils.hasText(groupBy)) {
            return AiSkillResult.fail("group 需要 groupBy");
        }
        Map<String, List<Double>> buckets = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String key = String.valueOf(row.get(groupBy));
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(parseDouble(row.get(column)));
        }
        StringBuilder builder = new StringBuilder();
        builder.append(groupBy).append("\t").append(column).append(" 聚合\n");
        buckets.forEach((key, values) -> {
            double result = switch (op) {
                case "sum" -> values.stream().mapToDouble(Double::doubleValue).sum();
                case "avg" -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                case "max" -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                case "min" -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                default -> values.size();
            };
            builder.append(key).append("\t")
                    .append(Math.round(result * 10000) / 10000.0)
                    .append("（n=").append(values.size()).append("）\n");
        });
        return AiSkillResult.ok(builder.toString());
    }

    private List<Map<String, String>> parseRows(String data) throws Exception {
        String trimmed = data.trim();
        if (trimmed.startsWith("[")) {
            ArrayNode array = (ArrayNode) objectMapper.readTree(trimmed);
            List<Map<String, String>> rows = new ArrayList<>();
            for (JsonNode node : array) {
                Map<String, String> row = new LinkedHashMap<>();
                node.fields().forEachRemaining(entry ->
                        row.put(entry.getKey(), entry.getValue().isNull() ? "" : entry.getValue().asText()));
                rows.add(row);
            }
            return rows;
        }
        List<String> lines = data.lines().filter(StringUtils::hasText).toList();
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> headers = splitCsv(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = splitCsv(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                row.put(headers.get(c), c < cells.size() ? cells.get(c) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private String render(List<Map<String, String>> rows) {
        if (rows.isEmpty()) {
            return "（0 行）";
        }
        List<String> headers = new ArrayList<>(rows.get(0).keySet());
        StringBuilder builder = new StringBuilder();
        builder.append(String.join(" | ", headers)).append("\n");
        for (Map<String, String> row : rows) {
            List<String> cells = new ArrayList<>();
            for (String header : headers) {
                cells.add(String.valueOf(row.get(header)));
            }
            builder.append(String.join(" | ", cells)).append("\n");
        }
        return builder.toString();
    }

    private List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private double parseDouble(String value) {
        if (value == null) {
            return Double.NaN;
        }
        String cleaned = value.replace(",", "").replace("元", "").replace("¥", "").trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private int intOr(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
