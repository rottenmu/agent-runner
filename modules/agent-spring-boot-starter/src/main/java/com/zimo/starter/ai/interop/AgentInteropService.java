package com.zimo.starter.ai.interop;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 互操作规则注入服务：把发现的规则文件渲染为可注入提示词片段（dsh A8）。
 *
 * <p>渲染格式（注入在系统提示词末尾）：
 * <blockquote><pre>
 * &lt;start of instruction file 内容&gt;
 * ...
 * </pre></blockquote>
 * 支持两种消费方式：直接返回片段文本（{@link #instructionBlock(int)}），或
 * 由调用方按技能绑定的规则集组装。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-24
 */
public class AgentInteropService {

    private final AgentInstructionReader reader;

    public AgentInteropService(AgentInstructionReader reader) {
        this.reader = reader == null ? new AgentInstructionReader(List.of("AGENTS.md", "CLAUDE.md"), null, 8)
                : reader;
    }

    /** 发现全部规则并渲染为提示词片段；无规则时返回空串。 */
    public String instructionText() {
        return instructionBlock(0);
    }

    /** 渲染规则片段，截断至 maxChars。 */
    public String instructionBlock(int maxChars) {
        List<InteropInstruction> instructions = reader.findAll();
        if (instructions.isEmpty()) {
            return "";
        }
        String block = instructions.stream()
                .filter(i -> !i.isEmpty())
                .map(i -> "【规则文件 " + i.name() + "】\n" + i.contents())
                .collect(Collectors.joining("\n"));
        if (block.isBlank()) {
            return "";
        }
        if (maxChars > 0 && block.length() > maxChars) {
            block = block.substring(0, maxChars) + "\n…(规则已截断)";
        }
        return block;
    }

    /** 当前发现的规则文件（供排查/日志）。 */
    public List<InteropInstruction> discovered() {
        return reader.findAll();
    }
}