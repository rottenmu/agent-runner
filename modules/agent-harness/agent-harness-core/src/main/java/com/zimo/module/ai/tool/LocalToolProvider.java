package com.zimo.module.ai.tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 内置工具后端：以注册函数方式提供<b>确定性</b>工具能力。
 *
 * <p>为什么需要这样一个后端：MCP 与沙箱都依赖外部环境（外部服务可达、机器上装了某条命令），
 * 一旦环境不具备，工具治理层的权限、审计、失败隔离逻辑就没法被稳定验证 ——
 * 只能靠"看起来跑通了"下结论。内置工具让治理链路本身可被确定性测试。</p>
 *
 * <p>内置函数（{@code providerRef} 取值）：{@code echo}（回显 args.text）、
 * {@code upper}（大写 args.text）、{@code sum}（对 args.numbers 求和）、
 * {@code now}（当前毫秒时间戳）。业务可调用 {@link #register} 追加。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class LocalToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalToolProvider.class);

    /** 后端类型标识。 */
    public static final String TYPE = "local";

    /** 已注册函数表：函数名 → 执行逻辑。 */
    private final Map<String, Function<Map<String, Object>, Object>> functions = new ConcurrentHashMap<>();

    /** 构造并注册内置函数。 */
    public LocalToolProvider() {
        register("echo", args -> args.get("text"));
        register("upper", args -> {
            Object text = args.get("text");
            return text == null ? "" : String.valueOf(text).toUpperCase();
        });
        register("sum", LocalToolProvider::sumNumbers);
        register("now", args -> System.currentTimeMillis());
    }

    /**
     * 注册/覆盖一个内置函数。
     *
     * @param name 函数名（与 {@code providerRef} 对应）
     * @param function 执行逻辑；返回 {@code null} 视为空结果
     */
    public void register(String name, Function<Map<String, Object>, Object> function) {
        if (!StringUtils.hasText(name) || function == null) {
            return;
        }
        functions.put(name, function);
        log.debug("[tool] 注册内置工具函数: {}", name);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ToolInvokeResult invoke(ToolMeta meta, Map<String, Object> args) {
        String ref = meta.getProviderRef();
        Function<Map<String, Object>, Object> function = functions.get(ref);
        if (function == null) {
            return ToolInvokeResult.fail("内置工具函数未注册: " + ref);
        }
        try {
            Object value = function.apply(args == null ? Map.of() : args);
            return ToolInvokeResult.ok(value == null ? "" : String.valueOf(value));
        } catch (RuntimeException e) {
            return ToolInvokeResult.fail("内置工具执行失败: " + e.getMessage());
        }
    }

    /** 对 args.numbers 求和；数字缺失按 0 计。 */
    private static Object sumNumbers(Map<String, Object> args) {
        Object numbers = args.get("numbers");
        if (!(numbers instanceof Iterable<?> iterable)) {
            return 0;
        }
        double total = 0;
        for (Object item : iterable) {
            if (item instanceof Number number) {
                total += number.doubleValue();
            }
        }
        return total;
    }
}
