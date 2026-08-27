package com.zimo.starter.ai.interop;

import java.nio.file.Path;
import java.util.List;

/**
 * 互操作规则文件发现结果（dsh A8：AGENTS.md / CLAUDE.md 原生读取）。
 *
 * <p>{@code name} 为文件名（如 {@code AGENTS.md}），{@code instructions} 为解析后的指令行列表，
 * {@code file} 为来源文件路径（便于排查）。注入内容按有序 {@link #contents()} 拼接。</p>
 *
 * @param name         规则文件名
 * @param instructions 指令行（trim 后非空）
 * @param file         来源文件绝对路径（不存在时为空）
 */
public record InteropInstruction(String name, List<String> instructions, Path file) {

    public InteropInstruction {
        instructions = instructions == null ? List.of() : List.copyOf(instructions);
    }

    /** 是否有可注入指令。 */
    public boolean isEmpty() {
        return name == null || name.isBlank() || instructions.isEmpty();
    }

    /** 拼接全部指令（渲染为规则条目）。 */
    public String contents() {
        StringBuilder builder = new StringBuilder();
        for (String line : instructions) {
            if (line == null || line.isBlank()) {
                continue;
            }
            builder.append("- ").append(line.trim()).append('\n');
        }
        return builder.toString();
    }
}