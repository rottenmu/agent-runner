package com.zimo.starter.ai.sandbox;

/**
 * 沙箱执行结果（不可变）。
 *
 * <p>无论本地进程还是远程执行，统一以该结构返回，消费方（命令类工具）不感知后端差异。</p>
 *
 * @param exitCode 退出码（0 表示成功）
 * @param stdout 标准输出
 * @param stderr 标准错误
 * @param timedOut 是否超时强制终止
 * @param error 执行级错误信息（IO/网络/违反安全策略，null 表示正常返回）
 */
public record SandboxResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        String error) {

    public static SandboxResult ok(int exitCode, String stdout, String stderr) {
        return new SandboxResult(exitCode, stdout, stderr, false, null);
    }

    public static SandboxResult timeout(String stdout, String stderr) {
        return new SandboxResult(-1, stdout, stderr, true, null);
    }

    public static SandboxResult failed(String error) {
        return new SandboxResult(-2, null, null, false, error);
    }

    public boolean succeeded() {
        return error == null && !timedOut && exitCode == 0;
    }
}