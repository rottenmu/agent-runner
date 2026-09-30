package com.zimo.module.ds.connector;

import java.io.File;
import java.net.Socket;
import cn.hutool.core.util.StrUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import net.sourceforge.tess4j.Tesseract;

/**
 * 7 类数据源连接器实现。
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
final class DsConnectors {

    static final int MAX_ROWS = 100;

    private DsConnectors() {
    }

    /* ================= 数据库 ================= */

    static class DatabaseConnector implements DsConnector {

        @Override
        public String type() {
            return "database";
        }

        @Override
        public String test(Map<String, Object> config) {
            String jdbcUrl = str(config.get("jdbcUrl"));
            if (!StringUtils.hasText(jdbcUrl)) {
                return "FAIL: 缺少 jdbcUrl";
            }
            try (Connection connection = connect(config)) {
                return "OK: 数据库连接成功 (" + jdbcUrl + ")";
            } catch (Exception e) {
                return "FAIL: " + safeMessage(e);
            }
        }

        @Override
        public DsDataPreview preview(Map<String, Object> config, Map<String, Object> params) {
            String sql = firstText(params.get("sql"), config.get("sql"));
            if (!StringUtils.hasText(sql)) {
                return DsDataPreview.of(List.of("说明"), List.of(Map.of("说明", "请提供预览 SQL，或配置默认查询语句")), "未提供 SQL");
            }
            sql = sql.trim().replaceAll(";+$", "");
            if (!sql.toLowerCase().startsWith("select")) {
                sql = "SELECT * FROM (" + sql + ") ds_preview LIMIT " + MAX_ROWS;
            } else if (!sql.toLowerCase().contains("limit")) {
                sql = sql + " LIMIT " + MAX_ROWS;
            }
            List<String> columns = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection connection = connect(config);
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet result = statement.executeQuery()) {
                ResultSetMetaData meta = result.getMetaData();
                int count = meta.getColumnCount();
                for (int i = 1; i <= count; i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                int line = 0;
                while (result.next() && line < MAX_ROWS) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= count; i++) {
                        row.put(meta.getColumnLabel(i), result.getObject(i));
                    }
                    rows.add(row);
                    line++;
                }
            } catch (Exception e) {
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", safeMessage(e))), "查询失败");
            }
            return new DsDataPreview(columns, rows, rows.size(), "查询成功");
        }

        protected Connection connect(Map<String, Object> config) throws Exception {
            String jdbcUrl = str(config.get("jdbcUrl"));
            String driver = str(config.get("driver"));
            if (StringUtils.hasText(driver)) {
                Class.forName(driver);
            }
            String username = str(config.get("username"));
            String password = str(config.get("password"));
            return DriverManager.getConnection(jdbcUrl, username, password);
        }
    }

    /* ================= ERP 数据表 ================= */

    static class ErpConnector extends DatabaseConnector {

        @Override
        public String type() {
            return "erp";
        }

        @Override
        public String test(Map<String, Object> config) {
            String erpType = str(config.get("erpType"));
            String base = super.test(config);
            return base.startsWith("OK") && StringUtils.hasText(erpType)
                    ? base + "，ERP 类型=" + erpType
                    : base;
        }
    }

    /* ================= 文档 Excel/Word/PDF ================= */

    static class DocumentConnector implements DsConnector {

        @Override
        public String type() {
            return "document";
        }

        @Override
        public String test(Map<String, Object> config) {
            String path = str(config.get("filePath"));
            if (!StringUtils.hasText(path)) {
                return "FAIL: 缺少 filePath";
            }
            File file = new File(path);
            if (!file.exists()) {
                return "FAIL: 路径不存在 " + path;
            }
            return file.isDirectory()
                    ? "OK: 目录存在，共 " + listFiles(file).size() + " 个文件"
                    : "OK: 文件存在 (" + file.length() + " bytes)";
        }

        @Override
        public DsDataPreview preview(Map<String, Object> config, Map<String, Object> params) {
            String path = firstText(params.get("path"), config.get("filePath"));
            if (!StringUtils.hasText(path)) {
                return DsDataPreview.of(List.of("说明"), List.of(Map.of("说明", "请提供文档路径")), "缺少路径");
            }
            File file = new File(path);
            if (!file.exists()) {
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", "路径不存在: " + path)), "读取失败");
            }
            if (file.isDirectory()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (File child : listFiles(file)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", child.getName());
                    row.put("size", child.length());
                    row.put("type", child.isDirectory() ? "dir" : "file");
                    rows.add(row);
                }
                return new DsDataPreview(List.of("name", "size", "type"), rows, rows.size(), "目录文件列表");
            }
            String lower = file.getName().toLowerCase();
            try {
                if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                    return parseExcel(file);
                }
                if (lower.endsWith(".docx")) {
                    return parseWord(file);
                }
                if (lower.endsWith(".pdf")) {
                    return parsePdf(file);
                }
                if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")) {
                    String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    if (text.length() > 12000) {
                        text = text.substring(0, 12000) + "\n...[截断]";
                    }
                    return DsDataPreview.of(List.of("text"), List.of(Map.of("text", text)), 1, "文本内容");
                }
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", "不支持的文件类型: " + lower)), "解析失败");
            } catch (Exception e) {
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", safeMessage(e))), "解析失败");
            }
        }

        private DsDataPreview parseExcel(File file) throws Exception {
            try (Workbook workbook = WorkbookFactory.create(file)) {
                List<String> columns = new ArrayList<>();
                List<Map<String, Object>> rows = new ArrayList<>();
                List<String> messages = new ArrayList<>();
                int sheets = Math.min(workbook.getNumberOfSheets(), 5);
                for (int s = 0; s < sheets && rows.size() < MAX_ROWS; s++) {
                    Sheet sheet = workbook.getSheetAt(s);
                    messages.add("Sheet: " + sheet.getSheetName());
                    for (Row row : sheet) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        for (int c = 0; c < row.getLastCellNum(); c++) {
                            Cell cell = row.getCell(c);
                            String value = cell == null ? "" : cell.toString();
                            if (columns.size() <= c) {
                                columns.add("col" + (c + 1));
                            }
                            map.put("col" + (c + 1), value);
                        }
                        rows.add(map);
                        if (rows.size() >= MAX_ROWS) {
                            break;
                        }
                    }
                }
                return new DsDataPreview(columns, rows, rows.size(), String.join(" | ", messages));
            }
        }

        private DsDataPreview parseWord(File file) throws Exception {
            List<String> columns = List.of("段落", "表格");
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> messages = new ArrayList<>();
            try (XWPFDocument document = new XWPFDocument(Files.newInputStream(file.toPath()))) {
                StringBuilder text = new StringBuilder();
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    String line = paragraph.getText();
                    if (StringUtils.hasText(line)) {
                        text.append(line).append("\n");
                    }
                }
                messages.add("段落数=" + document.getParagraphs().size());
                messages.add("表格数=" + document.getTables().size());
                if (text.length() > 6000) {
                    text = new StringBuilder(text.substring(0, 6000)).append("\n...[截断]");
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("段落", text.toString());
                List<String> tablesText = new ArrayList<>();
                int tableCount = Math.min(document.getTables().size(), 3);
                for (int t = 0; t < tableCount; t++) {
                    XWPFTable table = document.getTables().get(t);
                    StringBuilder tableText = new StringBuilder();
                    for (XWPFTableRow tableRow : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : tableRow.getTableCells()) {
                            cells.add(cell.getText());
                        }
                        tableText.append(String.join(" | ", cells)).append("\n");
                    }
                    tablesText.add(tableText.toString());
                }
                row.put("表格", String.join("\n---\n", tablesText));
                rows.add(row);
            }
            return new DsDataPreview(columns, rows, rows.size(), String.join(" | ", messages));
        }

        private DsDataPreview parsePdf(File file) throws Exception {
            try (PDDocument document = PDDocument.load(file)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                if (text.length() > 12000) {
                    text = text.substring(0, 12000) + "\n...[截断]";
                }
                return DsDataPreview.of(
                        List.of("text"),
                        List.of(Map.of("text", text)),
                        1,
                        "PDF 共 " + document.getNumberOfPages() + " 页");
            }
        }

        private List<File> listFiles(File dir) {
            File[] files = dir.listFiles();
            if (files == null) {
                return List.of();
            }
            List<File> result = new ArrayList<>();
            for (File file : files) {
                result.add(file);
                if (result.size() >= MAX_ROWS) {
                    break;
                }
            }
            return result;
        }
    }

    /* ================= 扫描件 OCR ================= */

    static class OcrConnector implements DsConnector {

        @Override
        public String type() {
            return "ocr";
        }

        @Override
        public String test(Map<String, Object> config) {
            String path = str(config.get("imagePath"));
            if (!StringUtils.hasText(path)) {
                return "FAIL: 缺少 imagePath";
            }
            File file = new File(path);
            if (!file.exists()) {
                return "FAIL: 路径不存在 " + path;
            }
            return "OK: 扫描件路径可用 (" + (file.isDirectory() ? "目录" : "文件") + ")";
        }

        @Override
        public DsDataPreview preview(Map<String, Object> config, Map<String, Object> params) {
            String path = firstText(params.get("path"), config.get("imagePath"));
            if (!StringUtils.hasText(path)) {
                return DsDataPreview.of(List.of("说明"), List.of(Map.of("说明", "请提供扫描件图片路径")), "缺少路径");
            }
            File file = new File(path);
            if (!file.exists()) {
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", "路径不存在: " + path)), "读取失败");
            }
            String datapath = firstText(params.get("datapath"), config.get("datapath"));
            String language = firstText(params.get("language"), config.get("language"));
            if (StringUtils.hasText(language) && !language.isBlank()) {
                language = language.trim();
            } else {
                language = "chi_sim+eng";
            }
            String missing = missingLanguageFile(datapath, language);
            if (missing != null) {
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误",
                        "未找到 tessdata 语言包: " + missing
                                + "。请下载对应 .traineddata 文件（如 chi_sim.traineddata）并配置 datapath 指向 tessdata 目录")),
                        "OCR 语言包缺失");
            }
            try {
                Tesseract tesseract = new Tesseract();
                if (StringUtils.hasText(datapath)) {
                    tesseract.setDatapath(datapath);
                }
                tesseract.setLanguage(language);
                List<Map<String, Object>> rows = new ArrayList<>();
                List<String> columns = List.of("text");
                List<String> messages = new ArrayList<>();
                if (file.isDirectory()) {
                    File[] images = file.listFiles((dir, name) -> name.matches("(?i).*\\.(png|jpg|jpeg|bmp|tiff|tif)$"));
                    if (images == null || images.length == 0) {
                        return DsDataPreview.of(columns, rows, 0, "目录内无图片");
                    }
                    for (File image : images) {
                        if (rows.size() >= MAX_ROWS) {
                            break;
                        }
                        String text = tesseract.doOCR(image);
                        rows.add(Map.of("text", text));
                        messages.add(image.getName() + " 识别完成");
                    }
                } else {
                    String text = tesseract.doOCR(file);
                    rows.add(Map.of("text", text));
                    messages.add("识别完成，长度=" + text.length());
                }
                return new DsDataPreview(columns, rows, rows.size(), String.join(" | ", messages));
            } catch (Throwable e) {
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误",
                        "OCR 识别失败: " + safeMessage(e))), "OCR 失败");
            }
        }

        /** 检查 tessdata 目录是否存在指定语言文件，缺失返回文件名。 */
        private String missingLanguageFile(String datapath, String language) {
            String[] candidates;
            if (StringUtils.hasText(datapath)) {
                candidates = new String[]{datapath};
            } else {
                String env = System.getenv("TESSDATA_PREFIX");
                candidates = StringUtils.hasText(env)
                        ? new String[]{env, "tessdata", "./tessdata"}
                        : new String[]{"tessdata", "./tessdata"};
            }
            for (String dir : candidates) {
                File tessdir = new File(dir);
                if (!tessdir.isDirectory()) {
                    continue;
                }
                for (String lang : language.split("\\+")) {
                    File trained = new File(tessdir, lang.trim() + ".traineddata");
                    if (!trained.isFile()) {
                        return lang.trim() + ".traineddata（目录 " + tessdir.getAbsolutePath() + "）";
                    }
                }
                return null;
            }
            return "tessdata 目录不存在（已尝试: " + String.join(", ", candidates) + "）";
        }
    }

    /* ================= 网页 ================= */

    static class WebConnector implements DsConnector {

        @Override
        public String type() {
            return "web";
        }

        @Override
        public String test(Map<String, Object> config) {
            String url = str(config.get("url"));
            if (!StringUtils.hasText(url)) {
                return "FAIL: 缺少 url";
            }
            try {
                Document document = Jsoup.connect(url).timeout(8000).get();
                return "OK: 页面可访问，标题=" + document.title();
            } catch (Exception e) {
                return "FAIL: " + safeMessage(e);
            }
        }

        @Override
        public DsDataPreview preview(Map<String, Object> config, Map<String, Object> params) {
            String url = firstText(params.get("url"), config.get("url"));
            if (!StringUtils.hasText(url)) {
                return DsDataPreview.of(List.of("说明"), List.of(Map.of("说明", "请提供网页 URL")), "缺少 URL");
            }
            String selector = firstText(params.get("selector"), config.get("selector"));
            try {
                Document document = Jsoup.connect(url).timeout(10000).get();
                List<Map<String, Object>> rows = new ArrayList<>();
                List<String> messages = new ArrayList<>();
                if (StringUtils.hasText(selector)) {
                    Elements elements = document.select(selector);
                    for (Element element : elements) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("text", element.text());
                        row.put("html", element.outerHtml().length() > 500 ? element.outerHtml().substring(0, 500) : element.outerHtml());
                        rows.add(row);
                        if (rows.size() >= MAX_ROWS) {
                            break;
                        }
                    }
                    messages.add("选择器命中 " + elements.size() + " 个元素");
                } else {
                    List<String> columns = List.of("text");
                    Map<String, Object> row = new LinkedHashMap<>();
                    String text = document.body() == null ? "" : document.body().text();
                    if (text.length() > 12000) {
                        text = text.substring(0, 12000) + "\n...[截断]";
                    }
                    row.put("text", text);
                    rows.add(row);
                    messages.add("标题: " + document.title());
                    messages.add("正文长度: " + text.length());
                }
                List<String> columns = StringUtils.hasText(selector)
                        ? List.of("text", "html")
                        : List.of("text");
                return new DsDataPreview(columns, rows, rows.size(), String.join(" | ", messages));
            } catch (Exception e) {
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", safeMessage(e))), "抓取失败");
            }
        }
    }

    /* ================= 接口数据 ================= */

    static class ApiConnector implements DsConnector {

        private final RestTemplate restTemplate = new RestTemplate();

        @Override
        public String type() {
            return "api";
        }

        @Override
        public String test(Map<String, Object> config) {
            String url = fullUrl(config, "");
            if (!StringUtils.hasText(url)) {
                return "FAIL: 缺少接口地址（baseUrl/path）";
            }
            try {
                String response = restTemplate.getForObject(url, String.class);
                return "OK: 接口可访问，响应长度=" + (response == null ? 0 : response.length());
            } catch (Exception e) {
                return "FAIL: " + safeMessage(e);
            }
        }

        @Override
        public DsDataPreview preview(Map<String, Object> config, Map<String, Object> params) {
            String path = firstText(params.get("path"), config.get("path"));
            String url = fullUrl(config, path);
            if (!StringUtils.hasText(url)) {
                return DsDataPreview.of(List.of("说明"), List.of(Map.of("说明", "请提供接口地址")), "缺少地址");
            }
            try {
                String response = restTemplate.getForObject(url, String.class);
                if (response == null) {
                    return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", "接口无响应")), "调用失败");
                }
                if (response.trim().startsWith("[")) {
                    return parseArray(response);
                }
                if (response.trim().startsWith("{")) {
                    return parseObject(response);
                }
                return DsDataPreview.of(List.of("text"), List.of(Map.of("text", response)), 1, "文本响应");
            } catch (Exception e) {
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", safeMessage(e))), "调用失败");
            }
        }

        private DsDataPreview parseArray(String json) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<Object> list = mapper.readValue(json, List.class);
                List<String> columns = new ArrayList<>();
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object item : list) {
                    if (!(item instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                        String key = String.valueOf(entry.getKey());
                        if (!columns.contains(key)) {
                            columns.add(key);
                        }
                        row.put(key, entry.getValue());
                    }
                    rows.add(row);
                    if (rows.size() >= MAX_ROWS) {
                        break;
                    }
                }
                return new DsDataPreview(columns, rows, rows.size(), "JSON 数组，共 " + list.size() + " 条");
            } catch (Exception e) {
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", safeMessage(e))), "解析失败");
            }
        }

        private DsDataPreview parseObject(String json) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> map = mapper.readValue(json, Map.class);
                List<Map<String, Object>> rows = new ArrayList<>();
                rows.add(map);
                return new DsDataPreview(new ArrayList<>(map.keySet()), rows, 1, "JSON 对象");
            } catch (Exception e) {
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", safeMessage(e))), "解析失败");
            }
        }

        private String fullUrl(Map<String, Object> config, String path) {
            String base = str(config.get("baseUrl"));
            String p = StringUtils.hasText(path) ? path : str(config.get("path"));
            if (!StringUtils.hasText(base) && !StringUtils.hasText(p)) {
                return "";
            }
            String url = StringUtils.hasText(base) ? base : p;
            if (StringUtils.hasText(p) && StringUtils.hasText(base)) {
                url = base.endsWith("/") ? base + p : base + "/" + p;
            }
            return url;
        }
    }

    /* ================= 文件服务器 ================= */

    static class FileServerConnector implements DsConnector {

        @Override
        public String type() {
            return "file_server";
        }

        @Override
        public String test(Map<String, Object> config) {
            String protocol = str(config.get("protocol"));
            if ("ftp".equalsIgnoreCase(protocol)) {
                return ftpTest(config);
            }
            String root = str(config.get("rootPath"));
            if (!StringUtils.hasText(root)) {
                return "FAIL: 缺少 rootPath";
            }
            File dir = new File(root);
            return dir.isDirectory() ? "OK: 目录可访问 " + root : "FAIL: 目录不存在 " + root;
        }

        @Override
        public DsDataPreview preview(Map<String, Object> config, Map<String, Object> params) {
            String path = firstText(params.get("path"), params.get("directory"));
            String root = firstText(params.get("rootPath"), config.get("rootPath"));
            String target = StringUtils.hasText(path) ? path : root;
            if (!StringUtils.hasText(target)) {
                return DsDataPreview.of(List.of("说明"), List.of(Map.of("说明", "请提供目录路径")), "缺少路径");
            }
            if ("ftp".equalsIgnoreCase(str(config.get("protocol")))) {
                return ftpPreview(config, target);
            }            File dir = new File(target);
            if (!dir.isDirectory()) {
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", "目录不存在: " + target)), "读取失败");
            }
            File[] files = dir.listFiles();
            List<Map<String, Object>> rows = new ArrayList<>();
            if (files != null) {
                for (File file : files) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", file.getName());
                    row.put("size", file.length());
                    row.put("type", file.isDirectory() ? "dir" : "file");
                    rows.add(row);
                    if (rows.size() >= MAX_ROWS) {
                        break;
                    }
                }
            }
            return new DsDataPreview(List.of("name", "size", "type"), rows, rows.size(), "目录 " + target);
        }
    }

    /* ================= 细粒度数据库：MySQL / MariaDB / PostgreSQL ================= */

    /**
     * JDBC 预设连接器：在泛化 DatabaseConnector 之上按数据库品种
     * 预置 URL 前缀与驱动类，允许只填 host/port/database 而不手写 jdbcUrl。
     */
    abstract static class JdbcPresetConnector extends DatabaseConnector {

        private final String label;
        private final String urlPrefix;
        private final String driverClass;
        private final int defaultPort;

        JdbcPresetConnector(String label, String urlPrefix, String driverClass, int defaultPort) {
            this.label = label;
            this.urlPrefix = urlPrefix;
            this.driverClass = driverClass;
            this.defaultPort = defaultPort;
        }

        @Override
        protected Connection connect(Map<String, Object> config) throws Exception {
            Map<String, Object> merged = withJdbcUrl(config);
            if (merged == null) {
                throw new IllegalArgumentException("缺少 jdbcUrl 或 host");
            }
            return super.connect(merged);
        }

        @Override
        public String test(Map<String, Object> config) {
            Map<String, Object> merged = withJdbcUrl(config);
            if (merged == null) {
                return "FAIL: 缺少 jdbcUrl 或 host";
            }
            return withLabel(super.test(merged));
        }

        /** 未手填 jdbcUrl 时按 host/port/database 预构建；两者都没有返回 null。 */
        private Map<String, Object> withJdbcUrl(Map<String, Object> config) {
            if (StringUtils.hasText(str(config.get("jdbcUrl")))) {
                return config;
            }
            String host = str(config.get("host"));
            if (!StringUtils.hasText(host)) {
                return null;
            }
            String database = str(config.get("database"));
            int port = intVal(config, "port", defaultPort);
            Map<String, Object> merged = new LinkedHashMap<>(config);
            merged.put("jdbcUrl", buildJdbcUrl(host, port, database));
            if (!StringUtils.hasText(str(config.get("driver")))) {
                merged.put("driver", driverClass);
            }
            return merged;
        }

        String buildJdbcUrl(String host, int port, String database) {
            return urlPrefix + host + ":" + port + "/" + database;
        }

        /** 把泛化消息包装成带品种标签的测试结果（OK: 数据库连接成功 → OK: MySQL 连接成功 …）。 */
        String withLabel(String baseResult) {
            if (baseResult != null && baseResult.startsWith("OK")) {
                return "OK: " + label + " " + baseResult.substring("OK: 数据库".length());
            }
            return baseResult;
        }
    }

    static class MysqlConnector extends JdbcPresetConnector {

        MysqlConnector() {
            super("MySQL", "jdbc:mysql://", "com.mysql.cj.jdbc.Driver", 3306);
        }

        @Override
        public String type() {
            return "mysql";
        }

        @Override
        String buildJdbcUrl(String host, int port, String database) {
            return super.buildJdbcUrl(host, port, database)
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        }
    }

    static class MariadbConnector extends JdbcPresetConnector {

        MariadbConnector() {
            super("MariaDB", "jdbc:mariadb://", "org.mariadb.jdbc.Driver", 3306);
        }

        @Override
        public String type() {
            return "mariadb";
        }
    }

    static class PostgreSqlConnector extends JdbcPresetConnector {

        PostgreSqlConnector() {
            super("PostgreSQL", "jdbc:postgresql://", "org.postgresql.Driver", 5432);
        }

        @Override
        public String type() {
            return "postgresql";
        }
    }

    /* ================= Redis ================= */

    /** Redis 连接器：零依赖，直接说 RESP 协议（AUTH → SELECT → PING/GET/DBSIZE）。 */
    static class RedisConnector implements DsConnector {

        @Override
        public String type() {
            return "redis";
        }

        @Override
        public String test(Map<String, Object> config) {
            try (Socket socket = connect(config)) {
                String pong = resp(send(socket, "PING"));
                return "PONG".equals(pong)
                        ? "OK: Redis 连接成功 " + str(config.get("host")) + ":" + intVal(config, "port", 6379)
                        : "FAIL: PING 响应异常: " + pong;
            } catch (Exception e) {
                return "FAIL: " + safeMessage(e);
            }
        }

        @Override
        public DsDataPreview preview(Map<String, Object> config, Map<String, Object> params) {
            String key = firstText(params == null ? null : params.get("key"), config.get("key"));
            try (Socket socket = connect(config)) {
                List<Map<String, Object>> rows = new ArrayList<>();
                List<String> columns = new ArrayList<>();
                List<String> messages = new ArrayList<>();
                if (StringUtils.hasText(key)) {
                    String value = resp(send(socket, "GET", key));
                    columns.add("key");
                    columns.add("value");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("key", key);
                    row.put("value", value);
                    rows.add(row);
                    messages.add("GET " + key);
                } else {
                    String dbsize = resp(send(socket, "DBSIZE"));
                    columns.add("dbsize");
                    rows.add(Map.of("dbsize", dbsize));
                    messages.add("DBSIZE=" + dbsize + "（用 params.key 可预览单个键）");
                }
                return new DsDataPreview(columns, rows, rows.size(), String.join(" | ", messages));
            } catch (Exception e) {
                return DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", safeMessage(e))), "Redis 查询失败");
            }
        }

        private Socket connect(Map<String, Object> config) throws Exception {
            String host = str(config.get("host"));
            if (!StringUtils.hasText(host)) {
                throw new IllegalArgumentException("缺少 host");
            }
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(host, intVal(config, "port", 6379)), 5000);
            socket.setSoTimeout(5000);
            String password = str(config.get("password"));
            if (StringUtils.hasText(password)) {
                String auth = resp(send(socket, "AUTH", password));
                if (auth == null || !auth.startsWith("OK")) {
                    throw new IllegalStateException("AUTH 失败: " + auth);
                }
            }
            String db = str(config.get("db"));
            if (StringUtils.hasText(db) && !"0".equals(db)) {
                String select = resp(send(socket, "SELECT", db));
                if (select == null || !select.startsWith("OK")) {
                    throw new IllegalStateException("SELECT db 失败: " + select);
                }
            }
            return socket;
        }

        /** 发送 RESP 数组命令并返回原始输入流（供 resp() 解析）。 */
        private java.io.InputStream send(Socket socket, String... args) throws Exception {
            StringBuilder cmd = new StringBuilder("*").append(args.length).append("\r\n");
            for (String arg : args) {
                cmd.append("$").append(arg.getBytes(StandardCharsets.UTF_8).length).append("\r\n")
                        .append(arg).append("\r\n");
            }
            java.io.OutputStream out = socket.getOutputStream();
            out.write(cmd.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            return socket.getInputStream();
        }

        /** 解析单条 RESP 回复：+/○/-/:/$ 五类前缀，只取首个回复。 */
        private String resp(java.io.InputStream in) throws Exception {
            int prefix = in.read();
            if (prefix == -1) {
                return null;
            }
            String line = readLine(in);
            char type = (char) prefix;
            return switch (type) {
                case '+', '-', ':' -> line;
                case '$' -> {
                    int len = Integer.parseInt(line.trim());
                    if (len < 0) {
                        yield null; // RESP 空批量（键不存在）
                    }
                    byte[] buf = in.readNBytes(len);
                    readLine(in); // 消费结尾 CRLF
                    yield new String(buf, StandardCharsets.UTF_8);
                }
                default -> line;
            };
        }

        private String readLine(java.io.InputStream in) throws Exception {
            StringBuilder sb = new StringBuilder();
            int prev = -1;
            int b;
            while ((b = in.read()) != -1) {
                if (prev == '\r' && b == '\n') {
                    break;
                }
                if (prev != -1) {
                    sb.append((char) prev);
                }
                prev = b;
            }
            return sb.toString();
        }
    }

    /* ================= MinIO / 阿里云 OSS（对象存储） ================= */

    /** MinIO 连接器：健康检查走未鉴权 /minio/health/live（对象列举需签名，预览给引导说明）。 */
    static class MinioConnector implements DsConnector {

        @Override
        public String type() {
            return "minio";
        }

        @Override
        public String test(Map<String, Object> config) {
            String endpoint = str(config.get("endpoint"));
            if (!StringUtils.hasText(endpoint)) {
                return "FAIL: 缺少 endpoint（如 http://127.0.0.1:9000）";
            }
            try {
                int code = httpProbe(endpoint.endsWith("/") ? endpoint + "minio/health/live" : endpoint + "/minio/health/live");
                return code >= 200 && code < 300
                        ? "OK: MinIO 存活 " + endpoint
                        : "FAIL: MinIO 健康检查返回 HTTP " + code;
            } catch (Exception e) {
                return "FAIL: " + safeMessage(e);
            }
        }

        @Override
        public DsDataPreview preview(Map<String, Object> config, Map<String, Object> params) {
            return DsDataPreview.of(List.of("说明"), List.of(Map.of("说明",
                    "MinIO 对象列举需要 AWS SigV4 签名，请通过「测试连接」验证存活性，"
                            + "或用 api 类型配合预签名 URL 浏览对象")), "对象预览需签名");
        }
    }

    /** 阿里云 OSS 连接器：以 bucket 端点 HTTP 可达性做存活性判断。 */
    static class OssConnector implements DsConnector {

        @Override
        public String type() {
            return "oss";
        }

        @Override
        public String test(Map<String, Object> config) {
            String endpoint = str(config.get("endpoint"));
            String bucket = str(config.get("bucket"));
            if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(bucket)) {
                return "FAIL: 缺少 endpoint 或 bucket";
            }
            String url = "https://" + bucket + "." + endpoint + "/";
            try {
                int code = httpProbe(url);
                // 未签名请求正常会拿到 403/404 —— 只要端点应答即视为可达
                return code < 500
                        ? "OK: OSS 端点可达 " + url + "（HTTP " + code + "）"
                        : "FAIL: OSS 端点异常 HTTP " + code;
            } catch (Exception e) {
                return "FAIL: " + safeMessage(e);
            }
        }

        @Override
        public DsDataPreview preview(Map<String, Object> config, Map<String, Object> params) {
            return DsDataPreview.of(List.of("说明"), List.of(Map.of("说明",
                    "OSS 对象列举需要签名，请通过「测试连接」验证 bucket 可达性，"
                            + "或用 api 类型配合预签名 URL 浏览对象")), "对象预览需签名");
        }
    }

    /* ================= FTP（独立类型，复用文件服务器的 FTP 逻辑） ================= */

    static class FtpConnector implements DsConnector {

        @Override
        public String type() {
            return "ftp";
        }

        @Override
        public String test(Map<String, Object> config) {
            return ftpTest(config);
        }

        @Override
        public DsDataPreview preview(Map<String, Object> config, Map<String, Object> params) {
            String target = firstText(params == null ? null : params.get("path"),
                    firstText(params == null ? null : params.get("directory"), config.get("rootPath")));
            if (!StringUtils.hasText(target)) {
                return DsDataPreview.of(List.of("说明"), List.of(Map.of("说明", "请提供 rootPath 或 params.path")), "缺少路径");
            }
            return ftpPreview(config, target);
        }
    }

    /* ================= 工具 ================= */

    static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 取整型配置，缺省/非法回落默认值。 */
    static int intVal(Map<String, Object> config, String key, int defaultValue) {
        Object v = config.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return StringUtils.hasText(str(v)) ? Integer.parseInt(str(v)) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** HTTP GET 存活探测：返回状态码（4xx 也算端点应答）；连接/网络异常向上抛。 */
    static int httpProbe(String url) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        conn.disconnect();
        return code;
    }

    /** FTP 连通性测试（file_server protocol=ftp 与独立 ftp 类型共用）。 */
    static String ftpTest(Map<String, Object> config) {
        String host = str(config.get("host"));
        if (!StringUtils.hasText(host)) {
            return "FAIL: FTP 缺少 host";
        }
        FTPClient client = new FTPClient();
        try {
            int port = intVal(config, "port", 21);
            client.connect(host, port);
            client.login(str(config.get("username")), str(config.get("password")));
            return "OK: FTP 连接成功 " + host + ":" + port;
        } catch (Exception e) {
            return "FAIL: " + safeMessage(e);
        } finally {
            try {
                client.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    /** FTP 目录列表预览（file_server protocol=ftp 与独立 ftp 类型共用）。 */
    static DsConnector.DsDataPreview ftpPreview(Map<String, Object> config, String target) {
        FTPClient client = new FTPClient();
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            int port = intVal(config, "port", 21);
            client.connect(str(config.get("host")), port);
            client.login(str(config.get("username")), str(config.get("password")));
            for (org.apache.commons.net.ftp.FTPFile file : client.listFiles(target)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", file.getName());
                row.put("size", file.getSize());
                row.put("type", file.isDirectory() ? "dir" : "file");
                rows.add(row);
                if (rows.size() >= MAX_ROWS) {
                    break;
                }
            }
            return new DsConnector.DsDataPreview(List.of("name", "size", "type"), rows, rows.size(), "FTP 目录 " + target);
        } catch (Exception e) {
            return DsConnector.DsDataPreview.of(List.of("错误"), List.of(Map.of("错误", safeMessage(e))), "FTP 读取失败");
        } finally {
            try {
                client.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    static String firstText(Object first, Object second) {
        return StringUtils.hasText(str(first)) ? str(first) : str(second);
    }

    static String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
