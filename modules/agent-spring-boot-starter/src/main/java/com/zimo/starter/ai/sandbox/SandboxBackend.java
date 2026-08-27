package com.zimo.starter.ai.sandbox;

import java.util.List;

/**
 * 进程/命令执行沙箱后端（对应 dsh {@code ctx.sandbox}）：
 * 在启动子进程前包装 argv 并提供实际执行能力（本地直通 / 远程转发 / 容器）。
 *
 * <p>{@link #execute(SandboxCommand)} 是唯一执行入口：本地实现直接起进程，
 * 远程实现将命令序列化转发到远程沙箱服务。消费方（命令类工具）只依赖本接口。</p>
 *
 * <p>默认实现 {@link LocalSandboxBackend} 本地直通；业务可注册自定义后端
 * （如 {@code HttpRemoteSandboxBackend}）替换。</p>
 */
public interface SandboxBackend {

    /** 后端名称（如 local / remote / docker）。 */
    String name();

    /**
     * 包装待执行命令：可改写 argv（注入白名单、环境、包装器），
     * 或抛 {@link SecurityException} 拒绝执行。
     *
     * <p>兼容保留：执行链优先走 {@link #execute(SandboxCommand)}，
     * 该钩子用于命令审计/改写场景。</p>
     *
     * @param argv 原始命令参数（argv[0] 为可执行程序）
     * @return 实际执行的命令参数
     */
    List<String> wrapCommand(List<String> argv);

    /** 检查某命令是否被允许执行。 */
    boolean isAllowed(String executable);

    /**
     * 在沙箱内执行命令。
     *
     * <p>实现约定：返回 {@link SandboxResult} 而非抛异常——执行级错误
     * （IO/远程不可达/超时/策略拒绝）统一放入 {@code error} 字段，
     * 便于调用方与业务逻辑解耦。命令本身非法（如空 argv）可抛
     * {@link IllegalArgumentException}。</p>
     *
     * @param command 待执行命令
     * @return 执行结果（含退出码/输出/超时/错误）
     */
    SandboxResult execute(SandboxCommand command);
}