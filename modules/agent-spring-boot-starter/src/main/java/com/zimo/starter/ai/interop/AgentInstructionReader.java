package com.zimo.starter.ai.interop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AGENTS.md / CLAUDE.md 互操作规则发现与解析（dsh A8）。
 *
 * <p>从应用工作目录（或显式根目录）向上逐级发现规则文件，优先浅层；
 * 文件名按配置顺序匹配（默认 {@code AGENTS.md},{@code CLAUDE.md}），
 * 仅读取文本文件并截断超长文件。不做递归搜索，避免扫描无关目录。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-24
 */
public class AgentInstructionReader {

    private static final Logger log = LoggerFactory.getLogger(AgentInstructionReader.class);

    /** 单文件最大读取字节数，超出部分忽略（防超大规则文件拖慢注入）。 */
    private static final long MAX_FILE_BYTES = 256 * 1024;

    private final List<String> fileNames;
    private final Path searchRoot;
    private final int maxDepth;

    /**
     * @param fileNames 规则文件名列表（按优先级顺序，null 视为空）
     * @param searchRoot 向上发现的起始目录；为 null 时使用应用工作目录
     * @param maxDepth 向上搜索最大层级（1 = 仅起始目录）；非正数回退默认 {@code 8}
     */
    public AgentInstructionReader(List<String> fileNames, Path searchRoot, int maxDepth) {
        this.fileNames = fileNames == null ? List.of() : List.copyOf(fileNames);
        this.searchRoot = searchRoot == null ? Path.of("").toAbsolutePath() : searchRoot;
        this.maxDepth = maxDepth > 0 ? maxDepth : 8;
    }

    /** 按配置顺序发现首个存在的规则文件（浅层优先）。 */
    public InteropInstruction findFirst() {
        for (String name : fileNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Path candidate = locate(name.trim());
            if (candidate != null) {
                InteropInstruction instruction = parse(candidate);
                if (instruction != null && !instruction.isEmpty()) {
                    return instruction;
                }
            }
        }
        return null;
    }

    /** 发现全部存在的规则文件（同层多文件可用）。 */
    public List<InteropInstruction> findAll() {
        List<InteropInstruction> result = new ArrayList<>();
        for (String name : fileNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Path candidate = locate(name.trim());
            if (candidate != null) {
                InteropInstruction instruction = parse(candidate);
                if (instruction != null && !instruction.isEmpty()) {
                    result.add(instruction);
                }
            }
        }
        return result;
    }

    /** 从 searchRoot 向上定位指定文件；找不到返回 null。 */
    private Path locate(String fileName) {
        Path dir = searchRoot;
        for (int depth = 0; depth < maxDepth; depth++) {
            if (dir == null) {
                break;
            }
            Path candidate = dir.resolve(fileName);
            if (Files.isRegularFile(candidate) && Files.isReadable(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /** 读取规则文件并解析为指令行；不可读/超限/内容为空时返回 null。 */
    private InteropInstruction parse(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
                return null;
            }
            List<String> instructions = Files.readAllLines(file).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
            if (instructions.isEmpty()) {
                return null;
            }
            return new InteropInstruction(file.getFileName().toString(), instructions, file);
        } catch (Exception e) {
            log.debug("互操作规则文件读取失败: {}", file, e);
            return null;
        }
    }
}