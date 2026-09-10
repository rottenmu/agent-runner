package com.zimo.module.tools.tool;

import com.zimo.framework.ai.skill.AiSkill;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.ai.skill.AiSkillResult;
import com.zimo.module.tools.ToolsProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * 文件处理工具：读 / 写 / 追加 / 列表 / 删除 / 统计。
 *
 * <p>所有操作限制在配置的工作目录（{@code ai.tools.file-workspace}）内，防止越权访问。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class FileTool implements AiSkill {

    private final ToolsProperties properties;

    public FileTool(ToolsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String name() {
        return "file";
    }

    @Override
    public String description() {
        return "文件处理：在工作目录内读写文件。参数：action(read/write/append/list/delete/stat，必填)、"
                + "path(相对工作区的文件路径，必填)、content(写/追加时内容)。"
                + "read 返回文件内容，list 返回目录列表，stat 返回文件信息。";
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        if (arguments == null) {
            return AiSkillResult.fail("缺少参数");
        }
        String action = str(arguments.get("action"));
        String path = str(arguments.get("path"));
        if (!StringUtils.hasText(action) || !StringUtils.hasText(path)) {
            return AiSkillResult.fail("请提供 action 与 path");
        }
        try {
            Path workspace = workspace();
            Files.createDirectories(workspace);
            Path target = resolve(workspace, path);
            return switch (action) {
                case "read" -> read(target);
                case "write" -> write(target, str(arguments.get("content")));
                case "append" -> append(target, str(arguments.get("content")));
                case "list" -> list(target);
                case "delete" -> delete(target);
                case "stat" -> stat(target);
                default -> AiSkillResult.fail("不支持的操作: " + action + "（支持 read/write/append/list/delete/stat）");
            };
        } catch (Exception e) {
            return AiSkillResult.fail("文件操作失败：" + safeMessage(e));
        }
    }

    private AiSkillResult read(Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return AiSkillResult.fail("文件不存在: " + target);
        }
        String content = Files.readString(target, StandardCharsets.UTF_8);
        return AiSkillResult.ok(content.length() > 12000 ? content.substring(0, 12000) + "\n...(已截断)" : content);
    }

    private AiSkillResult write(Path target, String content) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
        return AiSkillResult.ok("已写入 " + target + "（" + (content == null ? 0 : content.length()) + " 字符）");
    }

    private AiSkillResult append(Path target, String content) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        return AiSkillResult.ok("已追加 " + target);
    }

    private AiSkillResult list(Path target) throws IOException {
        if (!Files.isDirectory(target)) {
            return AiSkillResult.fail("目录不存在: " + target);
        }
        StringBuilder builder = new StringBuilder();
        try (var stream = Files.list(target)) {
            stream.sorted().forEach(p -> {
                try {
                    String size = Files.isDirectory(p) ? "<dir>" : String.valueOf(Files.size(p)) + "B";
                    builder.append(p.getFileName()).append("\t").append(size).append("\n");
                } catch (IOException ignored) {
                }
            });
        }
        return AiSkillResult.ok(builder.length() == 0 ? "（空目录）" : builder.toString());
    }

    private AiSkillResult delete(Path target) throws IOException {
        if (!Files.exists(target)) {
            return AiSkillResult.fail("不存在: " + target);
        }
        Files.deleteIfExists(target);
        return AiSkillResult.ok("已删除 " + target);
    }

    private AiSkillResult stat(Path target) throws IOException {
        if (!Files.exists(target)) {
            return AiSkillResult.fail("不存在: " + target);
        }
        return AiSkillResult.ok("path=" + target
                + "\ntype=" + (Files.isDirectory(target) ? "dir" : "file")
                + "\nsize=" + (Files.isDirectory(target) ? "-" : Files.size(target) + " bytes")
                + "\nlastModified=" + Files.getLastModifiedTime(target));
    }

    private Path workspace() {
        String configured = properties.getFileWorkspace();
        if (!StringUtils.hasText(configured)) {
            configured = "data/tool-files";
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private Path resolve(Path workspace, String path) {
        Path candidate = workspace.resolve(path.replace("\\", "/")).normalize();
        if (!candidate.startsWith(workspace)) {
            throw new SecurityException("路径越界，仅允许访问工作目录: " + workspace);
        }
        return candidate;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
