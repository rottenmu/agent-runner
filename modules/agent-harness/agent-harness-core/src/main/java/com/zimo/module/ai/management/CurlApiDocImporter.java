package com.zimo.module.ai.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * cURL bash 命令解析器（API 管理 · cURL 导入）。
 *
 * <p>将浏览器/调试工具复制的 cURL 命令解析为 {@link AiApiDoc} 接口定义：
 * method（-X/--request 或按 -d 推断）、URL（含 query 参数拆解）、
 * 请求头（-H/--header）、请求体（-d/--data/--data-raw/--json，--json 隐含
 * Content-Type: application/json）。支持一次粘贴多条 cURL（按 curl 命令切分）。</p>
 */
public class CurlApiDocImporter {

    private static final Pattern CURL_START = Pattern.compile("(?m)(?<![A-Za-z0-9_])curl\\b");
    private static final Pattern HEADER_SPLIT = Pattern.compile("^([^:]+):\\s*(.*)$");

    private final ObjectMapper objectMapper;

    public CurlApiDocImporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 解析整段文本（可含多条 cURL）。 */
    public List<AiApiDoc> parse(String curlText, String defaultGroup) {
        List<AiApiDoc> docs = new ArrayList<>();
        if (!StringUtils.hasText(curlText)) {
            return docs;
        }
        String joined = joinContinuedLines(curlText);
        Matcher matcher = CURL_START.matcher(joined);
        List<String> commands = new ArrayList<>();
        int last = -1;
        while (matcher.find()) {
            if (last >= 0) {
                commands.add(joined.substring(last, matcher.start()));
            }
            last = matcher.start();
        }
        if (last >= 0) {
            commands.add(joined.substring(last));
        }
        for (String cmd : commands) {
            AiApiDoc doc = parseOne(cmd, defaultGroup);
            if (doc != null && StringUtils.hasText(doc.getPath())) {
                docs.add(doc);
            }
        }
        return docs;
    }

    /** 解析单条 cURL 命令。 */
    AiApiDoc parseOne(String command, String defaultGroup) {
        List<String> tokens = tokenize(command);
        String method = null;
        String url = null;
        List<String[]> headers = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        boolean jsonBody = false;
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            String lower = t.toLowerCase(Locale.ROOT);
            switch (t) {
                case "-X":
                case "--request":
                    if (i + 1 < tokens.size()) {
                        method = tokens.get(++i).toUpperCase(Locale.ROOT);
                    }
                    break;
                case "-H":
                case "--header":
                    if (i + 1 < tokens.size()) {
                        Matcher m = HEADER_SPLIT.matcher(tokens.get(++i));
                        if (m.matches()) {
                            headers.add(new String[]{m.group(1).trim(), m.group(2).trim()});
                        }
                    }
                    break;
                case "-d":
                case "--data":
                case "--data-raw":
                case "--data-ascii":
                case "--data-binary":
                case "--json":
                    if (i + 1 < tokens.size()) {
                        if (body.length() > 0) {
                            body.append('&');
                        }
                        body.append(tokens.get(++i));
                        if (lower.equals("--json")) {
                            jsonBody = true;
                        }
                    }
                    break;
                case "-u":
                case "--user":
                    if (i + 1 < tokens.size()) {
                        String userpass = tokens.get(++i);
                        headers.add(new String[]{"Authorization",
                                "Basic " + Base64.getEncoder().encodeToString(
                                        userpass.getBytes(StandardCharsets.UTF_8))});
                    }
                    break;
                default:
                    if (t.startsWith("-")) {
                        if (isValueOption(lower) && i + 1 < tokens.size()) {
                            i++;
                        }
                    } else if (url == null && (t.startsWith("http://") || t.startsWith("https://"))) {
                        url = t;
                    }
            }
        }
        if (url == null) {
            for (String t : tokens) {
                if (!t.equals("curl") && !t.startsWith("-")) {
                    url = t;
                    break;
                }
            }
        }
        if (url == null) {
            return null;
        }
        if (method == null) {
            method = body.length() > 0 ? "POST" : "GET";
        }
        if (jsonBody && headers.stream().noneMatch(h -> h[0].equalsIgnoreCase("Content-Type"))) {
            headers.add(new String[]{"Content-Type", "application/json"});
        }
        // 拆分 query 参数
        String path = url;
        Map<String, String> query = new LinkedHashMap<>();
        int q = url.indexOf('?');
        if (q >= 0) {
            path = url.substring(0, q);
            for (String pair : url.substring(q + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    query.put(pair.substring(0, eq), pair.substring(eq + 1));
                } else if (!pair.isEmpty()) {
                    query.put(pair, "");
                }
            }
        }
        AiApiDoc doc = new AiApiDoc();
        doc.setMethod(method);
        doc.setPath(path);
        doc.setName(buildName(method, path));
        doc.setDescription("cURL 导入：" + (url.length() > 100 ? url.substring(0, 100) + "…" : url));
        doc.setGroupName(defaultGroup);
        doc.setRequestHeaders(toJson(headers));
        doc.setRequestParams(buildParams(query, body.toString(), jsonBody));
        doc.setSource("curl");
        doc.setEnabled(true);
        return doc;
    }

    /** 命令切词（支持单/双引号与 " 转义）。 */
    static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                } else {
                    cur.append(c);
                }
            } else if (inDouble) {
                if (c == '\\' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    inDouble = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '\'') {
                inSingle = true;
            } else if (c == '"') {
                inDouble = true;
            } else if (Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    /** bash 反斜杠续行合并。 */
    static String joinContinuedLines(String text) {
        return text.replaceAll("\\\\\\r?\\n", " ");
    }

    /** 名称推导：path 末段 → host → method+path。 */
    static String buildName(String method, String path) {
        String p = path;
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        int slash = p.lastIndexOf('/');
        String last = slash >= 0 ? p.substring(slash + 1) : p;
        if (StringUtils.hasText(last) && !last.contains(".")) {
            return last;
        }
        String rest = p;
        int scheme = rest.indexOf("://");
        if (scheme >= 0) {
            rest = rest.substring(scheme + 3);
        }
        int firstSlash = rest.indexOf('/');
        String host = firstSlash >= 0 ? rest.substring(0, firstSlash) : rest;
        return StringUtils.hasText(host) ? host : (method + " " + p);
    }

    private boolean looksLikeJson(String s) {
        String t = s.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    private String toJson(List<String[]> headers) {
        if (headers.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"name\":\"").append(escape(headers.get(i)[0]))
                    .append("\",\"value\":\"").append(escape(headers.get(i)[1])).append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }

    private String buildParams(Map<String, String> query, String body, boolean jsonBody) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (!query.isEmpty()) {
            params.put("query", query);
        }
        if (StringUtils.hasText(body)) {
            if (jsonBody || looksLikeJson(body)) {
                try {
                    params.put("body", objectMapper.readTree(body));
                } catch (Exception e) {
                    params.put("body", body);
                }
            } else {
                params.put("body", body);
            }
        }
        if (params.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return params.toString();
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean isValueOption(String lower) {
        switch (lower) {
            case "--connect-timeout":
            case "--max-time":
            case "-m":
            case "-o":
            case "--output":
            case "--cookie":
            case "-b":
            case "--referer":
            case "-e":
            case "--user-agent":
            case "-a":
            case "--range":
            case "--retry":
            case "--limit-rate":
                return true;
            default:
                return false;
        }
    }
}
