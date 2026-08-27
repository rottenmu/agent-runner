package com.zimo.module.rag.pipeline;

import com.zimo.module.rag.RagProperties;
import java.util.ArrayList;
import cn.hutool.core.util.StrUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 文本切片器：按策略将清洗后的文本切分为检索单元。
 *
 * <p>策略：paragraph=按段落，heading=按 Markdown 标题层级，sentence=按句子，fixed=固定大小+重叠。
 * 表格以独立切片保存（保留表格语义）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class RagChunker {

    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})\\s+(.+)$");
    private static final Pattern SENTENCE = Pattern.compile("(?<=[。！？!?；;])\\s*");

    /**
     * 切片单元。
     *
     * @param content 内容
     * @param tableInfo 关联表格（Markdown），无则为空
     * @param metadata 元数据（标题等）
     */
    public record Chunk(String content, String tableInfo, Map<String, String> metadata) {
    }

    /**
     * 对文档文本与表格执行切片。
     *
     * @param text 清洗后的全文文本
     * @param tables Markdown 表格列表
     * @param properties 切片配置
     * @return 切片列表
     */
    public List<Chunk> chunk(String text, List<String> tables, RagProperties properties) {
        List<Chunk> chunks = new ArrayList<>();
        // 表格独立切片（保留表格语义，问答可直接引用）
        if (tables != null) {
            int tableSeq = 0;
            for (String table : tables) {
                if (StrUtil.isBlank(table)) {
                    continue;
                }
                tableSeq++;
                chunks.add(new Chunk("【表格】请参考以下表格数据回答：\n" + table, table,
                        Map.of("type", "table", "seq", String.valueOf(tableSeq))));
            }
        }
        if (StrUtil.isBlank(text)) {
            return chunks;
        }
        List<String> segments = splitByStrategy(text, properties.getSplitBy());
        for (String segment : segments) {
            String cleaned = segment.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            chunks.addAll(fixedSizeChunks(cleaned, properties));
        }
        return chunks;
    }

    private List<String> splitByStrategy(String text, String strategy) {
        return switch (strategy == null ? "paragraph" : strategy) {
            case "sentence" -> splitSentences(text);
            case "heading" -> splitHeadings(text);
            case "fixed" -> List.of(text);
            default -> splitParagraphs(text);
        };
    }

    /** 按段落（空行/换行）切。 */
    private List<String> splitParagraphs(String text) {
        List<String> segments = new ArrayList<>();
        for (String part : text.split("\n{2,}")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                segments.add(trimmed);
            }
        }
        return segments;
    }

    /** 按句子切。 */
    private List<String> splitSentences(String text) {
        List<String> segments = new ArrayList<>();
        String normalized = text.replace("\n", " ");
        for (String sentence : SENTENCE.split(normalized)) {
            if (!sentence.trim().isEmpty()) {
                segments.add(sentence.trim());
            }
        }
        return segments;
    }

    /** 按 Markdown 标题切（保留标题作为段落头）。 */
    private List<String> splitHeadings(String text) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String lastHeading = "";
        for (String line : text.split("\n")) {
            var matcher = HEADING.matcher(line);
            if (matcher.matches()) {
                if (current.length() > 0) {
                    segments.add(current.toString().trim());
                }
                lastHeading = line.trim();
                current.setLength(0);
                current.append(line).append("\n");
            } else {
                current.append(line).append("\n");
            }
        }
        if (current.toString().trim().length() > 0) {
            segments.add(current.toString().trim());
        }
        return segments;
    }

    /** 固定大小 + 重叠切片（对超长段落二次切分）。 */
    private List<Chunk> fixedSizeChunks(String segment, RagProperties properties) {
        int size = properties.getChunkSize();
        int overlap = Math.min(properties.getOverlap(), size / 2);
        List<Chunk> chunks = new ArrayList<>();
        if (segment.length() <= size) {
            chunks.add(new Chunk(segment, null, Map.of("type", "text")));
            return chunks;
        }
        int start = 0;
        int seq = 0;
        while (start < segment.length()) {
            int end = Math.min(start + size, segment.length());
            if (end < segment.length()) {
                int boundary = findBoundary(segment, start, end);
                end = boundary > start ? boundary : end;
            }
            String part = segment.substring(start, end).trim();
            if (!part.isEmpty()) {
                chunks.add(new Chunk(part, null, Map.of("type", "text", "seq", String.valueOf(seq++))));
            }
            start = end - overlap;
            if (start < 0) {
                start = 0;
            }
            if (end >= segment.length()) {
                break;
            }
        }
        return chunks;
    }

    /** 在 [start, end] 内找最近的行边界，避免切断句子。 */
    private int findBoundary(String text, int start, int end) {
        for (int i = end; i > Math.max(start, end - 200); i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？') {
                return i + 1;
            }
        }
        return end;
    }
}
