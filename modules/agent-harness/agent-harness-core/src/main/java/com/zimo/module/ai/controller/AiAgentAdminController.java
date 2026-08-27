package com.zimo.module.ai.controller;

import com.zimo.framework.common.ApiResponse;
import com.zimo.framework.common.BizException;
import com.zimo.module.ai.management.AiAgentManagementService;
import com.zimo.module.ai.management.AiManagedAgent;
import com.zimo.module.ai.management.AiManagedAgentRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 智能体管理接口，统一前缀为 {@code /api/biz/ai/agents}。
 *
 * <p>接口面向后台管理页面并统一返回 {@link ApiResponse}。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@RestController
@RequestMapping("/api/biz/ai/agents")
public class AiAgentAdminController {

    private final AiAgentManagementService managementService;

    /**
     * 创建智能体管理控制器。
     *
     * @param managementService AI 管理服务，不允许为空
     */
    public AiAgentAdminController(AiAgentManagementService managementService) {
        this.managementService = Objects.requireNonNull(managementService, "managementService must not be null");
    }

    /** 查询全部未删除智能体，无数据时返回空列表。 */
    @GetMapping
    public ApiResponse<List<AiManagedAgent>> list() {
        return ApiResponse.ok(managementService.listAgents());
    }

    /** 创建智能体，请求中的名称和运行时配置必须合法。 */
    @PostMapping
    public ApiResponse<AiManagedAgent> create(@RequestBody AiManagedAgentRequest request) {
        return AiAdminControllerSupport.call(() -> managementService.create(request));
    }

    /** 更新指定智能体；目标不存在时抛出 404 业务异常。 */
    @PutMapping("/{id}")
    public ApiResponse<AiManagedAgent> update(
            @PathVariable String id,
            @RequestBody AiManagedAgentRequest request) {
        return AiAdminControllerSupport.call(() -> {
            AiManagedAgent agent = managementService.update(id, request);
            if (agent == null) {
                throw new BizException(404, "智能体不存在");
            }
            return agent;
        });
    }

    /** 逻辑删除指定智能体；目标不存在时抛出 404 业务异常。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        if (!managementService.delete(id)) {
            throw new BizException(404, "智能体不存在");
        }
        return ApiResponse.ok();
    }
}
