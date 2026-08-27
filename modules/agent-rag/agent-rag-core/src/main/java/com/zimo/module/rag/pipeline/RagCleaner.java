package com.zimo.module.rag.pipeline;

import java.util.regex.Pattern;
import cn.hutool.core.util.StrUtil;

/**
 * 文本清洗器：去除噪音，规范文本。
 *
 * <p>清洗规则：HTML 标签、控制字符、多余空白、重复空行、常见乱码字符，
 * 可选去除 URL 与多余标点。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class RagCleaner {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");
    private static final Pattern MULTI_BLANK_LINE = Pattern.compile("\\n{3,}");
    private static final Pattern GARBAGE = Pattern.compile("[\uFFFD\uFEFF\u00AD]");

    /**
     * 清洗文本。
     *
     * @param text 原始文本
     * @param removeUrls 是否去除 URL
     * @return 清洗后的文本
     */
    public String clean(String text, boolean removeUrls) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String result = text;
        result = CONTROL_CHARS.matcher(result).replaceAll("");
        result = GARBAGE.matcher(result).replaceAll("");
        result = HTML_TAG.matcher(result).replaceAll(" ");
        if (removeUrls) {
            result = result.replaceAll("https?://\\S+", " ");
        }
        result = result.replace('\u3000', ' ');
        result = MULTI_SPACE.matcher(result).replaceAll(" ");
        result = result.replace("\r\n", "\n").replace('\r', '\n');
        result = MULTI_BLANK_LINE.matcher(result).replaceAll("\n\n");
        return result.trim();
    }
}
