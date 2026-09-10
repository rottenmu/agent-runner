package com.zimo.framework.ai.interop;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.framework.ai.skill.AiSkillResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AGENTS.md / CLAUDE.md 互操作读取单测（dsh A8）。
 */
class AgentInteropServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversAndParsesAgentsMd() throws IOException {
        write(tempDir.resolve("AGENTS.md"), "# 项目规则\n- 使用 Maven 构建\n- 包名前缀 com.zimo\n");
        AgentInstructionReader reader = new AgentInstructionReader(
                List.of("AGENTS.md", "CLAUDE.md"), tempDir, 3);
        AgentInteropService service = new AgentInteropService(reader);

        assertThat(service.instructionText())
                .contains("【规则文件 AGENTS.md】")
                .contains("- 使用 Maven 构建")
                .contains("- 包名前缀 com.zimo");
        assertThat(service.discovered()).hasSize(1);
    }

    @Test
    void prefersAgentsMdOverClaudeMdWhenBothExist() throws IOException {
        write(tempDir.resolve("AGENTS.md"), "AGENTS 规则");
        write(tempDir.resolve("CLAUDE.md"), "CLAUDE 规则");
        AgentInstructionReader reader = new AgentInstructionReader(
                List.of("AGENTS.md", "CLAUDE.md"), tempDir, 3);

        InteropInstruction first = reader.findFirst();
        assertThat(first).isNotNull();
        assertThat(first.name()).isEqualTo("AGENTS.md");
        assertThat(first.contents()).contains("AGENTS 规则");
    }

    @Test
    void traversesParentDirectoriesUpward() throws IOException {
        Path nested = tempDir.resolve("a").resolve("b");
        Files.createDirectories(nested);
        write(tempDir.resolve("AGENTS.md"), "# 根规则\n- 测试父级发现\n");
        AgentInstructionReader reader = new AgentInstructionReader(
                List.of("AGENTS.md"), nested, 6);

        InteropInstruction found = reader.findFirst();
        assertThat(found).isNotNull();
        assertThat(found.file().toString())
                .isEqualTo(tempDir.resolve("AGENTS.md").toString());
    }

    @Test
    void returnsEmptyWhenNoRuleFileExists() {
        AgentInstructionReader reader = new AgentInstructionReader(
                List.of("AGENTS.md", "CLAUDE.md"), tempDir, 3);
        AgentInteropService service = new AgentInteropService(reader);

        assertThat(service.instructionText()).isEmpty();
        assertThat(service.discovered()).isEmpty();
    }

    @Test
    void trimsToMaxChars() throws IOException {
        write(tempDir.resolve("AGENTS.md"), "A".repeat(1000));
        AgentInstructionReader reader = new AgentInstructionReader(
                List.of("AGENTS.md"), tempDir, 3);
        AgentInteropService service = new AgentInteropService(reader);

        String block = service.instructionBlock(100);
        assertThat(block).hasSizeLessThanOrEqualTo(130);
        assertThat(block).endsWith("…(规则已截断)");
    }

    @Test
    void instructionFileSkillReturnsContentAndFiltersByTopic() throws IOException {
        write(tempDir.resolve("AGENTS.md"), "# 规则\n- Maven 构建\n- 禁止提交 target\n");
        AgentInstructionReader reader = new AgentInstructionReader(
                List.of("AGENTS.md"), tempDir, 3);
        AgentInteropService service = new AgentInteropService(reader);
        InstructionFileSkill skill = new InstructionFileSkill(service, 8000);

        AiSkillResult all = skill.call(Map.of());
        assertThat(all.success()).isTrue();
        assertThat(all.content()).contains("Maven 构建").contains("禁止提交 target");

        AiSkillResult filtered = skill.call(Map.of("topic", "Maven"));
        assertThat(filtered.success()).isTrue();
        assertThat(filtered.content()).contains("Maven 构建").doesNotContain("禁止提交");

        AiSkillResult missing = skill.call(Map.of("topic", "不可能存在的词"));
        assertThat(missing.success()).isTrue();
        assertThat(missing.content()).contains("未找到");
    }

    @Test
    void instructionFileSkillReportsEmptyWhenNoRules() {
        AgentInstructionReader reader = new AgentInstructionReader(
                List.of("AGENTS.md"), tempDir, 3);
        InstructionFileSkill skill = new InstructionFileSkill(
                new AgentInteropService(reader), 8000);

        AiSkillResult result = skill.call(Map.of());
        assertThat(result.success()).isTrue();
        assertThat(result.content()).contains("未发现互操作规则文件");
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}