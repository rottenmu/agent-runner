package com.zimo.module.tools.govern;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.ai.sandbox.SandboxBackend;
import com.zimo.framework.ai.sandbox.SandboxCommand;
import com.zimo.framework.ai.sandbox.SandboxResult;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Python 脚本工具执行器：自定义脚本工具通过本执行器运行。
 *
 * <p>约定：脚本从 stdin 读取 JSON 参数，将结果 JSON 写入 stdout。
 * 安全：禁止导入系统/网络模块（os/subprocess/socket 等），超时强制终止。
 * 执行经 {@link SandboxBackend}（本地直通或远程沙箱）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class PythonScriptExecutor implements ToolExecutor {

    /** 脚本执行超时（秒）。 */
    private static final int TIMEOUT_SECONDS = 10;

    private static final String[] BLOCKED = {
            "os", "subprocess", "socket", "shutil", "sys", "ctypes", "requests",
            "urllib", "importlib", "pathlib", "glob", "shlex", "signal", "threading",
            "multiprocessing", "platform", "getpass", "pickle", "marshal"
    };

    private final String name;
    private final String description;
    private final String script;
    private final String pythonBin;
    private final SandboxBackend sandbox;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PythonScriptExecutor(String name, String description, String script, String pythonBin) {
        this(name, description, script, pythonBin, null);
    }

    public PythonScriptExecutor(String name, String description, String script, String pythonBin,
                                SandboxBackend sandbox) {
        this.name = name;
        this.description = description;
        this.script = script;
        this.pythonBin = StrUtil.isBlank(pythonBin) ? "python3" : pythonBin;
        this.sandbox = sandbox;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        for (String blocked : BLOCKED) {
            if (java.util.regex.Pattern.compile(
                    "(^|\\n)\\s*(import|from)\\s+" + java.util.regex.Pattern.quote(blocked) + "(\\s|\\.|$)",
                    java.util.regex.Pattern.MULTILINE).matcher(script).find()) {
                throw new SecurityException("脚本包含受限模块 import " + blocked);
            }
        }
        String inputJson = objectMapper.writeValueAsString(arguments == null ? Map.of() : arguments);
        SandboxCommand command = new SandboxCommand(
                List.of(pythonBin, "-c", script),
                null, inputJson, TIMEOUT_SECONDS, ".");
        SandboxResult result = executeSandbox(command);
        if (result == null) {
            throw new IllegalStateException("沙箱执行返回空结果");
        }
        if (result.error() != null && !result.error().isBlank()) {
            throw new IllegalStateException("沙箱执行失败: " + result.error());
        }
        if (result.timedOut()) {
            throw new IllegalStateException("脚本执行超时（" + TIMEOUT_SECONDS + "s），已强制终止");
        }
        if (result.exitCode() != 0) {
            throw new IllegalStateException("脚本执行失败（退出码 " + result.exitCode() + "）: "
                    + (StringUtils.hasText(result.stderr()) ? result.stderr().trim() : "无错误输出"));
        }
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(result.stdout())) {
            builder.append(result.stdout().trim());
        }
        if (StringUtils.hasText(result.stderr())) {
            builder.append(builder.length() > 0 ? "\n" : "").append("[stderr] ").append(result.stderr().trim());
        }
        if (builder.length() == 0) {
            builder.append("（脚本无输出）");
        }
        return builder.toString();
    }

    private SandboxResult executeSandbox(SandboxCommand command) {
        try {
            if (sandbox != null) {
                return sandbox.execute(command);
            }
            return new com.zimo.framework.ai.sandbox.LocalSandboxBackend(TIMEOUT_SECONDS).execute(command);
        } catch (Exception e) {
            return SandboxResult.failed(stripStackTrace(e));
        }
    }

    private String stripStackTrace(Exception exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}