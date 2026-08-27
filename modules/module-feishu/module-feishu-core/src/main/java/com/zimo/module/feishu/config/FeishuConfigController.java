package com.zimo.module.feishu.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zimo.framework.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/biz/feishu/config")
public class FeishuConfigController {
    private final FeishuConfigService configService;

    public FeishuConfigController(FeishuConfigService configService) {
        this.configService = configService;
    }

    /**
     * 通过 GET /api/biz/feishu/config/page 分页查询飞书配置。
     *
     * @param current 当前页，默认 1
     * @param size 每页条数，默认 10
     * @param configName 配置名称，允许为空
     * @param appId 飞书 App ID，允许为空
     * @param enabled 启用状态，允许为空
     * @param bound 绑定状态；true 查询已绑定，false 查询未绑定，null 不筛选
     * @return 脱敏后的飞书配置分页结果
     */
    @GetMapping("/page")
    public ApiResponse<Page<FeishuConfigResponse>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String configName,
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(required = false) Boolean bound) {
        return ApiResponse.ok(configService.page(current, size, configName, appId, enabled, bound));
    }

    @GetMapping("/{id}")
    public ApiResponse<FeishuConfigResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(configService.get(id));
    }

    @PostMapping
    public ApiResponse<FeishuConfigResponse> create(@RequestBody FeishuConfigRequest request) {
        return ApiResponse.ok(configService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<FeishuConfigResponse> update(@PathVariable Long id, @RequestBody FeishuConfigRequest request) {
        return ApiResponse.ok(configService.update(id, request));
    }

    /**
     * 通过 PUT /api/biz/feishu/config/{id}/agent-binding 更新指定配置的智能体绑定。
     *
     * @param id 飞书配置主键，不允许为空且必须存在
     * @param request 绑定请求；agentId 为空或空白时表示解除绑定
     * @return 包含最新 agentId 的脱敏飞书配置响应
     */
    @PutMapping("/{id}/agent-binding")
    public ApiResponse<FeishuConfigResponse> bindAgent(
            @PathVariable Long id,
            @RequestBody FeishuAgentBindingRequest request) {
        return ApiResponse.ok(configService.bindAgent(id, request.getAgentId()));
    }

    /**
     * 通过 PUT /api/biz/feishu/config/{id}/agent-unbinding 解除指定飞书配置的智能体绑定。
     *
     * @param id 飞书配置主键，不允许为空且必须存在
     * @return 包含空 agentId 的脱敏飞书配置响应；配置不存在时由服务层抛出业务异常
     */
    @PutMapping("/{id}/agent-unbinding")
    public ApiResponse<FeishuConfigResponse> unbindAgent(@PathVariable Long id) {
        return ApiResponse.ok(configService.bindAgent(id, null));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/enable")
    public ApiResponse<FeishuConfigResponse> enable(@PathVariable Long id) {
        return ApiResponse.ok(configService.enable(id));
    }
}
