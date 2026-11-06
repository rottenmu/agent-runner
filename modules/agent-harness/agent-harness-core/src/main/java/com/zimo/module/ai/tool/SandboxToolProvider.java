package com.zimo.module.ai.tool;

import com.zimo.framework.ai.sandbox.SandboxBackend;
import com.zimo.framework.ai.sandbox.SandboxCommand;
import com.zimo.framework.ai.sandbox.SandboxResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

/**
 * 命令沙箱后端：把调用转交给 {@code framework-ai} 的 {@link SandboxBackend}。
 *
 * <p>参数约定：{@code args.argv}（数组，首元素为可执行程序）优先，其次 {@code args.command}
 * （字符串，按空白切分，仅支持无引号的简单命令），{@code args.timeoutSeconds} 为超时秒数。</p>
 *
 * <p><b>为什么先问 {@code isAllowed} 再执行</b>：沙箱后端的白名单是最后一道闸门，
 * 但它只覆盖"能不能起这个进程"；工具治理层要做的是"这个租户能不能调这个工具"。
 * 两层分开判断，任何一层拒绝都不执行，报错信息才能指向真正的原因。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class SandboxToolProvider implements ToolProvider {

    /** 后端类型标识。 */
    public static final String TYPE = "sandbox";

    private final ObjectProvider<SandboxBackend> backendProvider;

    /**
     * 构造沙箱工具后端。
     *
     * @param backendProvider 沙箱后端提供者，允许运行时缺失
     */
    public SandboxToolProvider(ObjectProvider<SandboxBackend> backendProvider) {
        this.backendProvider = backendProvider;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ToolInvokeResult invoke(ToolMeta meta, Map<String, Object> args) {
        SandboxBackend backend = backendProvider == null ? null : backendProvider.getIfAvailable();
        if (backend == null) {
            return ToolInvokeResult.fail("沙箱后端未装配（framework-ai SandboxBackend 缺失）");
        }
        List<String> argv = resolveArgv(meta, args);
        if (argv.isEmpty()) {
            return ToolInvokeResult.fail("沙箱工具缺少执行命令: " + meta.getToolId());
        }
        if (!backend.isAllowed(argv.get(0))) {
            return ToolInvokeResult.fail("沙箱策略拒绝执行: " + argv.get(0));
        }
        return execute(backend, argv, args);
    }

    /** 执行命令并归一化结果（超时与错误都转成失败原因）。 */
    private ToolInvokeResult execute(SandboxBackend backend, List<String> argv, Map<String, Object> args) {
        try {
            List<String> wrapped = backend.wrapCommand(argv);
            SandboxCommand command = new SandboxCommand(wrapped, null, null, timeoutSeconds(args), null);
            SandboxResult result = backend.execute(command);
            if (result == null) {
                return ToolInvokeResult.fail("沙箱无返回");
            }
            if (result.error() != null) {
                return ToolInvokeResult.fail("沙箱执行错误: " + result.error());
            }
            if (result.timedOut()) {
                return ToolInvokeResult.fail("沙箱执行超时");
            }
            if (result.exitCode() != 0) {
                return ToolInvokeResult.fail("退出码 " + result.exitCode()
                        + "，stderr=" + trim(result.stderr()));
            }
            return ToolInvokeResult.ok(result.stdout());
        } catch (SecurityException e) {
            return ToolInvokeResult.fail("沙箱策略拒绝: " + e.getMessage());
        } catch (RuntimeException e) {
            return ToolInvokeResult.fail("沙箱调用异常: " + e.getMessage());
        }
    }

    /** 解析 argv：优先数组参数，其次字符串命令。 */
    private List<String> resolveArgv(ToolMeta meta, Map<String, Object> args) {
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        List<String> argv = new ArrayList<>();
        Object arrayArg = safeArgs.get("argv");
        if (arrayArg instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    argv.add(String.valueOf(item));
                }
            }
            return argv;
        }
        Object commandArg = safeArgs.get("command");
        String command = commandArg == null ? meta.getProviderRef() : String.valueOf(commandArg);
        if (!StringUtils.hasText(command)) {
            return argv;
        }
        for (String part : command.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                argv.add(part);
            }
        }
        return argv;
    }

    /** 读取超时秒数，缺省 0 表示用后端默认。 */
    private int timeoutSeconds(Map<String, Object> args) {
        Object value = args == null ? null : args.get("timeoutSeconds");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /** 截断过长的输出，避免审计与返回值被大段 stderr 撑爆。 */
    private String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }
}
