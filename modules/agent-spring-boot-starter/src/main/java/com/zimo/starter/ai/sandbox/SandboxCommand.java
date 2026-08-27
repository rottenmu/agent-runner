package com.zimo.starter.ai.sandbox;

import java.util.List;
import java.util.Map;

/**
 * 沙箱执行命令（不可变）。
 *
 * <p>描述一次要在沙箱内执行的进程调用：argv[0] 为可执行程序，
 * env 为附加环境变量（可空），timeoutSeconds 为超时（触发强制终止）。</p>
 *
 * @param argv 命令参数（argv[0] 为可执行程序）
 * @param env 附加环境变量（可空）
 * @param stdin 标准输入内容（可空）
 * @param timeoutSeconds 超时秒数（&lt;=0 用后端默认）
 * @param workdir 工作目录（可空，后端默认）
 */
public record SandboxCommand(
        List<String> argv,
        Map<String, String> env,
        String stdin,
        int timeoutSeconds,
        String workdir) {

    /** 仅命令参数的简构造。 */
    public SandboxCommand(List<String> argv) {
        this(argv, null, null, 0, null);
    }

    public SandboxCommand {
        argv = argv == null ? List.of() : List.copyOf(argv);
    }
}