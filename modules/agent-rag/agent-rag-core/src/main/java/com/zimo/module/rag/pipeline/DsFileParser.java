package com.zimo.module.rag.pipeline;

import java.io.File;
import cn.hutool.core.util.StrUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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

/**
 * 文件解析器：解析 PDF / Word / Excel / 文本文件，输出正文文本与结构化表格。
 *
 * <p>表格识别：Excel 每个 Sheet 转 Markdown 表格；Word 表格转 Markdown 表格；
 * PDF 提取纯文本（复杂表格以文本形式保留）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class DsFileParser {

    /**
     * 解析结果：正文文本 + 表格列表。
     *
     * @param text 全文文本
     * @param tables Markdown 表格列表
     * @param pageCount 页数（仅 PDF 有效）
     */
    public record DocumentContent(String text, List<String> tables, int pageCount) {
    }

    /**
     * 解析文件。
     *
     * @param file 文件
     * @return 解析结果
     * @throws Exception 解析失败
     */
    public DocumentContent parse(File file) throws Exception {
        String lower = file.getName().toLowerCase();
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseExcel(file);
        }
        if (lower.endsWith(".docx")) {
            return parseWord(file);
        }
        if (lower.endsWith(".pdf")) {
            return parsePdf(file);
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")
                || lower.endsWith(".json") || lower.endsWith(".markdown")) {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return new DocumentContent(text, List.of(), 0);
        }
        throw new IllegalArgumentException("不支持的文件类型: " + lower);
    }

    private DocumentContent parseExcel(File file) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(file)) {
            StringBuilder text = new StringBuilder();
            List<String> tables = new ArrayList<>();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                text.append("\n### Sheet: ").append(sheet.getSheetName()).append("\n");
                List<List<String>> grid = new ArrayList<>();
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (int c = 0; c < Math.max(row.getLastCellNum(), 1); c++) {
                        Cell cell = row.getCell(c);
                        cells.add(cell == null ? "" : cell.toString().trim());
                    }
                    grid.add(cells);
                }
                String table = toMarkdownTable(grid);
                tables.add(table);
                text.append(table).append("\n");
            }
            return new DocumentContent(text.toString(), tables, 0);
        }
    }

    private DocumentContent parseWord(File file) throws Exception {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(file.toPath()))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String line = paragraph.getText();
                if (StrUtil.isNotBlank(line)) {
                    text.append(line).append("\n");
                }
            }
            List<String> tables = new ArrayList<>();
            for (XWPFTable table : document.getTables()) {
                List<List<String>> grid = new ArrayList<>();
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        cells.add(cell.getText().trim());
                    }
                    grid.add(cells);
                }
                String markdown = toMarkdownTable(grid);
                tables.add(markdown);
                text.append("\n").append(markdown).append("\n");
            }
            return new DocumentContent(text.toString(), tables, 0);
        }
    }

    private DocumentContent parsePdf(File file) throws Exception {
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return new DocumentContent(text, List.of(), document.getNumberOfPages());
        }
    }

    /** 将二维网格转 Markdown 表格。 */
    private String toMarkdownTable(List<List<String>> grid) {
        if (grid.isEmpty()) {
            return "";
        }
        int columns = grid.stream().mapToInt(List::size).max().orElse(1);
        StringBuilder builder = new StringBuilder();
        for (int r = 0; r < grid.size(); r++) {
            List<String> row = grid.get(r);
            for (int c = 0; c < columns; c++) {
                String cell = c < row.size() ? row.get(c) : "";
                builder.append('|').append(cell.replace('|', '/'));
            }
            builder.append("|\n");
            if (r == 0) {
                builder.append("|".repeat(columns + 1)).append("\n");
            }
        }
        return builder.toString();
    }
}
