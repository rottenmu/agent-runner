package com.zimo.module.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zimo.starter.ai.skill.AiSkill;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 数据转换工具：JSON / CSV / XML / Base64 / 文本规范化。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class TransformTool implements AiSkill {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "transform";
    }

    @Override
    public String description() {
        return "数据转换：op(json2csv/csv2json/json2xml/xml2json/base64_encode/base64_decode/upper/lower/trim/"
                + "pretty) + input(原始数据)。json2csv 将 JSON 数组转 CSV；csv2json 将 CSV 转 JSON；"
                + "json2xml 将 JSON 对象转 XML；xml2json 将 XML 转 JSON；base64 编解码；upper/lower/trim 文本规范化；"
                + "pretty 格式化 JSON。";
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
        String op = str(arguments.get("op"));
        String input = str(arguments.get("input"));
        if (!StringUtils.hasText(op)) {
            return AiSkillResult.fail("请提供操作类型（op）");
        }
        if (!StringUtils.hasText(input)) {
            return AiSkillResult.fail("请提供输入数据（input）");
        }
        try {
            return switch (op) {
                case "json2csv" -> AiSkillResult.ok(json2csv(input));
                case "csv2json" -> AiSkillResult.ok(csv2json(input));
                case "json2xml" -> AiSkillResult.ok(json2xml(input));
                case "xml2json" -> AiSkillResult.ok(xml2json(input));
                case "base64_encode" -> AiSkillResult.ok(Base64.getEncoder()
                        .encodeToString(input.getBytes(StandardCharsets.UTF_8)));
                case "base64_decode" -> AiSkillResult.ok(new String(
                        Base64.getDecoder().decode(input), StandardCharsets.UTF_8));
                case "upper" -> AiSkillResult.ok(input.toUpperCase());
                case "lower" -> AiSkillResult.ok(input.toLowerCase());
                case "trim" -> AiSkillResult.ok(input.trim());
                case "pretty" -> AiSkillResult.ok(objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(objectMapper.readTree(input)));
                default -> AiSkillResult.fail("不支持的操作: " + op);
            };
        } catch (Exception e) {
            return AiSkillResult.fail("转换失败：" + safeMessage(e));
        }
    }

    private String json2csv(String input) throws Exception {
        JsonNode root = objectMapper.readTree(input);
        if (!root.isArray() || root.isEmpty()) {
            throw new IllegalArgumentException("需要非空 JSON 数组");
        }
        ArrayNode array = (ArrayNode) root;
        JsonNode first = array.get(0);
        List<String> headers = new ArrayList<>();
        first.fieldNames().forEachRemaining(headers::add);
        StringBuilder builder = new StringBuilder();
        builder.append(String.join(",", headers)).append("\n");
        for (JsonNode item : array) {
            List<String> cells = new ArrayList<>();
            for (String header : headers) {
                JsonNode value = item.get(header);
                String text = value == null || value.isNull() ? "" : value.asText();
                cells.add(escapeCsv(text));
            }
            builder.append(String.join(",", cells)).append("\n");
        }
        return builder.toString();
    }

    private String csv2json(String input) throws Exception {
        List<String> lines = input.lines().filter(StringUtils::hasText).toList();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CSV 内容为空");
        }
        List<String> headers = splitCsv(lines.get(0));
        ArrayNode array = objectMapper.createArrayNode();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = splitCsv(lines.get(i));
            ObjectNode row = objectMapper.createObjectNode();
            for (int c = 0; c < headers.size(); c++) {
                String value = c < cells.size() ? cells.get(c) : "";
                row.put(headers.get(c), value);
            }
            array.add(row);
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(array);
    }

    private String json2xml(String input) throws Exception {
        JsonNode root = objectMapper.readTree(input);
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        jsonToXml(root, "root", builder);
        return builder.toString();
    }

    private void jsonToXml(JsonNode node, String name, StringBuilder builder) {
        if (node.isObject()) {
            builder.append("<").append(name).append(">");
            node.fields().forEachRemaining(entry ->
                    jsonToXml(entry.getValue(), entry.getKey(), builder));
            builder.append("</").append(name).append(">");
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                jsonToXml(item, name, builder);
            }
        } else {
            builder.append("<").append(name).append(">")
                    .append(escapeXml(node.isNull() ? "" : node.asText()))
                    .append("</").append(name).append(">");
        }
    }

    private String xml2json(String input) throws Exception {
        // 简化 XML → JSON：解析简单标签树
        XmlNode root = parseXml(input);
        ObjectNode json = objectMapper.createObjectNode();
        toJsonNode(root, json);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
    }

    private record XmlNode(String name, String text, List<XmlNode> children) {
    }

    private XmlNode parseXml(String xml) {
        String content = xml.replaceAll("<\\?xml[^>]*\\?>", "").trim();
        return parseXmlNode(content);
    }

    private XmlNode parseXmlNode(String xml) {
        int open = xml.indexOf('<');
        int close = xml.indexOf('>');
        if (open < 0 || close < 0) {
            return new XmlNode("text", xml.trim(), List.of());
        }
        String tag = xml.substring(open + 1, close).trim();
        String rest = xml.substring(close + 1);
        String endTag = "</" + tag.split(" ")[0] + ">";
        int endIndex = rest.indexOf(endTag);
        String inner = endIndex >= 0 ? rest.substring(0, endIndex) : rest;
        List<XmlNode> children = new ArrayList<>();
        if (inner.contains("<")) {
            String remaining = inner;
            while (remaining.contains("<") && remaining.contains(">")) {
                XmlNode child = parseXmlNode(remaining);
                children.add(child);
                int childEnd = remaining.indexOf("</" + child.name() + ">");
                remaining = childEnd >= 0 ? remaining.substring(childEnd + child.name().length() + 3) : "";
            }
        }
        String text = inner.replaceAll("<[^>]+>", "").trim();
        return new XmlNode(tag.split(" ")[0], text, children);
    }

    private void toJsonNode(XmlNode node, ObjectNode parent) {
        if (node.children().isEmpty()) {
            parent.put(node.name(), node.text());
        } else {
            ObjectNode child = parent.putObject(node.name());
            for (XmlNode nested : node.children()) {
                toJsonNode(nested, child);
            }
        }
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

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
