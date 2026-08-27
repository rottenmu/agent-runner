package com.zimo.module.tools.tool;

import com.zimo.starter.ai.skill.AiSkill;
import cn.hutool.core.util.StrUtil;
import com.zimo.starter.ai.skill.AiSkillResult;
import com.zimo.starter.ai.sandbox.SandboxBackend;
import com.zimo.starter.ai.sandbox.SandboxCommand;
import com.zimo.starter.ai.sandbox.SandboxResult;
import com.zimo.module.tools.ToolsProperties;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 代码沙箱工具：在受限环境中执行 Python 代码。
 *
 * <p>安全策略：拒绝导入危险模块（os/subprocess/socket 等）、超时强制终止、输出截断。
 * 执行经 {@link SandboxBackend}（本地直通或远程沙箱），远程后端可切换。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class CodeTool implements AiSkill {

    private static final List<String> BLOCKED_IMPORTS = List.of(
            "os", "subprocess", "socket", "shutil", "sys", "ctypes", "requests",
            "urllib", "importlib", "pathlib", "glob", "shlex", "signal", "threading",
            "multiprocessing", "platform", "getpass", "pickle", "marshal", "builtins.open");

    private final ToolsProperties properties;
    private final SandboxBackend sandbox;

    public CodeTool(ToolsProperties properties) {
        this(properties, null);
    }

    public CodeTool(ToolsProperties properties, SandboxBackend sandbox) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.sandbox = sandbox;
    }

    @Override
    public String name() {
        return "code";
    }

    @Override
    public String description() {
        return "代码沙箱：执行 Python 代码（受限环境，禁止系统/网络操作）。参数：code(Python 代码，必填)、"
                + "timeout(秒，默认 10)。支持纯计算/数据处理/算法；结果返回 stdout。";
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
        String code = str(arguments.get("code"));
        if (!StringUtils.hasText(code)) {
            return AiSkillResult.fail("请提供 code");
        }
        String blocked = findBlockedImport(code);
        if (blocked != null) {
            return AiSkillResult.fail("安全策略拒绝：代码包含受限模块 import " + blocked + "（沙箱仅允许纯计算/数据处理）");
        }
        int timeout = arguments.get("timeout") instanceof Number n
                ? Math.min(Math.max(n.intValue(), 1), 30) : properties.getSandboxTimeoutSeconds();
        SandboxResult result = execute(code, timeout);
        if (result == null) {
            return AiSkillResult.fail("沙箱执行返回空结果");
        }
        if (!result.succeeded() && !result.timedOut() && result.error() != null) {
            return AiSkillResult.fail("代码执行失败：" + result.error());
        }
        StringBuilder builder = new StringBuilder();
        builder.append("退出码: ").append(result.exitCode()).append("\n");
        if (StringUtils.hasText(result.stdout())) {
            builder.append("输出:\n").append(truncate(result.stdout()));
        }
        if (StringUtils.hasText(result.stderr())) {
            builder.append("错误:\n").append(truncate(result.stderr()));
        }
        if (result.timedOut()) {
            builder.append("（执行超时，已强制终止）");
        }
        if (!StringUtils.hasText(result.stdout()) && !StringUtils.hasText(result.stderr())) {
            builder.append("（无输出）");
        }
        return AiSkillResult.ok(builder.toString());
    }

    private SandboxResult execute(String code, int timeout) {
        SandboxBackend backend = resolveSandbox();
        SandboxCommand command = new SandboxCommand(
                List.of(properties.getPythonBin(), "-c", code),
                null, null, timeout, ".");
        String sandboxName = backend == null ? "local" : backend.name();
        try {
            return backend == null ? localFallback(command) : backend.execute(command);
        } catch (Exception e) {
            return SandboxResult.failed(sandboxName + " 沙箱执行异常: " + safeMessage(e));
        }
    }

    /** 无沙箱后端注入时退化为直接 ProcessBuilder（保留旧构造行为）。 */
    private SandboxResult localFallback(SandboxCommand command) {
        com.zimo.starter.ai.sandbox.LocalSandboxBackend local =
                new com.zimo.starter.ai.sandbox.LocalSandboxBackend(properties.getSandboxTimeoutSeconds());
        return local.execute(command);
    }

    private SandboxBackend resolveSandbox() {
        return sandbox;
    }

    private String findBlockedImport(String code) {
        for (String blocked : BLOCKED_IMPORTS) {
            String pattern = "(^|\\n)\\s*(import|from)\\s+" + Pattern.quote(blocked) + "(\\s|\\.|$)";
            if (Pattern.compile(pattern, Pattern.MULTILINE).matcher(code).find()) {
                return blocked;
            }
        }
        return null;
    }

    private String truncate(String text) {
        return text.length() > 6000 ? text.substring(0, 6000) + "\n...(已截断)" : text;
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