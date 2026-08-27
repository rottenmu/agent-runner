package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.mapper.AiApiDocMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * API 接口定义服务实现。
 *
 * <p>除标准 CRUD 外，提供 YApi 导出 JSON 与 OpenAPI 3.0 文档的解析导入能力。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@Service
public class AiApiDocServiceImpl extends ServiceImpl<AiApiDocMapper, AiApiDoc>
        implements AiApiDocService {

    private final ObjectMapper objectMapper;
    private final CurlApiDocImporter curlImporter;

    public AiApiDocServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.curlImporter = new CurlApiDocImporter(objectMapper);
    }

    @Override
    public List<AiApiDoc> importCurl(String curlText, String groupName) {
        if (!StringUtils.hasText(curlText)) {
            throw new IllegalArgumentException("cURL 内容不能为空");
        }
        List<AiApiDoc> docs = curlImporter.parse(curlText, groupName);
        if (docs.isEmpty()) {
            throw new IllegalArgumentException("未解析到有效的 cURL 命令，请确认以 curl 开头的完整命令");
        }
        saveBatch(docs);
        return docs;
    }

    @Override
    public List<AiApiDoc> importYApi(String json) {
        if (!StringUtils.hasText(json)) {
            throw new IllegalArgumentException("导入内容不能为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage());
        }
        List<AiApiDoc> docs = new ArrayList<>();
        if (root.isArray()) {
            docs.addAll(parseYApiArray(root));
        } else if (root.isObject()) {
            if (root.has("openapi") || root.has("swagger")) {
                docs.addAll(parseOpenApi(root));
            } else {
                JsonNode arr = findArrayField(root);
                if (arr == null) {
                    throw new IllegalArgumentException(
                            "无法识别的导入格式：期望 YApi 接口数组或 OpenAPI 文档");
                }
                docs.addAll(parseYApiArray(arr));
            }
        } else {
            throw new IllegalArgumentException("导入内容格式错误：顶层必须是数组或对象");
        }
        if (docs.isEmpty()) {
            throw new IllegalArgumentException("未从导入内容中解析到任何接口");
        }
        saveBatch(docs);
        return docs;
    }

    /** 解析 YApi 接口列表数组。 */
    private List<AiApiDoc> parseYApiArray(JsonNode arr) {
        List<AiApiDoc> docs = new ArrayList<>();
        for (JsonNode node : arr) {
            if (node == null || !node.isObject()) {
                continue;
            }
            String path = text(node, "path");
            if (!StringUtils.hasText(path)) {
                continue;
            }
            AiApiDoc doc = new AiApiDoc();
            doc.setName(text(node, "title", "name"));
            doc.setMethod(normalizeMethod(text(node, "method")));
            doc.setPath(path);
            doc.setDescription(text(node, "desc", "description"));
            doc.setGroupName(text(node, "catname", "group"));
            doc.setRequestHeaders(jsonOrNull(node.get("req_headers")));
            doc.setRequestParams(buildParams(node));
            doc.setResponseSchema(jsonOrNull(node.get("res_body")));
            doc.setSource("yapi");
            doc.setEnabled(true);
            docs.add(doc);
        }
        return docs;
    }

    /** 合并 YApi 的 req_query 与 req_body_other 为统一参数 JSON。 */
    private String buildParams(JsonNode node) {
        JsonNode query = node.get("req_query");
        JsonNode body = node.get("req_body_other");
        boolean hasQuery = query != null && query.isArray() && query.size() > 0;
        boolean hasBody = body != null && !body.isNull() && !body.isMissingNode();
        if (!hasQuery && !hasBody) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        if (hasQuery) {
            sb.append("\"query\":").append(query.toString());
        }
        if (hasBody) {
            if (hasQuery) {
                sb.append(",");
            }
            String bodyText = body.isValueNode() ? body.asText() : body.toString();
            sb.append("\"body\":").append(bodyText);
        }
        sb.append("}");
        return sb.toString();
    }

    /** 解析 OpenAPI 3.0 / Swagger 文档的 paths。 */
    private List<AiApiDoc> parseOpenApi(JsonNode root) {
        List<AiApiDoc> docs = new ArrayList<>();
        JsonNode info = root.get("info");
        String defaultGroup = info == null ? null : text(info, "title");
        JsonNode paths = root.get("paths");
        if (paths == null || !paths.isObject()) {
            return docs;
        }
        Iterator<Map.Entry<String, JsonNode>> pathIt = paths.fields();
        while (pathIt.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathIt.next();
            String path = pathEntry.getKey();
            JsonNode ops = pathEntry.getValue();
            if (ops == null || !ops.isObject()) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> opIt = ops.fields();
            while (opIt.hasNext()) {
                Map.Entry<String, JsonNode> opEntry = opIt.next();
                String methodKey = opEntry.getKey();
                if (!isHttpMethod(methodKey)) {
                    continue;
                }
                JsonNode op = opEntry.getValue();
                if (op == null || !op.isObject()) {
                    continue;
                }
                AiApiDoc doc = new AiApiDoc();
                doc.setName(text(op, "summary", "operationId", "description"));
                doc.setMethod(methodKey.toUpperCase());
                doc.setPath(path);
                doc.setDescription(text(op, "description"));
                doc.setGroupName(defaultGroup);
                doc.setRequestHeaders(jsonOrNull(op.get("parameters")));
                doc.setRequestParams(jsonOrNull(op.get("requestBody")));
                doc.setResponseSchema(jsonOrNull(op.get("responses")));
                doc.setSource("yapi");
                doc.setEnabled(true);
                docs.add(doc);
            }
        }
        return docs;
    }

    /** 在对象中查找接口数组字段。 */
    private JsonNode findArrayField(JsonNode obj) {
        for (String key : new String[] {
                "interface_list", "apis", "interfaces", "items", "list", "records", "data" }) {
            JsonNode n = obj.get(key);
            if (n != null && n.isArray()) {
                return n;
            }
        }
        Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            JsonNode v = it.next().getValue();
            if (v.isArray() && v.size() > 0) {
                return v;
            }
        }
        return null;
    }

    private String normalizeMethod(String method) {
        if (!StringUtils.hasText(method)) {
            return "GET";
        }
        String upper = method.toUpperCase();
        return isHttpMethod(upper) ? upper : "GET";
    }

    private boolean isHttpMethod(String m) {
        switch (m.toUpperCase()) {
            case "GET":
            case "POST":
            case "PUT":
            case "DELETE":
            case "PATCH":
            case "HEAD":
            case "OPTIONS":
                return true;
            default:
                return false;
        }
    }

    /** 依次取字段文本值（支持多级字段名，如 info/title）。 */
    private String text(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode n = node.get(f);
            if (n != null && n.isValueNode() && StringUtils.hasText(n.asText())) {
                return n.asText();
            }
        }
        return null;
    }

    private String jsonOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isValueNode()) {
            String s = node.asText();
            return StringUtils.hasText(s) ? s : null;
        }
        return node.toString();
    }
}
