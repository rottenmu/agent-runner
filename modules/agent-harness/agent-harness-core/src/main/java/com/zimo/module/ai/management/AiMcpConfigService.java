package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * MCP 配置服务。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
public interface AiMcpConfigService extends IService<AiMcpConfig> {

    /**
     * 测试 MCP 连接（轻量 initialize 握手探测）。
     *
     * <p>http/sse：对 endpoint 发 JSON-RPC initialize（带 MCP-Protocol-Version 头，
     * 透传 transportConfig.headers），按响应状态码与 JSON-RPC 回执判断连通；
     * stdio：由 endpoint 拆出「命令 参数...」启动子进程，写 initialize 并读回执（8s 超时）。</p>
     *
     * @param id 配置 ID
     * @return "OK: …" 或 "FAIL: …" 单行摘要（与数据源测试同风格）
     */
    String test(Long id);
}
