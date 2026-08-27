package com.zimo.module.ai.controller;

import com.zimo.framework.common.ApiResponse;
import com.zimo.framework.common.validation.ValidationUtil;
import com.zimo.module.ai.management.AiMcpConfig;
import com.zimo.module.ai.management.AiMcpConfigService;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP 配置管理接口。
 *
 * <p>提供 MCP 服务器配置的创建、编辑、删除与查询能力。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@RestController
@RequestMapping("/api/biz/ai/mcp-configs")
public class AiMcpConfigController {

    private final AiMcpConfigService mcpConfigService;

    public AiMcpConfigController(AiMcpConfigService mcpConfigService) {
        this.mcpConfigService = mcpConfigService;
    }

    /**
     * 获取全部 MCP 配置。
     *
     * @return MCP 配置列表
     */
    @GetMapping
    public ApiResponse<List<AiMcpConfig>> list() {
        return ApiResponse.ok(mcpConfigService.list());
    }

    /**
     * 创建 MCP 配置。
     *
     * @param config MCP 配置
     * @return 新建的配置
     */
    @PostMapping
    public ApiResponse<AiMcpConfig> create(@RequestBody AiMcpConfig config) {
        validate(config);
        config.setId(null);
        mcpConfigService.save(config);
        return ApiResponse.ok(config);
    }

    /**
     * 编辑 MCP 配置。
     *
     * @param id 配置 ID
     * @param config MCP 配置
     * @return 更新后的配置
     */
    @PutMapping("/{id}")
    public ApiResponse<AiMcpConfig> update(@PathVariable Long id, @RequestBody AiMcpConfig config) {
        AiMcpConfig existing = mcpConfigService.getById(id);
        ValidationUtil.requireNotNull(existing, "MCP 配置不存在: id=");
        validate(config);
        existing.setName(config.getName());
        existing.setDescription(config.getDescription());
        existing.setMcpType(config.getMcpType());
        existing.setEndpoint(config.getEndpoint());
        existing.setTransportConfig(config.getTransportConfig());
        existing.setEnabled(config.getEnabled());
        mcpConfigService.updateById(existing);
        return ApiResponse.ok(existing);
    }

    /**
     * 删除 MCP 配置。
     *
     * @param id 配置 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (mcpConfigService.getById(id) == null) {
            throw new IllegalArgumentException("MCP 配置不存在: id=" + id);
        }
        mcpConfigService.removeById(id);
        return ApiResponse.ok();
    }

    private void validate(AiMcpConfig config) {
        if (config == null || !StringUtils.hasText(config.getName())) {
            throw new IllegalArgumentException("MCP 名称不能为空");
        }
        String type = config.getMcpType();
        if (!StringUtils.hasText(type)) {
            throw new IllegalArgumentException("MCP 类型不能为空");
        }
        boolean requiresEndpoint = "http".equalsIgnoreCase(type) || "sse".equalsIgnoreCase(type);
        if (requiresEndpoint && !StringUtils.hasText(config.getEndpoint())) {
            throw new IllegalArgumentException("http/sse 类型必须填写端点地址");
        }
        if (config.getEnabled() == null) {
            config.setEnabled(true);
        }
    }
}
